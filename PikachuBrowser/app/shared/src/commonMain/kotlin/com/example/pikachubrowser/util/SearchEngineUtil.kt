package com.example.pikachubrowser.util

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
    GOOGLE(
        id = "Google",
        displayName = "Google",
        searchUrlTemplate = "https://www.google.com/search?q=%s",
        iconEmoji = "🔍",
        description = "Comprehensive global web search"
    ),
    BRAVE(
        id = "Brave",
        displayName = "Brave Search",
        searchUrlTemplate = "https://search.brave.com/search?q=%s",
        iconEmoji = "🦁",
        description = "Independent search index with zero profiling"
    ),
    BING(
        id = "Bing",
        displayName = "Bing",
        searchUrlTemplate = "https://www.bing.com/search?q=%s",
        iconEmoji = "🔎",
        description = "Microsoft intelligent web search"
    );

    fun buildSearchUrl(query: String): String {
        val encoded = query.trim().replace(" ", "+")
        return searchUrlTemplate.replace("%s", encoded)
    }

    companion object {
        fun fromName(name: String): SearchEngine {
            val trimmed = name.trim().lowercase()
            return when {
                trimmed.contains("google") -> GOOGLE
                trimmed.contains("brave") -> BRAVE
                trimmed.contains("bing") -> BING
                else -> DUCKDUCKGO
            }
        }
    }
}
