package com.sionic.global.exception

class BusinessException(
    val errorCode: ErrorCode,
    message: String = errorCode.message,
) : RuntimeException(message)
