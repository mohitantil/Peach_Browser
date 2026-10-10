package com.example.pikachubrowser.domain.model

data class Tab(
    val id: String,
    val title: String = "New Tab",
    val url: String = ""
)
