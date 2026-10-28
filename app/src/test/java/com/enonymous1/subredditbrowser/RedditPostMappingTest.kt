package com.enonymous1.subredditbrowser

import com.enonymous1.subredditbrowser.data.remote.RedditPostDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RedditPostMappingTest {

    @Test
    fun dtoMapsToPost() {
        val post = RedditPostDto(
            id = "t3_abc",
            subreddit = "Android",
            title = "Hello",
            author = "someone",
            ups = 42,
            permalink = "/r/Android/comments/abc/hello/",
            createdUtc = 1_700_000_000.0,
        ).toPost("Android", fetchedAtMillis = 123L)
        assertEquals("t3_abc", post?.id)
        assertEquals("Android", post?.subreddit)
        assertEquals("Hello", post?.title)
        assertEquals("someone", post?.author)
        assertEquals(42, post?.ups)
        assertEquals(1_700_000_000L, post?.createdUtcSeconds)
        assertEquals("https://www.reddit.com/r/Android/comments/abc/hello/", post?.redditUrl)
        assertEquals("u/someone", post?.authorDisplay)
    }

    @Test
    fun blankIdIsDropped() {
        val post = RedditPostDto(id = "", title = "Hello").toPost("Android", 0L)
        assertNull(post)
    }

    @Test
    fun blankTitleIsDropped() {
        val post = RedditPostDto(id = "t3_abc", title = "  ").toPost("Android", 0L)
        assertNull(post)
    }

    @Test
    fun blankSubredditFallsBackToRequestedSubreddit() {
        val post = RedditPostDto(id = "t3_abc", subreddit = "", title = "Hello")
            .toPost("Coding", 0L)
        assertEquals("Coding", post?.subreddit)
    }

    @Test
    fun deletedAuthorKeepsItsLabel() {
        val post = RedditPostDto(id = "t3_abc", title = "Hello", author = "[deleted]")
            .toPost("Android", 0L)
        assertEquals("[deleted]", post?.authorDisplay)
    }
}
