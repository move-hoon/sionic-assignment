package com.sionic.global.client.openai

import com.fasterxml.jackson.annotation.JsonProperty
import com.sionic.global.client.dto.AiResponse
import com.sionic.global.exception.BusinessException
import com.sionic.global.exception.ErrorCode

data class OpenAiChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
) {
    fun toAiResponse(): AiResponse {
        val content = choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "OpenAI response did not contain assistant text")

        return AiResponse(
            content = content,
            model = model ?: throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "OpenAI response did not contain model metadata"),
            usage = usage?.toTokenUsage() ?: AiResponse.TokenUsage(0, 0, 0),
        )
    }

    data class Choice(
        val index: Int = 0,
        val message: Message = Message(),
        @JsonProperty("finish_reason")
        val finishReason: String? = null,
    )

    data class Message(
        val role: String? = null,
        val content: String? = null,
    )

    data class Usage(
        @JsonProperty("prompt_tokens")
        val promptTokens: Int = 0,
        @JsonProperty("completion_tokens")
        val completionTokens: Int = 0,
        @JsonProperty("total_tokens")
        val totalTokens: Int = 0,
    ) {
        fun toTokenUsage(): AiResponse.TokenUsage =
            AiResponse.TokenUsage(
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
            )
    }
}

data class OpenAiChatCompletionChunk(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: OpenAiChatCompletionResponse.Usage? = null,
) {
    data class Choice(
        val index: Int = 0,
        val delta: Delta = Delta(),
        @JsonProperty("finish_reason")
        val finishReason: String? = null,
    )

    data class Delta(
        val role: String? = null,
        val content: String? = null,
    )
}
