package com.example.peachbrowser.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchEngineUtilTest {

    @Test
    fun testDuckDuckGoUrlBuilding() {
        val engine = SearchEngine.DUCKDUCKGO
        val url = engine.buildSearchUrl("kotlin multiplatform")
        assertEquals("https://duckduckgo.com/?q=kotlin+multiplatform", url)
    }

    @Test
    fun testBraveSearchUrlBuilding() {
        val engine = SearchEngine.BRAVE
        val url = engine.buildSearchUrl("privacy browser")
        assertEquals("https://search.brave.com/search?q=privacy+browser", url)
    }

    @Test
    fun testGoogleSearchUrlBuilding() {
        val engine = SearchEngine.GOOGLE
        val url = engine.buildSearchUrl("android jetpack compose")
        assertEquals("https://www.google.com/search?q=android+jetpack+compose", url)
    }

    @Test
    fun testStartpageSearchUrlBuilding() {
        val engine = SearchEngine.STARTPAGE
        val url = engine.buildSearchUrl("zero tracking")
        assertEquals("https://www.startpage.com/sp/search?query=zero+tracking", url)
    }

    @Test
    fun testBingSearchUrlBuilding() {
        val engine = SearchEngine.BING
        val url = engine.buildSearchUrl("microsoft docs")
        assertEquals("https://www.bing.com/search?q=microsoft+docs", url)
    }

    @Test
    fun testRfc3986EncodingSpecialCharacters() {
        val engine = SearchEngine.DUCKDUCKGO
        // #, &, +, =, ?, /, % must be percent-encoded so search queries don't break URL query strings
        val query = "C# & C++ in 100% /?="
        val url = engine.buildSearchUrl(query)
        assertTrue(url.contains("C%23+%26+C%2B%2B+in+100%25+%2F%3F%3D"))
    }

    @Test
    fun testUnicodeAndEmojiEncoding() {
        val engine = SearchEngine.GOOGLE
        val url = engine.buildSearchUrl("Peach 🍑 Browser")
        assertTrue(url.startsWith("https://www.google.com/search?q=Peach+"))
        // Emoji 🍑 is 4 bytes in UTF-8: F0 9F 8D 91 -> %F0%9F%8D%91
        assertTrue(url.contains("%F0%9F%8D%91"))
    }

    @Test
    fun testTrimWhitespaceInQuery() {
        val engine = SearchEngine.BRAVE
        val url = engine.buildSearchUrl("   spaces around   ")
        assertEquals("https://search.brave.com/search?q=spaces+around", url)
    }

    @Test
    fun testFromNameResolution() {
        assertEquals(SearchEngine.BRAVE, SearchEngine.fromName("Brave"))
        assertEquals(SearchEngine.BRAVE, SearchEngine.fromName("brave search"))
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromName("Google"))
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromName("GOOGLE SEARCH"))
        assertEquals(SearchEngine.STARTPAGE, SearchEngine.fromName("Startpage"))
        assertEquals(SearchEngine.BING, SearchEngine.fromName("Bing"))
        // Fallback default
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.fromName("unknown_engine"))
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.fromName(""))
    }
}

