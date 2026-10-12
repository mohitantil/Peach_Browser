package com.example.peachbrowser.domain.usecase

import com.example.peachbrowser.domain.model.LlmMessage
import com.example.peachbrowser.domain.repository.LlmRepository

class ChatWithLlmUseCase(
    private val llmRepository: LlmRepository
) {
    suspend operator fun invoke(chatHistory: List<LlmMessage>): Result<String> {
        if (chatHistory.isEmpty()) return Result.success("")
        return llmRepository.getChatCompletion(chatHistory)
    }
}
