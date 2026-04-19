package com.sionic.global.client.dto

data class AiRequest(
    val messages: List<AiMessage>,
    val model: String? = null,
    val maxCompletionTokens: Int = 1024,
    val stream: Boolean = false,
) {
    data class AiMessage(
        val role: String,
        val content: String,
    )
}
