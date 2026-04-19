package com.sionic.global.client

import com.sionic.global.client.dto.AiRequest
import com.sionic.global.client.dto.AiResponse
import com.sionic.global.client.gemini.GeminiRequest
import com.sionic.global.client.gemini.GeminiResponse
import com.sionic.global.exception.BusinessException
import com.sionic.global.exception.ErrorCode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class GeminiAiClient(
    private val aiRestClient: RestClient,

    @param:Value("\${ai.api-key}")
    private val apiKey: String,

    @param:Value("\${ai.model:gemini-2.5-flash}")
    private val model: String
) : AiClient {

    private val log = KotlinLogging.logger {}

    override fun complete(request: AiRequest): AiResponse {
        log.debug { "Gemini request: model=$model, prompt=${request.prompt.take(50)}..." }

        val response = aiRestClient.post()
            .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
            .body(GeminiRequest.of(request.prompt, request.maxTokens))
            .retrieve()
            .onStatus({ it.isError }) { _, res ->
                val body = res.body.bufferedReader().use { it.readText() }
                log.error { "Gemini API error: status=${res.statusCode}, body=$body" }
                throw BusinessException(ErrorCode.EXTERNAL_API_ERROR)
            }
            .body<GeminiResponse>()
            ?: throw BusinessException(ErrorCode.EXTERNAL_API_ERROR)

        log.debug { "Gemini response: tokens=${response.usageMetadata.totalTokenCount}" }
        return response.toAiResponse()
    }
}
