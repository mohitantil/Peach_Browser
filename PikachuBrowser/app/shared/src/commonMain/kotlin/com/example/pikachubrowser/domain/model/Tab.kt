package com.example.pikachubrowser.domain.model

data class Tab(
    val id: String,
    val title: String = "New Tab",
    val url: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val initialUrl: String = "",
    val createdAt: Long = 0L,
    val lastAccessedAt: Long = 0L
)
