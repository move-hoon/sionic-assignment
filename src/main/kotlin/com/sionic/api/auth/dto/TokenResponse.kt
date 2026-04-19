package com.sionic.api.auth.dto

data class TokenResponse(val token: String) {
    companion object {
        fun of(token: String) = TokenResponse(token)
    }
}
