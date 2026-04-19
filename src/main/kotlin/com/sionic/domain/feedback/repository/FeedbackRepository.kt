package com.sionic.domain.feedback.repository

import com.sionic.domain.feedback.entity.Feedback
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FeedbackRepository : JpaRepository<Feedback, UUID> {
    fun existsByUserIdAndChatId(userId: UUID, chatId: UUID): Boolean
    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<Feedback>
    fun findAllByUserIdAndIsPositive(userId: UUID, isPositive: Boolean, pageable: Pageable): Page<Feedback>
    fun findAllBy(pageable: Pageable): Page<Feedback>
    fun findAllByIsPositive(isPositive: Boolean, pageable: Pageable): Page<Feedback>
    fun deleteAllByChatIdIn(chatIds: List<UUID>): Long
}
