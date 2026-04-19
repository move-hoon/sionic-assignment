package com.sionic.support

import com.sionic.global.client.AiClient
import com.sionic.global.client.dto.AiRequest
import com.sionic.global.client.dto.AiResponse
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList

@TestConfiguration
class FakeAiClientConfig {

    @Bean
    fun fakeAiClientRecorder(): FakeAiClientRecorder = FakeAiClientRecorder()

    @Bean
    @Primary
    fun fakeAiClient(recorder: FakeAiClientRecorder): AiClient = object : AiClient {
        override fun complete(request: AiRequest): AiResponse {
            recorder.record(request)
            return createStubResponse(request)
        }

        override fun stream(request: AiRequest, onDelta: (String) -> Unit): AiResponse {
            recorder.record(request)
            val response = createStubResponse(request)
            response.content.chunked(12).forEach(onDelta)
            return response
        }

        private fun createStubResponse(request: AiRequest): AiResponse {
            val lastUserMessage = request.messages.lastOrNull { it.role == "user" }?.content ?: "empty"
            return AiResponse(
                content = "stubbed-answer:$lastUserMessage",
                model = request.model ?: "fake-test-model",
                usage = AiResponse.TokenUsage(
                    promptTokens = 10,
                    completionTokens = 5,
                    totalTokens = 15,
                ),
            )
        }
    }

    @Bean
    fun testClock(): Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
}

class FakeAiClientRecorder {
    private val recordedRequests = CopyOnWriteArrayList<AiRequest>()

    val requests: List<AiRequest>
        get() = recordedRequests.toList()

    fun record(request: AiRequest) {
        recordedRequests += request
    }

    fun clear() {
        recordedRequests.clear()
    }
}
