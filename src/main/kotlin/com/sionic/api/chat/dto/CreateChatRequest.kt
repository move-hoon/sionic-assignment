package com.sionic.api.chat.dto

import jakarta.validation.constraints.NotBlank
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "대화 생성 요청")
data class CreateChatRequest(
    @field:Schema(description = "사용자 질문", example = "Spring Boot에서 JWT 인증을 어떻게 구현하나요?")
    @field:NotBlank
    val question: String?,

    @field:Schema(description = "스트리밍 응답 여부", example = "false")
    val isStreaming: Boolean = false,

    @field:Schema(description = "응답 생성에 사용할 모델명", nullable = true, example = "gpt-5.4")
    val model: String? = null,
) {
    fun validatedQuestion() = question!!
}
