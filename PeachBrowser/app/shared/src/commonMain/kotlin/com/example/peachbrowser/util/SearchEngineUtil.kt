package com.example.peachbrowser.util

enum class SearchEngine(
    val id: String,
    val displayName: String,
    val searchUrlTemplate: String,
    val iconEmoji: String,
    val description: String
) {
    DUCKDUCKGO(
        id = "DuckDuckGo",
        displayName = "DuckDuckGo",
        searchUrlTemplate = "https://duckduckgo.com/?q=%s",
        iconEmoji = "🦆",
        description = "Privacy-focused search without tracking"
    ),
    BRAVE(
        id = "Brave",
        displayName = "Brave Search",
        searchUrlTemplate = "https://search.brave.com/search?q=%s",
        iconEmoji = "🦁",
        description = "Independent privacy search engine"
    ),
    STARTPAGE(
        id = "Startpage",
        displayName = "Startpage",
        searchUrlTemplate = "https://www.startpage.com/sp/search?query=%s",
        iconEmoji = "🛡️",
        description = "Private search results with zero profiling"
    ),
    GOOGLE(
        id = "Google",
        displayName = "Google",
        searchUrlTemplate = "https://www.google.com/search?q=%s",
        iconEmoji = "🔍",
        description = "Comprehensive global web search"
    ),
    BING(
        id = "Bing",
        displayName = "Bing",
        searchUrlTemplate = "https://www.bing.com/search?q=%s",
        iconEmoji = "🔎",
        description = "Microsoft intelligent web search"
    );

    fun buildSearchUrl(query: String): String {
        val encoded = encodeQueryParam(query.trim())
        return searchUrlTemplate.replace("%s", encoded)
    }

    private fun encodeQueryParam(s: String): String {
        val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
        val bytes = s.encodeToByteArray()
        val sb = StringBuilder()
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (c in unreserved) {
                sb.append(c)
            } else if (c == ' ') {
                sb.append('+')
            } else {
                val hex = (b.toInt() and 0xFF).toString(16).uppercase()
                sb.append('%').append(if (hex.length == 1) "0$hex" else hex)
            }
        }
        return sb.toString()
    }

    companion object {
        fun fromName(name: String): SearchEngine {
            val trimmed = name.trim().lowercase()
            return when {
                trimmed.contains("brave") -> BRAVE
                trimmed.contains("google") -> GOOGLE
                trimmed.contains("startpage") -> STARTPAGE
                trimmed.contains("bing") -> BING
                else -> DUCKDUCKGO
            }
        }
    }
}
