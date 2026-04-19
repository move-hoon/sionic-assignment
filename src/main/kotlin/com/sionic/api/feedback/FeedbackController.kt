package com.sionic.api.feedback

import com.sionic.api.feedback.dto.CreateFeedbackRequest
import com.sionic.api.feedback.dto.FeedbackResponse
import com.sionic.api.feedback.dto.UpdateFeedbackStatusRequest
import com.sionic.domain.feedback.service.FeedbackService
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
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/feedbacks")
@Tag(name = "Feedback", description = "피드백 생성, 조회, 상태 변경 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
class FeedbackController(private val feedbackService: FeedbackService) {

    @PostMapping
    @Operation(summary = "피드백 생성", description = "특정 chat에 대한 긍정/부정 피드백을 생성합니다.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "201", description = "피드백 생성 성공"),
            SwaggerApiResponse(
                responseCode = "401",
                description = "JWT 인증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            SwaggerApiResponse(
                responseCode = "403",
                description = "본인 chat이 아니어서 생성 불가",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "대상 chat 또는 thread를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            SwaggerApiResponse(
                responseCode = "409",
                description = "이미 해당 chat에 피드백을 작성함",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun createFeedback(
        @CurrentUser authUser: AuthUser,
        @RequestBody @Valid request: CreateFeedbackRequest,
    ): ResponseEntity<ApiResponse<FeedbackResponse>> {
        val feedback = feedbackService.createFeedback(
            userId = authUser.id,
            chatId = request.validatedChatId(),
            isPositive = request.validatedIsPositive(),
            isAdmin = authUser.isAdmin,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.ok(
                FeedbackResponse.of(
                    id = feedback.id,
                    userId = feedback.userId,
                    chatId = feedback.chatId,
                    isPositive = feedback.isPositive,
                    status = feedback.status,
                    createdAt = feedback.createdAt,
                )
            )
        )
    }

    @GetMapping
    @Operation(summary = "피드백 목록 조회", description = "피드백 목록을 페이지네이션/정렬/필터 조건으로 조회합니다.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
            SwaggerApiResponse(
                responseCode = "400",
                description = "잘못된 direction 또는 요청 파라미터",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "JWT 인증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun listFeedbacks(
        @CurrentUser authUser: AuthUser,
        @RequestParam(required = false) isPositive: Boolean?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "DESC") direction: String,
    ): ResponseEntity<ApiResponse<Page<FeedbackResponse>>> {
        val feedbacks = feedbackService.listFeedbacks(
            userId = authUser.id,
            isAdmin = authUser.isAdmin,
            isPositive = isPositive,
            page = page,
            size = size,
            direction = parseDirection(direction),
        )
        val response = feedbacks.map { feedback ->
            FeedbackResponse.of(
                id = feedback.id,
                userId = feedback.userId,
                chatId = feedback.chatId,
                isPositive = feedback.isPositive,
                status = feedback.status,
                createdAt = feedback.createdAt,
            )
        }
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    private fun parseDirection(direction: String): Sort.Direction =
        try {
            Sort.Direction.fromString(direction)
        } catch (_: IllegalArgumentException) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "direction must be ASC or DESC")
    }

    @PatchMapping("/{feedbackId}/status")
    @Operation(summary = "피드백 상태 변경", description = "관리자가 피드백 상태를 `PENDING` 또는 `RESOLVED`로 변경합니다.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "상태 변경 성공"),
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
            SwaggerApiResponse(
                responseCode = "404",
                description = "대상 피드백이 존재하지 않음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun updateFeedbackStatus(
        @CurrentUser authUser: AuthUser,
        @PathVariable feedbackId: UUID,
        @RequestBody @Valid request: UpdateFeedbackStatusRequest,
    ): ResponseEntity<ApiResponse<FeedbackResponse>> {
        if (!authUser.isAdmin) {
            throw BusinessException(ErrorCode.FORBIDDEN)
        }

        val feedback = feedbackService.updateFeedbackStatus(
            feedbackId = feedbackId,
            newStatus = request.validatedStatus(),
        )
        return ResponseEntity.ok(
            ApiResponse.ok(
                FeedbackResponse.of(
                    id = feedback.id,
                    userId = feedback.userId,
                    chatId = feedback.chatId,
                    isPositive = feedback.isPositive,
                    status = feedback.status,
                    createdAt = feedback.createdAt,
                )
            )
        )
    }
}
