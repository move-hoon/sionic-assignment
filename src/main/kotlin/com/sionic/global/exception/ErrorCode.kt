package com.sionic.global.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String
) {
    // 4xx
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C002", "요청한 리소스를 찾을 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "C003", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "C004", "접근 권한이 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C009", "지원하지 않는 HTTP 메서드입니다."),

    // 5xx
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C005", "서버 내부 오류가 발생했습니다."),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "C006", "외부 API 호출에 실패했습니다."),
    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "C007", "요청 처리 시간이 초과되었습니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "C008", "요청 횟수 제한을 초과했습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U002", "이미 사용 중인 이메일입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "U003", "이메일 또는 비밀번호가 올바르지 않습니다."),

    // Thread / Chat
    THREAD_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "스레드를 찾을 수 없습니다."),
    CHAT_NOT_FOUND(HttpStatus.NOT_FOUND, "T002", "대화를 찾을 수 없습니다."),

    // Feedback
    FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "F001", "피드백을 찾을 수 없습니다."),
    FEEDBACK_DUPLICATE(HttpStatus.CONFLICT, "F002", "이미 해당 대화에 피드백을 작성했습니다."),
}
