package com.example.pikachubrowser.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pikachubrowser.domain.model.LlmMessage
import com.example.pikachubrowser.domain.usecase.ChatWithLlmUseCase
import com.example.pikachubrowser.presentation.mvi.ChatIntent
import com.example.pikachubrowser.presentation.mvi.ChatState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatWithLlmUseCase: ChatWithLlmUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    fun processIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.SendMessage -> handleSendMessage(
                content = intent.message,
                pageUrl = intent.pageUrl,
                pageTitle = intent.pageTitle,
                pageContent = intent.pageContent
            )
            is ChatIntent.ClearChat -> handleClearChat()
        }
    }

    private fun handleSendMessage(
        content: String,
        pageUrl: String,
        pageTitle: String,
        pageContent: String
    ) {
        val userDisplayMsg = LlmMessage(LlmMessage.Role.USER, content)
        val updatedHistory = _state.value.messages + userDisplayMsg

        _state.value = _state.value.copy(
            messages = updatedHistory,
            isTyping = true,
            error = null
        )

        viewModelScope.launch {
            // Build messages sent to the LLM backend
            val messagesForLlm = if (pageContent.isNotBlank()) {
                val contextSystemMsg = LlmMessage(
                    role = LlmMessage.Role.SYSTEM,
                    content = "You are Pikachu AI, an intelligent agentic browser companion. " +
                            "The user is viewing '${pageTitle.ifBlank { pageUrl }}' ($pageUrl). " +
                            "Here is extracted context from the webpage:\n\n$pageContent\n\n" +
                            "Help the user analyze, summarize, or answer questions regarding this page accurately."
                )
                listOf(contextSystemMsg) + updatedHistory
            } else {
                updatedHistory
            }

            chatWithLlmUseCase(messagesForLlm).fold(
                onSuccess = { reply ->
                    val assistantMsg = LlmMessage(LlmMessage.Role.ASSISTANT, reply)
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + assistantMsg,
                        isTyping = false
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isTyping = false,
                        error = e.message ?: "Failed to connect to AI engine"
                    )
                }
            )
        }
    }

    private fun handleClearChat() {
        _state.value = ChatState()
    }
}
