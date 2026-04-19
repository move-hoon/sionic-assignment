package com.sionic.global.client.openai

import com.fasterxml.jackson.annotation.JsonProperty
import com.sionic.global.client.dto.AiRequest

data class OpenAiChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    @JsonProperty("max_completion_tokens")
    val maxCompletionTokens: Int,
    val stream: Boolean = false,
    @JsonProperty("stream_options")
    val streamOptions: StreamOptions? = null,
) {
    data class Message(
        val role: String,
        val content: String,
    )

    data class StreamOptions(
        @JsonProperty("include_usage")
        val includeUsage: Boolean = true,
    )

    companion object {
        fun from(request: AiRequest, resolvedModel: String): OpenAiChatCompletionRequest =
            OpenAiChatCompletionRequest(
                model = resolvedModel,
                messages = request.messages.map { Message(role = it.role, content = it.content) },
                maxCompletionTokens = request.maxCompletionTokens,
                stream = request.stream,
                streamOptions = if (request.stream) StreamOptions() else null,
            )
    }
}
