package com.sionic.domain.chat.service

import java.time.Instant
import java.util.UUID

data class ChatSummary(
    val id: UUID,
    val threadId: UUID,
    val question: String,
    val answer: String,
    val model: String?,
    val createdAt: Instant,
)

data class ThreadWithChatsView(
    val threadId: UUID,
    val userId: UUID,
    val createdAt: Instant,
    val chats: List<ChatSummary>,
)
