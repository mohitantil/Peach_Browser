package com.example.pikachubrowser.domain.model

data class Tab(
    val id: String,
    val title: String = "New Tab",
    val url: String = "",
    val history: List<String> = if (url.isNotBlank()) listOf(url) else listOf(""),
    val historyIndex: Int = 0
)
