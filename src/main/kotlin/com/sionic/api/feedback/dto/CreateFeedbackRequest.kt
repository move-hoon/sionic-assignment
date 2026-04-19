package com.sionic.api.feedback.dto

import jakarta.validation.constraints.NotNull
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "피드백 생성 요청")
data class CreateFeedbackRequest(
    @field:Schema(description = "피드백 대상 chat ID")
    @field:NotNull
    val chatId: UUID?,

    @field:Schema(description = "긍정 여부", example = "true")
    @field:NotNull
    val isPositive: Boolean?,
) {
    fun validatedChatId(): UUID = chatId!!
    fun validatedIsPositive() = isPositive!!
}
