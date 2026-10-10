package com.example.pikachubrowser.domain.model

data class Tab(
    val id: String,
    val url: String = "",
    val history: List<String> = listOf(""),
    val historyIndex: Int = 0
)
