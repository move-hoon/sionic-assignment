package com.sionic.global.client

import com.sionic.global.client.dto.AiRequest
import com.sionic.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeminiAiClientTest {

    private val restClientBuilder = RestClient.builder()
        .baseUrl("https://generativelanguage.googleapis.com")

    private val server = MockRestServiceServer.bindTo(restClientBuilder).build()

    private val aiClient = GeminiAiClient(
        aiRestClient = restClientBuilder.build(),
        apiKey = "test-key",
        model = "gemini-2.5-flash",
    )

    @Test
    fun `GeminiAiClient는 요청과 응답을 올바르게 매핑한다`() {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=test-key"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                content().json(
                    """
                    {
                      "contents": [{"parts": [{"text": "hello"}]}],
                      "generationConfig": {"maxOutputTokens": 128}
                    }
                    """.trimIndent(),
                ),
            )
            .andRespond(
                withSuccess(
                    """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [{"text": "pong"}]
                          }
                        }
                      ],
                      "usageMetadata": {
                        "promptTokenCount": 5,
                        "candidatesTokenCount": 1,
                        "totalTokenCount": 6
                      },
                      "modelVersion": "gemini-2.5-flash"
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = aiClient.complete(AiRequest(prompt = "hello", maxTokens = 128))

        assertEquals("pong", response.content)
        assertEquals("gemini-2.5-flash", response.model)
        assertEquals(6, response.usage.totalTokens)
    }

    @Test
    fun `GeminiAiClient는 외부 API 오류를 BusinessException으로 변환한다`() {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=test-key"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("""{"error":"upstream"}""").contentType(MediaType.APPLICATION_JSON))

        assertFailsWith<BusinessException> {
            aiClient.complete(AiRequest(prompt = "hello"))
        }
    }

    @Test
    fun `GeminiAiClient는 빈 응답 본문을 BusinessException으로 변환한다`() {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=test-key"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("null", MediaType.APPLICATION_JSON))

        assertFailsWith<BusinessException> {
            aiClient.complete(AiRequest(prompt = "hello"))
        }
    }
}
