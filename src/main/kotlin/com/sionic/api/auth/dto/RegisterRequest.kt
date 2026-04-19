package com.sionic.api.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank
    @field:Email
    val email: String?,

    @field:NotBlank
    @field:Size(min = 8)
    val password: String?,

    @field:NotBlank
    @field:Size(min = 2, max = 50)
    val name: String?,
) {
    fun validatedEmail() = email!!
    fun validatedPassword() = password!!
    fun validatedName() = name!!
}
