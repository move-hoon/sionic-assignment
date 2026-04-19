package com.sionic.global.client.dto

data class AiResponse(
    val content: String,
    val model: String,
    val usage: TokenUsage,
) {
    data class TokenUsage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
    )
}
