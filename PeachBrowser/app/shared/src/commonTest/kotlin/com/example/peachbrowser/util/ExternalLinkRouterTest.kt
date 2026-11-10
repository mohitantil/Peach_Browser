package com.example.peachbrowser.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExternalLinkRouterTest {

    @Test
    fun testExtractUrlFromCleanLinks() {
        assertEquals(
            "https://example.com/article",
            ExternalLinkRouter.extractUrl("https://example.com/article")
        )
        assertEquals(
            "http://news.ycombinator.com",
            ExternalLinkRouter.extractUrl("http://news.ycombinator.com")
        )
    }

    @Test
    fun testExtractUrlFromSurroundingSharedText() {
        val sharedText = "Hey, check out this video: https://youtu.be/dQw4w9WgXcQ it is incredible!"
        assertEquals("https://youtu.be/dQw4w9WgXcQ", ExternalLinkRouter.extractUrl(sharedText))

        val parenthesized = "Read this (https://en.wikipedia.org/wiki/Kotlin_(programming_language)) for details."
        assertEquals("https://en.wikipedia.org/wiki/Kotlin_(programming_language)", ExternalLinkRouter.extractUrl(parenthesized))

        val withPeriod = "Have you seen this website https://example.com/home.html? It looks great."
        assertEquals("https://example.com/home.html", ExternalLinkRouter.extractUrl(withPeriod))

        val bracketed = "Check out this documentation: [https://kotlinlang.org/docs/home.html]."
        assertEquals("https://kotlinlang.org/docs/home.html", ExternalLinkRouter.extractUrl(bracketed))
    }

    @Test
    fun testNonUrlTextReturnsNull() {
        assertNull(ExternalLinkRouter.extractUrl("just a plain search query"))
        assertNull(ExternalLinkRouter.extractUrl("hello world"))
        assertNull(ExternalLinkRouter.extractUrl(""))
        assertNull(ExternalLinkRouter.extractUrl("   "))
    }
}

