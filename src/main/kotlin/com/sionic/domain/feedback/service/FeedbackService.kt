package com.sionic.domain.feedback.service

import com.sionic.domain.chat.repository.ChatRepository
import com.sionic.domain.feedback.entity.Feedback
import com.sionic.domain.feedback.enums.FeedbackStatus
import com.sionic.domain.feedback.repository.FeedbackRepository
import com.sionic.domain.thread.repository.ThreadRepository
import com.sionic.global.exception.BusinessException
import com.sionic.global.exception.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FeedbackService(
    private val feedbackRepository: FeedbackRepository,
    private val chatRepository: ChatRepository,
    private val threadRepository: ThreadRepository,
) {
    private val maxPageSize = 100

    @Transactional
    fun createFeedback(userId: UUID, chatId: UUID, isPositive: Boolean, isAdmin: Boolean): Feedback {
        val chat = chatRepository.findByIdOrNull(chatId)
            ?: throw BusinessException(ErrorCode.CHAT_NOT_FOUND)

        if (feedbackRepository.existsByUserIdAndChatId(userId, chatId)) {
            throw BusinessException(ErrorCode.FEEDBACK_DUPLICATE)
        }

        if (!isAdmin) {
            val thread = threadRepository.findByIdOrNull(chat.threadId)
                ?: throw BusinessException(ErrorCode.THREAD_NOT_FOUND)

            if (thread.userId != userId) {
                throw BusinessException(ErrorCode.FORBIDDEN)
            }
        }

        return feedbackRepository.save(
            Feedback.create(id = UUID.randomUUID(), userId = userId, chatId = chatId, isPositive = isPositive)
        )
    }

    @Transactional(readOnly = true)
    fun listFeedbacks(
        userId: UUID,
        isAdmin: Boolean,
        isPositive: Boolean?,
        page: Int,
        size: Int,
        direction: Sort.Direction,
    ): Page<Feedback> {
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, maxPageSize),
            Sort.by(
                Sort.Order(direction, "createdAt"),
                Sort.Order(direction, "id"),
            ),
        )

        return when {
            isAdmin && isPositive != null -> feedbackRepository.findAllByIsPositive(isPositive, pageable)
            isAdmin -> feedbackRepository.findAllBy(pageable)
            isPositive != null -> feedbackRepository.findAllByUserIdAndIsPositive(userId, isPositive, pageable)
            else -> feedbackRepository.findAllByUserId(userId, pageable)
        }
    }

    @Transactional
    fun updateFeedbackStatus(feedbackId: UUID, newStatus: FeedbackStatus): Feedback {
        val feedback = feedbackRepository.findByIdOrNull(feedbackId)
            ?: throw BusinessException(ErrorCode.FEEDBACK_NOT_FOUND)

        feedback.updateStatus(newStatus)
        return feedbackRepository.save(feedback)
    }
}
