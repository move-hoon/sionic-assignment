package com.sionic.api.feedback.dto

import com.sionic.domain.feedback.enums.FeedbackStatus
import jakarta.validation.constraints.NotNull
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "피드백 상태 변경 요청")
data class UpdateFeedbackStatusRequest(
    @field:Schema(description = "변경할 상태", example = "RESOLVED")
    @field:NotNull
    val status: FeedbackStatus?,
) {
    fun validatedStatus() = status!!
}
