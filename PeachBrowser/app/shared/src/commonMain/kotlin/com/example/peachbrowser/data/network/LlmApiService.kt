package com.example.peachbrowser.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.7
)

@Serializable
data class GroqMessage(
    val role: String,
    val content: String
)

@Serializable
data class GroqResponse(
    val choices: List<GroqChoice>
)

@Serializable
data class GroqChoice(
    val message: GroqMessage
)

class LlmApiService(private val httpClient: HttpClient) {
    // In production, keys should be provided by a local secure vault / BYOK system
    // Hardcoded empty for safety in this architectural MVP
    private val apiKey = "dummy_key"
    private val endpoint = "https://api.groq.com/openai/v1/chat/completions"

    suspend fun getCompletion(request: GroqRequest): GroqResponse {
        return httpClient.post(endpoint) {
            header("Authorization", "Bearer \$apiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}
