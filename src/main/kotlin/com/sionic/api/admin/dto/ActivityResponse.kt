package com.sionic.api.admin.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "최근 24시간 활동 집계 응답")
data class ActivityResponse(
    @field:Schema(description = "회원가입 수", example = "5")
    val signupCount: Long,
    @field:Schema(description = "로그인 수", example = "12")
    val loginCount: Long,
    @field:Schema(description = "chat 생성 수", example = "34")
    val chatCount: Long,
) {
    companion object {
        fun of(data: Map<String, Long>) = ActivityResponse(
            signupCount = data["signupCount"] ?: 0L,
            loginCount = data["loginCount"] ?: 0L,
            chatCount = data["chatCount"] ?: 0L,
        )
    }
}
