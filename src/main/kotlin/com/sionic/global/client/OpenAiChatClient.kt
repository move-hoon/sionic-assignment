package com.sionic.global.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.sionic.global.client.dto.AiRequest
import com.sionic.global.client.dto.AiResponse
import com.sionic.global.client.openai.OpenAiChatCompletionChunk
import com.sionic.global.client.openai.OpenAiChatCompletionRequest
import com.sionic.global.client.openai.OpenAiChatCompletionResponse
import com.sionic.global.exception.BusinessException
import com.sionic.global.exception.ErrorCode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class OpenAiChatClient(
    private val aiRestClient: RestClient,
    private val objectMapper: ObjectMapper,
    @param:Value("\${ai.api-key}")
    private val apiKey: String,
    @param:Value("\${ai.model:gpt-5.4}")
    private val defaultModel: String,
) : AiClient {

    private val log = KotlinLogging.logger {}

    override fun complete(request: AiRequest): AiResponse {
        val resolvedModel = request.model ?: defaultModel
        log.debug { "OpenAI chat completion request: model=$resolvedModel, messages=${request.messages.size}" }

        val response = aiRestClient.post()
            .uri("/v1/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(OpenAiChatCompletionRequest.from(request.copy(stream = false), resolvedModel))
            .retrieve()
            .onStatus({ it.isError }) { _, res ->
                val body = res.body.bufferedReader().use { it.readText() }
                log.error { "OpenAI API error: status=${res.statusCode}, body=$body" }
                throw BusinessException(ErrorCode.EXTERNAL_API_ERROR)
            }
            .body(OpenAiChatCompletionResponse::class.java)
            ?: throw BusinessException(ErrorCode.EXTERNAL_API_ERROR)

        return response.toAiResponse()
    }

    override fun stream(request: AiRequest, onDelta: (String) -> Unit): AiResponse {
        val resolvedModel = request.model ?: defaultModel
        log.debug { "OpenAI streaming request: model=$resolvedModel, messages=${request.messages.size}" }

        return aiRestClient.post()
            .uri("/v1/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .body(OpenAiChatCompletionRequest.from(request.copy(stream = true), resolvedModel))
            .exchange { _, response ->
                if (response.statusCode.isError) {
                    val body = response.body.bufferedReader().use { it.readText() }
                    log.error { "OpenAI streaming API error: status=${response.statusCode}, body=$body" }
                    throw BusinessException(ErrorCode.EXTERNAL_API_ERROR)
                }

                response.body.use { streamBody ->
                    parseStream(streamBody.bufferedReader(), resolvedModel, onDelta)
                }
            } ?: throw BusinessException(ErrorCode.EXTERNAL_API_ERROR)
    }

    private fun parseStream(
        reader: java.io.BufferedReader,
        fallbackModel: String,
        onDelta: (String) -> Unit,
    ): AiResponse {
        val builder = StringBuilder()
        var resolvedModel = fallbackModel
        var usage = AiResponse.TokenUsage(0, 0, 0)

        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith("data:")) {
                return@forEachLine
            }

            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]" || payload.isEmpty()) {
                return@forEachLine
            }

            val chunk = objectMapper.readValue(payload, OpenAiChatCompletionChunk::class.java)
            if (!chunk.model.isNullOrBlank()) {
                resolvedModel = chunk.model
            }
            chunk.usage?.let { usage = it.toTokenUsage() }
            chunk.choices.forEach { choice ->
                choice.delta.content?.takeIf { it.isNotEmpty() }?.let { delta ->
                    builder.append(delta)
                    onDelta(delta)
                }
            }
        }

        if (builder.isEmpty()) {
            throw BusinessException(ErrorCode.EXTERNAL_API_ERROR, "OpenAI streaming response did not contain assistant text")
        }

        return AiResponse(
            content = builder.toString(),
            model = resolvedModel,
            usage = usage,
        )
    }
}
