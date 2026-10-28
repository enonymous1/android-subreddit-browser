package com.enonymous1.subredditbrowser.data.remote

import com.enonymous1.subredditbrowser.model.Post
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/** The User-Agent Reddit requires: a unique, descriptive identifier. */
const val REDDIT_USER_AGENT = "android:com.enonymous1.subredditbrowser:2.0.0 (by /u/enonymous1)"

/** What a hot-posts fetch can return, without throwing on HTTP failures. */
sealed interface FetchResult {
    data class Success(val posts: List<Post>) : FetchResult
    data object NotConfigured : FetchResult
    data class Failure(val message: String) : FetchResult
}

/** The source of hot posts. Kept as an interface so tests can fake it. */
interface PostsRemoteSource {
    val isConfigured: Boolean
    suspend fun fetchHot(subreddit: String): FetchResult
}

/**
 * Token endpoint on www.reddit.com. Installed apps authenticate with
 * HTTP Basic using only the client id (`clientId:` — no secret), sent by
 * the OkHttp interceptor in [createRedditClient], never in code.
 */
interface RedditAuthService {
    @FormUrlEncoded
    @POST("api/v1/access_token")
    suspend fun accessToken(
        @Field("grant_type") grantType: String,
        @Field("device_id") deviceId: String,
    ): RedditTokenResponse
}

/** Authenticated listing endpoint on oauth.reddit.com. */
interface RedditApiService {
    @GET("r/{subreddit}/hot")
    suspend fun hot(
        @Path("subreddit") subreddit: String,
        @Query("limit") limit: Int = 25,
        @Header("Authorization") authorization: String,
    ): RedditListingResponse
}

/**
 * Fetches hot posts through Reddit's OAuth API.
 *
 * The original app called the unauthenticated `www.reddit.com/r/<sub>/hot.json`
 * with raw `HttpURLConnection`, no User-Agent, no timeout, and no HTTP-status
 * check. That endpoint now returns 403. This client uses the installed-app
 * grant: a bearer token fetched with the client id, cached until shortly
 * before expiry, refreshed once on a 401, and never logged.
 *
 * [clockMillis] is injectable so token-expiry behavior can be unit-tested.
 */
class RedditClient(
    private val clientId: String,
    private val deviceId: String,
    private val authService: RedditAuthService,
    private val apiService: RedditApiService,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : PostsRemoteSource {

    override val isConfigured: Boolean
        get() = clientId.isNotBlank()

    private val tokenMutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiresAtMillis: Long = 0L

    override suspend fun fetchHot(subreddit: String): FetchResult {
        if (!isConfigured) return FetchResult.NotConfigured
        return try {
            val token = accessToken()
            val response = apiService.hot(subreddit, authorization = "Bearer $token")
            FetchResult.Success(mapPosts(response, subreddit))
        } catch (e: HttpException) {
            if (e.code() == 401) {
                // Token expired or was revoked: drop it and try once more.
                clearToken()
                try {
                    val token = accessToken()
                    val response = apiService.hot(subreddit, authorization = "Bearer $token")
                    FetchResult.Success(mapPosts(response, subreddit))
                } catch (retry: HttpException) {
                    FetchResult.Failure(messageFor(retry))
                } catch (retry: IOException) {
                    FetchResult.Failure("Couldn't reach Reddit. Check your connection and try again.")
                } catch (retry: SerializationException) {
                    FetchResult.Failure("Reddit returned a response this version couldn't read.")
                }
            } else {
                FetchResult.Failure(messageFor(e))
            }
        } catch (e: IOException) {
            FetchResult.Failure("Couldn't reach Reddit. Check your connection and try again.")
        } catch (e: SerializationException) {
            FetchResult.Failure("Reddit returned a response this version couldn't read.")
        }
    }

    private fun mapPosts(response: RedditListingResponse, subreddit: String): List<Post> {
        val fetchedAt = clockMillis()
        val seen = LinkedHashSet<String>()
        return response.data.children.mapNotNull { child ->
            child.data.toPost(subreddit, fetchedAt)
        }.filter { post -> seen.add(post.id) }
    }

    private fun messageFor(e: HttpException): String = when (e.code()) {
        404 -> "That subreddit wasn't found. Check the name and try again."
        403 -> "Reddit refused the request. The app may need to be re-registered (see README)."
        429 -> "Reddit is rate-limiting requests. Wait a minute and try again."
        in 500..599 -> "Reddit had a server error. Try again in a moment."
        else -> "Reddit returned an error (${e.code()}). Try again."
    }

    private suspend fun accessToken(): String = tokenMutex.withLock {
        val now = clockMillis()
        val cached = cachedToken
        if (cached != null && now < tokenExpiresAtMillis - 60_000L) return cached
        val response = authService.accessToken(
            grantType = "https://oauth.reddit.com/grants/installed_client",
            deviceId = deviceId,
        )
        cachedToken = response.accessToken
        tokenExpiresAtMillis = now + response.expiresIn * 1_000L
        response.accessToken
    }

    private suspend fun clearToken() {
        tokenMutex.withLock {
            cachedToken = null
            tokenExpiresAtMillis = 0L
        }
    }
}

/** Builds the two Retrofit services with timeouts and the Reddit User-Agent. */
fun createRedditClient(clientId: String, deviceId: String): RedditClient {
    val json = Json { ignoreUnknownKeys = true }
    val converter = json.asConverterFactory("application/json".toMediaType())

    val authHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", REDDIT_USER_AGENT)
                .header("Authorization", Credentials.basic(clientId, ""))
                .build()
            chain.proceed(request)
        }
        .build()
    val authService = Retrofit.Builder()
        .baseUrl("https://www.reddit.com/")
        .client(authHttpClient)
        .addConverterFactory(converter)
        .build()
        .create(RedditAuthService::class.java)

    val apiHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", REDDIT_USER_AGENT)
                .build()
            chain.proceed(request)
        }
        .build()
    val apiService = Retrofit.Builder()
        .baseUrl("https://oauth.reddit.com/")
        .client(apiHttpClient)
        .addConverterFactory(converter)
        .build()
        .create(RedditApiService::class.java)

    return RedditClient(clientId, deviceId, authService, apiService)
}
