package com.sionic.global.client.gemini

data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig,
) {
    data class Content(val parts: List<Part>)
    data class Part(val text: String)
    data class GenerationConfig(val maxOutputTokens: Int)

    companion object {
        fun of(prompt: String, maxTokens: Int): GeminiRequest = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(prompt)))),
            generationConfig = GenerationConfig(maxOutputTokens = maxTokens),
        )
    }
}
