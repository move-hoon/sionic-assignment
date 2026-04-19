package com.sionic.api.auth

import com.sionic.api.auth.dto.LoginRequest
import com.sionic.api.auth.dto.RegisterRequest
import com.sionic.api.auth.dto.TokenResponse
import com.sionic.domain.auth.service.AuthService
import com.sionic.global.response.ApiResponse
import com.sionic.global.response.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "회원가입과 로그인 API")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    @Operation(summary = "회원가입", description = "이메일, 패스워드, 이름으로 회원가입하고 JWT 토큰을 발급합니다.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "201", description = "회원가입 성공"),
            SwaggerApiResponse(
                responseCode = "400",
                description = "입력값 검증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            SwaggerApiResponse(
                responseCode = "409",
                description = "중복 이메일",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun register(@RequestBody @Valid request: RegisterRequest): ResponseEntity<ApiResponse<TokenResponse>> {
        val token = authService.register(
            email = request.validatedEmail(),
            password = request.validatedPassword(),
            name = request.validatedName(),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(TokenResponse.of(token)))
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 패스워드로 로그인하고 JWT 토큰을 발급합니다.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "로그인 성공"),
            SwaggerApiResponse(
                responseCode = "400",
                description = "입력값 검증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "이메일 또는 패스워드 불일치",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun login(@RequestBody @Valid request: LoginRequest): ResponseEntity<ApiResponse<TokenResponse>> {
        val token = authService.login(
            email = request.validatedEmail(),
            password = request.validatedPassword(),
        )
        return ResponseEntity.ok(ApiResponse.ok(TokenResponse.of(token)))
    }
}
