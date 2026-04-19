package com.sionic.api.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "로그인 요청")
data class LoginRequest(
    @field:Schema(description = "이메일", example = "user@example.com")
    @field:NotBlank
    @field:Email
    val email: String?,

    @field:Schema(description = "비밀번호", example = "Test1234!")
    @field:NotBlank
    val password: String?,
) {
    fun validatedEmail() = email!!
    fun validatedPassword() = password!!
}
