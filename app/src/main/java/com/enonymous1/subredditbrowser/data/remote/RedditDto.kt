package com.enonymous1.subredditbrowser.data.remote

import com.enonymous1.subredditbrowser.model.Post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire shape of Reddit's `/hot` listing. Every field has a default:
 * Reddit omits or nulls fields for deleted/removed posts, and the
 * original parser called `getString` on every field, crashing the
 * background task on the first such post. Unknown fields are ignored by
 * the lenient `Json` instance in [RedditClientFactory].
 */
@Serializable
data class RedditListingResponse(
    val data: RedditListingData = RedditListingData(),
)

@Serializable
data class RedditListingData(
    val children: List<RedditChild> = emptyList(),
    val after: String? = null,
)

@Serializable
data class RedditChild(
    val data: RedditPostDto = RedditPostDto(),
)

@Serializable
data class RedditPostDto(
    val id: String = "",
    val subreddit: String = "",
    val title: String = "",
    val author: String = "",
    val ups: Int = 0,
    val permalink: String = "",
    @SerialName("created_utc")
    val createdUtc: Double = 0.0,
    val url: String = "",
) {
    /** Drops listing entries with no id or title instead of crashing. */
    fun toPost(subredditFallback: String, fetchedAtMillis: Long): Post? {
        if (id.isBlank() || title.isBlank()) return null
        return Post(
            id = id,
            subreddit = subreddit.ifBlank { subredditFallback },
            title = title,
            author = author,
            ups = ups,
            permalink = permalink,
            createdUtcSeconds = createdUtc.toLong(),
            fetchedAtMillis = fetchedAtMillis,
        )
    }
}

/** The installed-app token response from `POST /api/v1/access_token`. */
@Serializable
data class RedditTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String = "bearer",
    @SerialName("expires_in")
    val expiresIn: Long = 3600,
    val scope: String = "",
)
