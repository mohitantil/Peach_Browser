package com.example.pikachubrowser.data.repository

import com.example.pikachubrowser.data.network.GroqMessage
import com.example.pikachubrowser.data.network.GroqRequest
import com.example.pikachubrowser.data.network.LlmApiService
import com.example.pikachubrowser.domain.model.LlmMessage
import com.example.pikachubrowser.domain.repository.LlmRepository

class LlmRepositoryImpl(
    private val apiService: LlmApiService
) : LlmRepository {
    override suspend fun getChatCompletion(messages: List<LlmMessage>): Result<String> {
        return try {
            val request = GroqRequest(
                model = "llama3-8b-8192", // Default Groq model
                messages = messages.map {
                    GroqMessage(
                        role = it.role.name.lowercase(),
                        content = it.content
                    )
                }
            )
            val response = apiService.getCompletion(request)
            val reply = response.choices.firstOrNull()?.message?.content ?: ""
            Result.success(reply)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
