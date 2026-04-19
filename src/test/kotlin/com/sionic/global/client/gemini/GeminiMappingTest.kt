package com.sionic.global.client.gemini

import com.sionic.global.exception.BusinessException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeminiMappingTest {

    @Test
    fun `GeminiRequest는 Gemini API 포맷으로 변환된다`() {
        val request = GeminiRequest.of(prompt = "hello", maxTokens = 256)

        assertEquals("hello", request.contents.single().parts.single().text)
        assertEquals(256, request.generationConfig.maxOutputTokens)
    }

    @Test
    fun `GeminiResponse는 내부 AiResponse로 변환된다`() {
        val response = GeminiResponse(
            candidates = listOf(
                GeminiResponse.Candidate(
                    content = GeminiResponse.Content(
                        parts = listOf(GeminiResponse.Part(text = "gemini result")),
                    ),
                ),
            ),
            usageMetadata = GeminiResponse.UsageMetadata(
                promptTokenCount = 10,
                candidatesTokenCount = 20,
                totalTokenCount = 30,
            ),
            modelVersion = "gemini-2.5-flash",
        )

        val mapped = response.toAiResponse()

        assertEquals("gemini result", mapped.content)
        assertEquals("gemini-2.5-flash", mapped.model)
        assertEquals(10, mapped.usage.promptTokens)
        assertEquals(20, mapped.usage.completionTokens)
        assertEquals(30, mapped.usage.totalTokens)
    }

    @Test
    fun `GeminiResponse 텍스트가 비어 있으면 BusinessException을 던진다`() {
        val response = GeminiResponse()

        assertFailsWith<BusinessException> {
            response.toAiResponse()
        }
    }
}
