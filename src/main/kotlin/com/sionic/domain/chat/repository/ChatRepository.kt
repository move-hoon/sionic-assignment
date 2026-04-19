package com.sionic.domain.chat.repository

import com.sionic.domain.chat.entity.Chat
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface ChatRepository : JpaRepository<Chat, UUID> {
    fun findAllByThreadIdOrderByCreatedAtAsc(threadId: UUID): List<Chat>
    fun findAllByThreadIdIn(threadIds: List<UUID>): List<Chat>
    fun countByCreatedAtAfter(after: Instant): Long
    fun findAllByCreatedAtAfter(after: Instant): List<Chat>
    fun deleteAllByThreadId(threadId: UUID): Long
}
