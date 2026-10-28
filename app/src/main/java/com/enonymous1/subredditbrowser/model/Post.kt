package com.enonymous1.subredditbrowser.model

/**
 * One hot post from a subreddit. Identity is the Reddit post id (for
 * example `t3_abc123`): two posts with the same title are still distinct,
 * and identity never depends on a title or author that Reddit can change.
 * The original Java model was a mutable bag of four public `String`
 * fields with no id at all, so a list could not tell two posts apart.
 */
data class Post(
    val id: String,
    val subreddit: String,
    val title: String,
    val author: String,
    val ups: Int,
    val permalink: String,
    val createdUtcSeconds: Long,
    val fetchedAtMillis: Long = 0L,
) {
    /** The full Reddit URL for this post, for the details screen. */
    val redditUrl: String
        get() = if (permalink.startsWith("/")) "https://www.reddit.com$permalink" else permalink

    val authorDisplay: String
        get() = if (author == "[deleted]") author else "u/$author"

    val subredditDisplay: String
        get() = "r/$subreddit"
}

/** The five subreddits the original app shipped with, in its spinner order. */
val DEFAULT_SUBREDDITS: List<String> = listOf(
    "gameDevClassifieds",
    "Coding",
    "Android",
    "Programming",
    "androiddev",
)

/**
 * Subreddit-name rules, kept pure so they can be unit-tested without
 * Android. Reddit names are 3–21 characters, letters, digits, and
 * underscores only. Input is trimmed and a leading `r/` is stripped, so
 * `r/Android`, `/r/Android/`, and `  Android  ` all mean `Android`.
 */
object SubredditName {

    private val VALID = Regex("^[A-Za-z0-9_]{3,21}$")

    fun isValid(name: String): Boolean = VALID.matches(name)

    /**
     * Returns the canonical name, or null when [raw] is blank or does not
     * satisfy the name rules. Never throws on user input — the original
     * passed the spinner text straight into a URL unchecked.
     */
    fun normalize(raw: String): String? {
        var name = raw.trim()
        if (name.startsWith("/")) name = name.drop(1)
        if (name.startsWith("r/", ignoreCase = true)) name = name.drop(2)
        if (name.endsWith("/")) name = name.dropLast(1)
        name = name.trim()
        return if (isValid(name)) name else null
    }
}
