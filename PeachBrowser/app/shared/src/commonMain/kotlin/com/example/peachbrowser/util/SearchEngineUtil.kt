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
    GOOGLE(
        id = "Google",
        displayName = "Google",
        searchUrlTemplate = "https://www.google.com/search?q=%s",
        iconEmoji = "🔍",
        description = "Comprehensive global web search"
    ),
    STARTPAGE(
        id = "Startpage",
        displayName = "Startpage",
        searchUrlTemplate = "https://www.startpage.com/sp/search?query=%s",
        iconEmoji = "🛡️",
        description = "Private search results with zero profiling"
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
                trimmed.contains("startpage") -> STARTPAGE
                trimmed.contains("bing") -> BING
                else -> DUCKDUCKGO
            }
        }
    }
}
