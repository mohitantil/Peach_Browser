package com.example.pikachubrowser.domain.model

data class LlmMessage(
    val role: Role,
    val content: String
) {
    enum class Role {
        SYSTEM, USER, ASSISTANT
    }
}
