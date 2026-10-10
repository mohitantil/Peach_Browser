package com.example.pikachubrowser.domain.usecase

import com.example.pikachubrowser.domain.model.LlmMessage
import com.example.pikachubrowser.domain.repository.LlmRepository

class ChatWithLlmUseCase(
    private val llmRepository: LlmRepository
) {
    suspend operator fun invoke(chatHistory: List<LlmMessage>): Result<String> {
        if (chatHistory.isEmpty()) return Result.success("")
        return llmRepository.getChatCompletion(chatHistory)
    }
}
