package com.sionic.api.chat.dto

import java.time.Instant
import java.util.UUID
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "단일 대화 응답")
data class ChatResponse(
    @field:Schema(description = "대화 ID")
    val id: UUID,
    @field:Schema(description = "소속 thread ID")
    val threadId: UUID,
    @field:Schema(description = "질문 내용")
    val question: String,
    @field:Schema(description = "AI 답변")
    val answer: String,
    @field:Schema(description = "사용된 모델명", nullable = true)
    val model: String?,
    @field:Schema(description = "생성 시각(UTC)")
    val createdAt: Instant,
) {
    companion object {
        fun of(id: UUID, threadId: UUID, question: String, answer: String, model: String?, createdAt: Instant) =
            ChatResponse(
                id = id,
                threadId = threadId,
                question = question,
                answer = answer,
                model = model,
                createdAt = createdAt,
            )
    }
}
