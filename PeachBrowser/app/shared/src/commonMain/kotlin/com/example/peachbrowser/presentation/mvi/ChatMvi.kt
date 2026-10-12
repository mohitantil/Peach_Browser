package com.example.peachbrowser.presentation.mvi

import com.example.peachbrowser.domain.model.LlmMessage

sealed interface ChatIntent {
    data class SendMessage(
        val message: String,
        val pageUrl: String = "",
        val pageTitle: String = "",
        val pageContent: String = ""
    ) : ChatIntent
    object ClearChat : ChatIntent
}

data class ChatState(
    val messages: List<LlmMessage> = emptyList(),
    val isTyping: Boolean = false,
    val error: String? = null
)
