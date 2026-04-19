package com.sionic.api.feedback.dto

import com.sionic.domain.feedback.enums.FeedbackStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "피드백 응답")
data class FeedbackResponse(
    @field:Schema(description = "피드백 ID")
    val id: UUID,
    @field:Schema(description = "피드백 작성 사용자 ID")
    val userId: UUID,
    @field:Schema(description = "대상 chat ID")
    val chatId: UUID,
    @field:Schema(description = "긍정 여부")
    val isPositive: Boolean,
    @field:Schema(description = "피드백 상태")
    val status: FeedbackStatus,
    @field:Schema(description = "생성 시각(UTC)")
    val createdAt: Instant,
) {
    companion object {
        fun of(id: UUID, userId: UUID, chatId: UUID, isPositive: Boolean, status: FeedbackStatus, createdAt: Instant) =
            FeedbackResponse(
                id = id,
                userId = userId,
                chatId = chatId,
                isPositive = isPositive,
                status = status,
                createdAt = createdAt,
            )
    }
}
