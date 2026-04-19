package com.sionic.global.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sionic.global.client.dto.AiRequest
import com.sionic.global.exception.BusinessException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenAiChatClientTest {

    private val restClientBuilder = RestClient.builder()
        .baseUrl("https://api.openai.com")

    private val server = MockRestServiceServer.bindTo(restClientBuilder).build()

    private val aiClient = OpenAiChatClient(
        aiRestClient = restClientBuilder.build(),
        objectMapper = jacksonObjectMapper(),
        apiKey = "test-key",
        defaultModel = "gpt-5.4",
    )

    @Test
    fun `OpenAiChatClient는 chat completions 요청과 응답을 올바르게 매핑한다`() {
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
            .andExpect(
                content().json(
                    """
                    {
                      "model": "gpt-5.4",
                      "messages": [
                        {"role": "developer", "content": "You are a helpful assistant."},
                        {"role": "user", "content": "hello"}
                      ],
                      "max_completion_tokens": 128,
                      "stream": false
                    }
                    """.trimIndent(),
                    JsonCompareMode.LENIENT,
                ),
            )
            .andRespond(
                withSuccess(
                    """
                    {
                      "id": "chatcmpl_123",
                      "model": "gpt-5.4",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": "pong"
                          },
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 10,
                        "completion_tokens": 3,
                        "total_tokens": 13
                      }
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = aiClient.complete(
            AiRequest(
                model = "gpt-5.4",
                maxCompletionTokens = 128,
                messages = listOf(
                    AiRequest.AiMessage(role = "developer", content = "You are a helpful assistant."),
                    AiRequest.AiMessage(role = "user", content = "hello"),
                ),
            )
        )

        assertEquals("pong", response.content)
        assertEquals("gpt-5.4", response.model)
        assertEquals(13, response.usage.totalTokens)
    }

    @Test
    fun `OpenAiChatClient는 stream 응답 delta를 합쳐서 반환한다`() {
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """
                    data: {"id":"chatcmpl_123","model":"gpt-5.4","choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"},"finish_reason":null}],"usage":null}
                    
                    data: {"id":"chatcmpl_123","model":"gpt-5.4","choices":[{"index":0,"delta":{"content":"lo"},"finish_reason":null}],"usage":null}
                    
                    data: {"id":"chatcmpl_123","model":"gpt-5.4","choices":[],"usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}}
                    
                    data: [DONE]
                    """.trimIndent(),
                    MediaType.TEXT_EVENT_STREAM,
                ),
            )

        val deltas = mutableListOf<String>()
        val response = aiClient.stream(
            AiRequest(
                model = "gpt-5.4",
                stream = true,
                messages = listOf(AiRequest.AiMessage(role = "user", content = "hello")),
            ),
            deltas::add,
        )

        assertEquals(listOf("Hel", "lo"), deltas)
        assertEquals("Hello", response.content)
        assertEquals(12, response.usage.totalTokens)
    }

    @Test
    fun `OpenAiChatClient는 외부 API 오류를 BusinessException으로 변환한다`() {
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("""{"error":"upstream"}""").contentType(MediaType.APPLICATION_JSON))

        assertFailsWith<BusinessException> {
            aiClient.complete(AiRequest(messages = listOf(AiRequest.AiMessage(role = "user", content = "hello"))))
        }
    }
}
