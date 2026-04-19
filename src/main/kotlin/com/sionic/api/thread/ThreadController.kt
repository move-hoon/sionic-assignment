package com.sionic.api.thread

import com.sionic.domain.chat.service.ChatService
import com.sionic.global.auth.AuthUser
import com.sionic.global.auth.CurrentUser
import com.sionic.global.config.OpenApiConfig
import com.sionic.global.response.ApiResponse
import com.sionic.global.response.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/threads")
@Tag(name = "Thread", description = "스레드 삭제 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
class ThreadController(private val chatService: ChatService) {

    @DeleteMapping("/{threadId}")
    @Operation(summary = "스레드 삭제", description = "본인이 생성한 thread만 삭제할 수 있으며, 관련 chat/feedback도 함께 정리됩니다.")
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "삭제 성공"),
            SwaggerApiResponse(
                responseCode = "401",
                description = "JWT 인증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            SwaggerApiResponse(
                responseCode = "403",
                description = "본인 thread가 아니어서 삭제 불가",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "대상 thread를 찾을 수 없음",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun deleteThread(
        @CurrentUser authUser: AuthUser,
        @PathVariable threadId: UUID,
    ): ResponseEntity<ApiResponse<Unit>> {
        chatService.deleteThread(threadId, authUser.id)
        return ResponseEntity.ok(ApiResponse.ok())
    }
}
