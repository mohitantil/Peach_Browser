package com.example.pikachubrowser.presentation.mvi

import com.example.pikachubrowser.domain.model.LlmMessage

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
