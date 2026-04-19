package com.sionic.api.chat

import com.fasterxml.jackson.databind.ObjectMapper
import com.sionic.api.chat.dto.ChatResponse
import com.sionic.api.chat.dto.CreateChatRequest
import com.sionic.api.chat.dto.ThreadWithChatsResponse
import com.sionic.domain.chat.service.ChatService
import com.sionic.domain.chat.service.ChatSummary
import com.sionic.domain.chat.service.ThreadWithChatsView
import com.sionic.global.auth.AuthUser
import com.sionic.global.auth.CurrentUser
import com.sionic.global.exception.BusinessException
import com.sionic.global.exception.ErrorCode
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
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping("/api/v1/chats")
@Tag(name = "Chat", description = "대화 생성 및 스레드 단위 조회 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
class ChatController(
    private val chatService: ChatService,
    private val objectMapper: ObjectMapper,
) {

    @PostMapping
    @Operation(
        summary = "대화 생성",
        description = "질문을 입력받아 AI 답변을 생성합니다. `isStreaming=true`면 SSE 응답을 반환하고, 기본값은 일반 JSON 응답입니다.",
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "201", description = "대화 생성 성공 (비스트리밍)"),
            SwaggerApiResponse(responseCode = "200", description = "대화 생성 성공 (스트리밍, text/event-stream)"),
            SwaggerApiResponse(
                responseCode = "400",
                description = "입력값 검증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "JWT 인증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            SwaggerApiResponse(
                responseCode = "502",
                description = "외부 AI 호출 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun createChat(
        @CurrentUser authUser: AuthUser,
        @RequestBody @Valid request: CreateChatRequest,
    ): ResponseEntity<Any> =
        if (request.isStreaming) {
            streamChat(authUser, request)
        } else {
            val chat = chatService.createChat(
                userId = authUser.id,
                question = request.validatedQuestion(),
                model = request.model,
            )
            ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.ok(
                    ChatResponse.of(
                        id = chat.id,
                        threadId = chat.threadId,
                        question = chat.question,
                        answer = chat.answer,
                        model = chat.model,
                        createdAt = chat.createdAt,
                    )
                ) as Any
            )
    }

    @GetMapping
    @Operation(
        summary = "대화 목록 조회",
        description = "스레드 단위로 그룹화된 대화 목록을 조회합니다. 일반 사용자는 본인 데이터만, 관리자는 전체 데이터를 조회할 수 있습니다.",
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
            SwaggerApiResponse(
                responseCode = "400",
                description = "정렬 방향 등 요청 파라미터가 잘못됨",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "JWT 인증 실패",
                content = [Content(schema = Schema(implementation = ErrorResponse::class))],
            ),
        ],
    )
    fun listChats(
        @CurrentUser authUser: AuthUser,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "DESC") direction: String,
    ): ResponseEntity<ApiResponse<Page<ThreadWithChatsResponse>>> {
        val threads = chatService.listChats(
            userId = authUser.id,
            isAdmin = authUser.isAdmin,
            page = page,
            size = size,
            direction = parseDirection(direction),
        )
        val response = threads.map(::toThreadWithChatsResponse)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    private fun streamChat(authUser: AuthUser, request: CreateChatRequest): ResponseEntity<Any> {
        val emitter = SseEmitter(0L)

        CompletableFuture.runAsync {
            try {
                val chat = chatService.streamChat(
                    userId = authUser.id,
                    question = request.validatedQuestion(),
                    model = request.model,
                ) { delta ->
                    emitter.send(
                        SseEmitter.event()
                            .name("delta")
                            .data(mapOf("content" to delta), MediaType.APPLICATION_JSON),
                    )
                }

                emitter.send(
                    SseEmitter.event()
                        .name("complete")
                        .data(
                            ChatResponse.of(
                                id = chat.id,
                                threadId = chat.threadId,
                                question = chat.question,
                                answer = chat.answer,
                                model = chat.model,
                                createdAt = chat.createdAt,
                            ),
                            MediaType.APPLICATION_JSON,
                        ),
                )
                emitter.complete()
            } catch (e: BusinessException) {
                emitter.send(
                    SseEmitter.event()
                        .name("error")
                        .data(ErrorResponse.of(e.errorCode, e.message ?: e.errorCode.message), MediaType.APPLICATION_JSON),
                )
                emitter.complete()
            } catch (_: Exception) {
                emitter.send(
                    SseEmitter.event()
                        .name("error")
                        .data(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR), MediaType.APPLICATION_JSON),
                )
                emitter.complete()
            }
        }

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .cacheControl(CacheControl.noStore())
            .body(emitter as Any)
    }

    private fun toThreadWithChatsResponse(view: ThreadWithChatsView): ThreadWithChatsResponse =
        ThreadWithChatsResponse(
            threadId = view.threadId,
            userId = view.userId,
            createdAt = view.createdAt,
            chats = view.chats.map(::toChatResponse),
        )

    private fun toChatResponse(summary: ChatSummary): ChatResponse =
        ChatResponse.of(
            id = summary.id,
            threadId = summary.threadId,
            question = summary.question,
            answer = summary.answer,
            model = summary.model,
            createdAt = summary.createdAt,
        )

    private fun parseDirection(direction: String): Sort.Direction =
        try {
            Sort.Direction.fromString(direction)
        } catch (_: IllegalArgumentException) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "direction must be ASC or DESC")
        }
}
