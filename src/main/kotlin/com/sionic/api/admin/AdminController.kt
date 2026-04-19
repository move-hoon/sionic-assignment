package com.sionic.api.admin

import com.sionic.api.admin.dto.ActivityResponse
import com.sionic.domain.admin.service.AdminService
import com.sionic.global.auth.AuthUser
import com.sionic.global.auth.CurrentUser
import com.sionic.global.config.OpenApiConfig
import com.sionic.global.exception.BusinessException
import com.sionic.global.exception.ErrorCode
import com.sionic.global.response.ApiResponse
import com.sionic.global.response.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "관리자 전용 분석 및 보고 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
class AdminController(private val adminService: AdminService) {

    @GetMapping("/activity")
    @Operation(summary = "최근 24시간 활동 집계", description = "회원가입, 로그인, chat 생성 수를 최근 24시간 기준으로 집계합니다.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "집계 성공"),
            SwaggerApiResponse(
                responseCode = "401",
                description = "JWT 인증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            SwaggerApiResponse(
                responseCode = "403",
                description = "관리자 권한이 아님",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun getActivity(@CurrentUser authUser: AuthUser): ResponseEntity<ApiResponse<ActivityResponse>> {
        if (!authUser.isAdmin) {
            throw BusinessException(ErrorCode.FORBIDDEN)
        }

        val activity = adminService.getActivity()
        return ResponseEntity.ok(ApiResponse.ok(ActivityResponse.of(activity)))
    }

    @GetMapping("/report")
    @Operation(summary = "CSV 보고서 생성", description = "최근 24시간 chat 데이터를 CSV로 다운로드합니다.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "CSV 생성 성공"),
            SwaggerApiResponse(
                responseCode = "401",
                description = "JWT 인증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            SwaggerApiResponse(
                responseCode = "403",
                description = "관리자 권한이 아님",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun getReport(@CurrentUser authUser: AuthUser): ResponseEntity<ByteArray> {
        if (!authUser.isAdmin) {
            throw BusinessException(ErrorCode.FORBIDDEN)
        }

        val csv = adminService.generateReport()
        val headers = HttpHeaders().apply {
            contentType = MediaType.parseMediaType("text/csv;charset=UTF-8")
            contentDisposition = org.springframework.http.ContentDisposition
                .attachment()
                .filename("report.csv")
                .build()
        }

        return ResponseEntity.ok()
            .headers(headers)
            .body(csv.toByteArray(Charsets.UTF_8))
    }
}
