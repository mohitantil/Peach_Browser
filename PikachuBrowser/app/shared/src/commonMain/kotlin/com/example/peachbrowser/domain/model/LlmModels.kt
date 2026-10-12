package com.example.peachbrowser.domain.model

data class LlmMessage(
    val role: Role,
    val content: String
) {
    enum class Role {
        SYSTEM, USER, ASSISTANT
    }
}
