package com.sionic.global.response

import com.sionic.global.exception.ErrorCode

data class ErrorResponse(
    val code: String,
    val message: String,
) {
    companion object {
        fun of(errorCode: ErrorCode): ErrorResponse =
            ErrorResponse(code = errorCode.code, message = errorCode.message)

        fun of(errorCode: ErrorCode, message: String): ErrorResponse =
            ErrorResponse(code = errorCode.code, message = message)
    }
}
