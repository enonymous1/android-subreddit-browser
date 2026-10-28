package com.enonymous1.subredditbrowser

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enonymous1.subredditbrowser.data.DataStoreSettingsRepository
import com.enonymous1.subredditbrowser.data.PostsDatabase
import com.enonymous1.subredditbrowser.data.RoomPostsRepository
import com.enonymous1.subredditbrowser.data.remote.FetchResult
import com.enonymous1.subredditbrowser.data.remote.PostsRemoteSource
import com.enonymous1.subredditbrowser.model.Post
import com.enonymous1.subredditbrowser.ui.SubredditBrowserApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device or emulator only; the GitHub Actions workflow runs the
 * JVM unit tests and a debug build, not these tests.
 */
@RunWith(AndroidJUnit4::class)
class SubredditBrowserAppTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val online = MutableStateFlow(true)

    private fun launch(remote: PostsRemoteSource = cannedRemote()) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, PostsDatabase::class.java).build()
        // A fresh settings file per launch, so a selection changed in one
        // test cannot leak into the next test's default.
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) { context.cacheDir.resolve("settings-${System.nanoTime()}.preferences_pb") }
        val viewModel = PostsViewModel(
            RoomPostsRepository(database.postsDao()),
            DataStoreSettingsRepository(dataStore),
            remote,
            online,
        )
        composeRule.setContent {
            MaterialTheme { SubredditBrowserApp(viewModel) }
        }
        composeRule.waitForIdle()
    }

    private fun cannedRemote(): PostsRemoteSource = object : PostsRemoteSource {
        override val isConfigured: Boolean = true

        override suspend fun fetchHot(subreddit: String): FetchResult {
            val posts = listOf(
                Post(
                    id = "t3_one",
                    subreddit = subreddit,
                    title = "Hot post in $subreddit",
                    author = "alice",
                    ups = 42,
                    permalink = "/r/$subreddit/comments/one/",
                    createdUtcSeconds = 1_700_000_000L,
                ),
                Post(
                    id = "t3_two",
                    subreddit = subreddit,
                    title = "Second post in $subreddit",
                    author = "bob",
                    ups = 7,
                    permalink = "/r/$subreddit/comments/two/",
                    createdUtcSeconds = 1_700_000_001L,
                ),
            )
            return FetchResult.Success(posts)
        }
    }

    @Test
    fun listShowsFetchedPosts() {
        launch()
        composeRule.onNodeWithText("Hot post in gameDevClassifieds").assertIsDisplayed()
        composeRule.onNodeWithText("u/alice · r/gameDevClassifieds").assertIsDisplayed()
        composeRule.onNodeWithText("42").assertIsDisplayed()
    }

    @Test
    fun tappingAPostOpensDetailsWithAnOpenButton() {
        launch()
        composeRule.onNodeWithText("Hot post in gameDevClassifieds").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("42 upvotes").assertIsDisplayed()
        composeRule.onNodeWithText("Posted by u/alice in r/gameDevClassifieds").assertIsDisplayed()
        composeRule.onNodeWithText("Open on Reddit").assertIsDisplayed()
    }

    @Test
    fun invalidSubredditNameShowsAnError() {
        launch()
        composeRule.onNodeWithTag("addSubredditField").performTextInput("a")
        composeRule.onNodeWithTag("addSubredditButton").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            "Enter a valid subreddit name (3–21 letters, digits, or underscores).",
        ).assertIsDisplayed()
    }

    @Test
    fun addingASubredditSelectsItAndShowsItsPosts() {
        launch()
        composeRule.onNodeWithTag("addSubredditField").performTextInput("Kotlin")
        composeRule.onNodeWithTag("addSubredditButton").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hot post in Kotlin").assertIsDisplayed()
    }

    @Test
    fun offlineWithNoCacheShowsTheEmptyState() {
        online.value = false
        launch(
            object : PostsRemoteSource {
                override val isConfigured: Boolean = true
                override suspend fun fetchHot(subreddit: String): FetchResult =
                    FetchResult.Failure("Couldn't reach Reddit.")
            },
        )
        composeRule.onNodeWithText(
            "No saved posts for r/gameDevClassifieds yet. " +
                "Go online and tap Refresh, and posts will be saved for offline use.",
        ).assertIsDisplayed()
    }
}
