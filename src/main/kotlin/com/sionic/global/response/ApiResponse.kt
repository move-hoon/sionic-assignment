package com.sionic.global.response

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)
        fun ok(): ApiResponse<Unit> = ApiResponse(success = true)
    }
}
