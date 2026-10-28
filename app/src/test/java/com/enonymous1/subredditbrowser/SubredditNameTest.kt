package com.enonymous1.subredditbrowser

import com.enonymous1.subredditbrowser.model.SubredditName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubredditNameTest {

    @Test
    fun plainNameIsValid() {
        assertTrue(SubredditName.isValid("Android"))
    }

    @Test
    fun nameWithUnderscoreAndDigitsIsValid() {
        assertTrue(SubredditName.isValid("android_dev1"))
    }

    @Test
    fun twoCharacterNameIsTooShort() {
        assertFalse(SubredditName.isValid("ab"))
    }

    @Test
    fun twentyTwoCharacterNameIsTooLong() {
        assertFalse(SubredditName.isValid("abcdefghijklmnopqrstuv"))
    }

    @Test
    fun nameWithSpaceIsInvalid() {
        assertFalse(SubredditName.isValid("bad name"))
    }

    @Test
    fun nameWithHyphenIsInvalid() {
        assertFalse(SubredditName.isValid("bad-name"))
    }

    @Test
    fun normalizeTrimsWhitespace() {
        assertEquals("Android", SubredditName.normalize("  Android  "))
    }

    @Test
    fun normalizeStripsLeadingRSlash() {
        assertEquals("Android", SubredditName.normalize("r/Android"))
    }

    @Test
    fun normalizeStripsLeadingSlashRSlashAndTrailingSlash() {
        assertEquals("Android", SubredditName.normalize("/r/Android/"))
    }

    @Test
    fun normalizeRejectsBlankInput() {
        assertNull(SubredditName.normalize("   "))
    }

    @Test
    fun normalizeRejectsInvalidName() {
        assertNull(SubredditName.normalize("bad name!"))
    }

    @Test
    fun normalizeRejectsTooShortName() {
        assertNull(SubredditName.normalize("ab"))
    }
}
