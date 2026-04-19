package com.sionic.global.response

import com.sionic.global.exception.ErrorCode
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "공통 오류 응답 포맷")
data class ErrorResponse(
    @field:Schema(description = "애플리케이션 오류 코드", example = "C003")
    val code: String,
    @field:Schema(description = "오류 메시지", example = "인증이 필요합니다.")
    val message: String,
) {
    companion object {
        fun of(errorCode: ErrorCode): ErrorResponse =
            ErrorResponse(code = errorCode.code, message = errorCode.message)

        fun of(errorCode: ErrorCode, message: String): ErrorResponse =
            ErrorResponse(code = errorCode.code, message = message)
    }
}
