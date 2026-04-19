package com.sionic.global.client.dto

data class AiRequest(
    val prompt: String,
    val maxTokens: Int = 1024,
)
