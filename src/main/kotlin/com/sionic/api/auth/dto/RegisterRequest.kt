package com.sionic.api.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "회원가입 요청")
data class RegisterRequest(
    @field:Schema(description = "이메일", example = "user@example.com")
    @field:NotBlank
    @field:Email
    val email: String?,

    @field:Schema(description = "비밀번호", example = "Test1234!")
    @field:NotBlank
    @field:Size(min = 8)
    val password: String?,

    @field:Schema(description = "이름", example = "홍길동")
    @field:NotBlank
    @field:Size(min = 2, max = 50)
    val name: String?,
) {
    fun validatedEmail() = email!!
    fun validatedPassword() = password!!
    fun validatedName() = name!!
}
