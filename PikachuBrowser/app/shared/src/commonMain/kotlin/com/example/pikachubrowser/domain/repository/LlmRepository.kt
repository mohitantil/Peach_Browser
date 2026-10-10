package com.example.pikachubrowser.domain.repository

import com.example.pikachubrowser.domain.model.LlmMessage

interface LlmRepository {
    suspend fun getChatCompletion(messages: List<LlmMessage>): Result<String>
}
