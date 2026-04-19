package com.sionic.global.client.gemini

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.sionic.global.client.dto.AiResponse
import com.sionic.global.exception.BusinessException
import com.sionic.global.exception.ErrorCode

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiResponse(
    val candidates: List<Candidate> = emptyList(),
    val usageMetadata: UsageMetadata = UsageMetadata(),
    val modelVersion: String = "",
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Candidate(
        val content: Content = Content(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Content(
        val parts: List<Part> = emptyList(),
    )

    data class Part(val text: String = "")

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UsageMetadata(
        val promptTokenCount: Int = 0,
        val candidatesTokenCount: Int = 0,
        val totalTokenCount: Int = 0,
    )

    private val responseText: String?
        get() = candidates.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?.takeIf { it.isNotBlank() }

    fun toAiResponse(): AiResponse = AiResponse(
        content = responseText
            ?: throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Gemini 응답에서 텍스트를 찾을 수 없습니다."),
        model = modelVersion,
        usage = AiResponse.TokenUsage(
            promptTokens = usageMetadata.promptTokenCount,
            completionTokens = usageMetadata.candidatesTokenCount,
            totalTokens = usageMetadata.totalTokenCount,
        ),
    )
}
