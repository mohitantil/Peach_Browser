package com.example.peachbrowser.domain.repository

import com.example.peachbrowser.domain.model.LlmMessage

interface LlmRepository {
    suspend fun getChatCompletion(messages: List<LlmMessage>): Result<String>
}
