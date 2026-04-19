package com.sionic.api.chat.dto

import java.time.Instant
import java.util.UUID
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "thread 단위로 그룹화된 대화 목록 응답")
data class ThreadWithChatsResponse(
    @field:Schema(description = "thread ID")
    val threadId: UUID,
    @field:Schema(description = "thread 소유 사용자 ID")
    val userId: UUID,
    @field:Schema(description = "thread 생성 시각(UTC)")
    val createdAt: Instant,
    @field:Schema(description = "thread에 포함된 대화 목록")
    val chats: List<ChatResponse>,
)
