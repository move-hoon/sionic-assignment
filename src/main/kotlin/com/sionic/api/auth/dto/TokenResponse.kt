package com.sionic.api.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "JWT 토큰 응답")
data class TokenResponse(
    @field:Schema(description = "발급된 JWT 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
    val token: String,
) {
    companion object {
        fun of(token: String) = TokenResponse(token)
    }
}
