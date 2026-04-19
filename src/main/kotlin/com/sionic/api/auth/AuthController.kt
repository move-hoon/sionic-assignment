package com.sionic.api.auth

import com.sionic.api.auth.dto.LoginRequest
import com.sionic.api.auth.dto.RegisterRequest
import com.sionic.api.auth.dto.TokenResponse
import com.sionic.domain.auth.service.AuthService
import com.sionic.global.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@RequestBody @Valid request: RegisterRequest): ResponseEntity<ApiResponse<TokenResponse>> {
        val token = authService.register(
            email = request.validatedEmail(),
            password = request.validatedPassword(),
            name = request.validatedName(),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(TokenResponse.of(token)))
    }

    @PostMapping("/login")
    fun login(@RequestBody @Valid request: LoginRequest): ResponseEntity<ApiResponse<TokenResponse>> {
        val token = authService.login(
            email = request.validatedEmail(),
            password = request.validatedPassword(),
        )
        return ResponseEntity.ok(ApiResponse.ok(TokenResponse.of(token)))
    }
}
