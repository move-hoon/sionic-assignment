package com.sionic.global.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "공통 성공 응답 포맷")
data class ApiResponse<T>(
    @field:Schema(description = "요청 성공 여부", example = "true")
    val success: Boolean,
    @field:Schema(description = "실제 응답 데이터")
    val data: T? = null,
    @field:Schema(description = "부가 메시지", nullable = true, example = "요청이 성공했습니다.")
    val message: String? = null,
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)
        fun ok(): ApiResponse<Unit> = ApiResponse(success = true)
    }
}
