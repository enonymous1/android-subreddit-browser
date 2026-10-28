package com.enonymous1.subredditbrowser

import com.enonymous1.subredditbrowser.data.PostsRepository
import com.enonymous1.subredditbrowser.data.SettingsRepository
import com.enonymous1.subredditbrowser.data.remote.FetchResult
import com.enonymous1.subredditbrowser.data.remote.PostsRemoteSource
import com.enonymous1.subredditbrowser.model.DEFAULT_SUBREDDITS
import com.enonymous1.subredditbrowser.model.Post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakePostsRepository : PostsRepository {
    private val state = MutableStateFlow<Map<String, List<Post>>>(emptyMap())

    override fun observePosts(subreddit: String): Flow<List<Post>> =
        state.map { it[subreddit] ?: emptyList() }

    override fun observePost(id: String): Flow<Post?> =
        state.map { map -> map.values.flatten().find { it.id == id } }

    override suspend fun replacePosts(subreddit: String, posts: List<Post>) {
        state.value = state.value + (subreddit to posts)
    }
}

private class FakeSettingsRepository : SettingsRepository {
    private val selected = MutableStateFlow(DEFAULT_SUBREDDITS.first())
    private val customs = MutableStateFlow<List<String>>(emptyList())

    override val selectedSubreddit: Flow<String> = selected
    override val customSubreddits: Flow<List<String>> = customs

    override suspend fun setSelectedSubreddit(subreddit: String) {
        selected.value = subreddit
    }

    override suspend fun addCustomSubreddit(subreddit: String) {
        if (subreddit !in customs.value) customs.value = customs.value + subreddit
    }

    override suspend fun removeCustomSubreddit(subreddit: String) {
        customs.value = customs.value - subreddit
    }
}

private class FakeRemoteSource(
    override var isConfigured: Boolean = true,
    var result: FetchResult = FetchResult.Success(emptyList()),
) : PostsRemoteSource {
    var calls = 0
    var lastSubreddit: String? = null

    override suspend fun fetchHot(subreddit: String): FetchResult {
        calls++
        lastSubreddit = subreddit
        return result
    }
}

private fun post(id: String, subreddit: String, title: String) = Post(
    id = id,
    subreddit = subreddit,
    title = title,
    author = "alice",
    ups = 3,
    permalink = "/r/$subreddit/comments/$id/",
    createdUtcSeconds = 1_700_000_000L,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PostsViewModelTest {

    private val online = MutableStateFlow(true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: FakePostsRepository = FakePostsRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        remote: FakeRemoteSource = FakeRemoteSource(),
    ) = PostsViewModel(repository, settings, remote, online)

    @Test
    fun selectingASubredditFetchesAndCachesItsPostsInApiOrder() = runTest {
        val remote = FakeRemoteSource(
            result = FetchResult.Success(
                listOf(
                    post("t3_one", "Coding", "First"),
                    post("t3_two", "Coding", "Second"),
                ),
            ),
        )
        val vm = viewModel(remote = remote)
        advanceUntilIdle()
        vm.selectSubreddit("Coding")
        advanceUntilIdle()
        assertEquals("Coding", remote.lastSubreddit)
        assertEquals(listOf("First", "Second"), vm.posts.value.map { it.title })
    }

    @Test
    fun offlineNeverCallsTheRemoteAndShowsSavedPosts() = runTest {
        online.value = false
        val repository = FakePostsRepository()
        repository.replacePosts("Android", listOf(post("t3_old", "Android", "Saved")))
        val remote = FakeRemoteSource()
        val vm = viewModel(repository = repository, remote = remote)
        advanceUntilIdle()
        vm.selectSubreddit("Android")
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(0, remote.calls)
        assertEquals(listOf("Saved"), vm.posts.value.map { it.title })
        assertTrue(vm.statusMessage.value?.contains("offline") == true)
    }

    @Test
    fun unconfiguredRemoteNeverCallsTheNetwork() = runTest {
        val remote = FakeRemoteSource(isConfigured = false)
        val vm = viewModel(remote = remote)
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(0, remote.calls)
        assertTrue(vm.statusMessage.value?.contains("isn't configured") == true)
    }

    @Test
    fun failedFetchKeepsCachedPostsAndReportsTheError() = runTest {
        val repository = FakePostsRepository()
        repository.replacePosts("Android", listOf(post("t3_old", "Android", "Saved")))
        val remote = FakeRemoteSource(result = FetchResult.Failure("Reddit had a server error."))
        val vm = viewModel(repository = repository, remote = remote)
        advanceUntilIdle()
        vm.selectSubreddit("Android")
        advanceUntilIdle()
        assertEquals(listOf("Saved"), vm.posts.value.map { it.title })
        assertEquals("Reddit had a server error.", vm.statusMessage.value)
    }

    @Test
    fun successfulFetchClearsTheStatusMessage() = runTest {
        val remote = FakeRemoteSource(
            result = FetchResult.Success(listOf(post("t3_one", "Android", "Fresh"))),
        )
        val vm = viewModel(remote = remote)
        advanceUntilIdle()
        assertNull(vm.statusMessage.value)
        assertEquals(listOf("Fresh"), vm.posts.value.map { it.title })
    }

    @Test
    fun invalidSubredditNameIsRejectedWithAnError() = runTest {
        val vm = viewModel()
        assertEquals(
            "Enter a valid subreddit name (3–21 letters, digits, or underscores).",
            vm.addSubreddit("bad name!"),
        )
        advanceUntilIdle()
        assertEquals(DEFAULT_SUBREDDITS.first(), vm.selectedSubreddit.value)
    }

    @Test
    fun addingAValidSubredditSelectsItAndListsItAsCustom() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)
        assertNull(vm.addSubreddit("r/Kotlin"))
        advanceUntilIdle()
        assertEquals("Kotlin", vm.selectedSubreddit.value)
        assertEquals(listOf("Kotlin"), vm.customSubreddits.value)
        assertTrue(vm.subreddits.value.contains("Kotlin"))
    }

    @Test
    fun addingABuiltInSubredditSelectsItWithoutDuplicatingIt() = runTest {
        val vm = viewModel()
        assertNull(vm.addSubreddit("Android"))
        advanceUntilIdle()
        assertEquals("Android", vm.selectedSubreddit.value)
        assertTrue(vm.customSubreddits.value.isEmpty())
    }

    @Test
    fun removingACustomSubredditReturnsToTheFirstDefault() = runTest {
        val vm = viewModel()
        vm.addSubreddit("Kotlin")
        advanceUntilIdle()
        vm.removeSubreddit("Kotlin")
        advanceUntilIdle()
        assertEquals(DEFAULT_SUBREDDITS.first(), vm.selectedSubreddit.value)
        assertTrue(vm.customSubreddits.value.isEmpty())
    }

    @Test
    fun removingABuiltInSubredditDoesNothing() = runTest {
        val vm = viewModel()
        vm.removeSubreddit("Android")
        advanceUntilIdle()
        assertEquals(DEFAULT_SUBREDDITS.first(), vm.selectedSubreddit.value)
    }
}
