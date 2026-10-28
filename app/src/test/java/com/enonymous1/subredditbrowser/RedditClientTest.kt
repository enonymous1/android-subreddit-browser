package com.enonymous1.subredditbrowser

import com.enonymous1.subredditbrowser.data.remote.FetchResult
import com.enonymous1.subredditbrowser.data.remote.RedditApiService
import com.enonymous1.subredditbrowser.data.remote.RedditAuthService
import com.enonymous1.subredditbrowser.data.remote.RedditChild
import com.enonymous1.subredditbrowser.data.remote.RedditClient
import com.enonymous1.subredditbrowser.data.remote.RedditListingData
import com.enonymous1.subredditbrowser.data.remote.RedditListingResponse
import com.enonymous1.subredditbrowser.data.remote.RedditPostDto
import com.enonymous1.subredditbrowser.data.remote.RedditTokenResponse
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun httpError(code: Int): HttpException =
    HttpException(Response.error<Any>(code, "".toResponseBody("text/plain".toMediaType())))

private class FakeAuthService(
    var token: String = "token-1",
    var expiresIn: Long = 3600,
) : RedditAuthService {
    var calls = 0
    var lastDeviceId: String? = null
    var failure: Throwable? = null

    override suspend fun accessToken(grantType: String, deviceId: String): RedditTokenResponse {
        calls++
        lastDeviceId = deviceId
        failure?.let { throw it }
        return RedditTokenResponse(accessToken = token, expiresIn = expiresIn)
    }
}

private class FakeApiService(
    var result: Result<RedditListingResponse> = Result.success(
        RedditListingResponse(
            RedditListingData(
                listOf(
                    RedditChild(
                        RedditPostDto(
                            id = "t3_one",
                            subreddit = "Android",
                            title = "First",
                            author = "alice",
                            ups = 10,
                            permalink = "/r/Android/comments/one/",
                        ),
                    ),
                    RedditChild(
                        RedditPostDto(
                            id = "t3_two",
                            subreddit = "Android",
                            title = "Second",
                            author = "bob",
                            ups = 5,
                            permalink = "/r/Android/comments/two/",
                        ),
                    ),
                ),
            ),
        ),
    ),
) : RedditApiService {
    var calls = 0
    val authorizations = mutableListOf<String>()
    var behavior: (call: Int) -> Result<RedditListingResponse> = { result }

    override suspend fun hot(subreddit: String, limit: Int, authorization: String): RedditListingResponse {
        calls++
        authorizations += authorization
        return behavior(calls).getOrThrow()
    }
}

class RedditClientTest {

    @Test
    fun fetchReturnsPostsInApiOrder() = runTest {
        val client = RedditClient("client-id", "device-1", FakeAuthService(), FakeApiService())
        val result = client.fetchHot("Android")
        assertTrue(result is FetchResult.Success)
        val posts = (result as FetchResult.Success).posts
        assertEquals(listOf("t3_one", "t3_two"), posts.map { it.id })
        assertEquals("First", posts[0].title)
    }

    @Test
    fun unconfiguredClientNeverCallsTheNetwork() = runTest {
        val auth = FakeAuthService()
        val api = FakeApiService()
        val client = RedditClient("", "device-1", auth, api)
        assertEquals(FetchResult.NotConfigured, client.fetchHot("Android"))
        assertEquals(0, auth.calls)
        assertEquals(0, api.calls)
    }

    @Test
    fun tokenIsCachedUntilShortlyBeforeExpiry() = runTest {
        var now = 0L
        val auth = FakeAuthService()
        val client = RedditClient("client-id", "device-1", auth, FakeApiService()) { now }
        client.fetchHot("Android")
        now = 3_500_000L
        client.fetchHot("Android")
        assertEquals(1, auth.calls)
        now = 3_600_000L
        client.fetchHot("Android")
        assertEquals(2, auth.calls)
    }

    @Test
    fun expiredTokenIsRefreshedAfterA401AndTheFetchRetriesOnce() = runTest {
        val auth = FakeAuthService(token = "token-2")
        val api = FakeApiService()
        api.behavior = { call ->
            if (call == 1) Result.failure(httpError(401)) else api.result
        }
        val client = RedditClient("client-id", "device-1", auth, api)
        val result = client.fetchHot("Android")
        assertTrue(result is FetchResult.Success)
        assertEquals(2, api.calls)
        assertEquals(2, auth.calls)
        assertEquals("Bearer token-2", api.authorizations.last())
    }

    @Test
    fun duplicatePostsAreDroppedKeepingApiOrder() = runTest {
        val api = FakeApiService(
            Result.success(
                RedditListingResponse(
                    RedditListingData(
                        listOf(
                            RedditChild(RedditPostDto(id = "t3_one", title = "First")),
                            RedditChild(RedditPostDto(id = "t3_one", title = "First again")),
                            RedditChild(RedditPostDto(id = "t3_two", title = "Second")),
                        ),
                    ),
                ),
            ),
        )
        val client = RedditClient("client-id", "device-1", FakeAuthService(), api)
        val posts = (client.fetchHot("Android") as FetchResult.Success).posts
        assertEquals(listOf("t3_one", "t3_two"), posts.map { it.id })
        assertEquals("First", posts[0].title)
    }

    @Test
    fun unknownSubredditIsAReadable404Message() = runTest {
        val api = FakeApiService(Result.failure(httpError(404)))
        val client = RedditClient("client-id", "device-1", FakeAuthService(), api)
        val result = client.fetchHot("NoSuchSubredditXYZ")
        assertTrue(result is FetchResult.Failure)
        assertTrue((result as FetchResult.Failure).message.contains("wasn't found"))
    }

    @Test
    fun networkFailureIsAReadableMessage() = runTest {
        val api = FakeApiService(Result.failure(IOException("down")))
        val client = RedditClient("client-id", "device-1", FakeAuthService(), api)
        val result = client.fetchHot("Android")
        assertTrue(result is FetchResult.Failure)
        assertTrue((result as FetchResult.Failure).message.contains("Couldn't reach Reddit"))
    }
}
