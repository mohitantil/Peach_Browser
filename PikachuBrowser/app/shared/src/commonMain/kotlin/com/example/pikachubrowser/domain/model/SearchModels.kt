package com.example.pikachubrowser.domain.model

data class SearchResult(
    val title: String,
    val url: String,
    val content: String,
    val engine: String
)

data class SearchQuery(
    val query: String
)
