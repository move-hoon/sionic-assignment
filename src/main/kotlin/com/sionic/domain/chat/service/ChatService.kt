package com.sionic.domain.chat.service

import com.sionic.domain.chat.entity.Chat
import com.sionic.domain.chat.repository.ChatRepository
import com.sionic.domain.feedback.repository.FeedbackRepository
import com.sionic.domain.thread.entity.Thread
import com.sionic.domain.thread.repository.ThreadRepository
import com.sionic.global.client.AiClient
import com.sionic.global.client.dto.AiRequest
import com.sionic.global.client.dto.AiResponse
import com.sionic.global.exception.BusinessException
import com.sionic.global.exception.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class ChatService(
    private val threadRepository: ThreadRepository,
    private val chatRepository: ChatRepository,
    private val feedbackRepository: FeedbackRepository,
    private val aiClient: AiClient,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val maxPageSize = 100
    private val threadTimeout: Duration = Duration.ofMinutes(30)
    private val assistantInstructions = "You are a helpful assistant for a VIP onboarding demo. Answer clearly and concisely."

    fun createChat(userId: UUID, question: String, model: String? = null): Chat {
        val questionArrivedAt = Instant.now(clock)
        val threadId = resolveThreadId(userId, questionArrivedAt)
        val aiRequest = buildAiRequest(threadId, question, model, stream = false)
        val aiResponse = aiClient.complete(aiRequest)

        return persistChat(threadId, question, aiResponse)
    }

    fun streamChat(userId: UUID, question: String, model: String? = null, onDelta: (String) -> Unit): Chat {
        val questionArrivedAt = Instant.now(clock)
        val threadId = resolveThreadId(userId, questionArrivedAt)
        val aiRequest = buildAiRequest(threadId, question, model, stream = true)
        val aiResponse = aiClient.stream(aiRequest, onDelta)

        return persistChat(threadId, question, aiResponse)
    }

    @Transactional(readOnly = true)
    fun listChats(userId: UUID, isAdmin: Boolean, page: Int, size: Int, direction: Sort.Direction): Page<ThreadWithChatsView> {
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, maxPageSize),
            Sort.by(
                Sort.Order(direction, "createdAt"),
                Sort.Order(direction, "id"),
            ),
        )

        val threadPage = if (isAdmin) {
            threadRepository.findAllBy(pageable)
        } else {
            threadRepository.findAllByUserId(userId, pageable)
        }

        val threadIds = threadPage.content.map { it.id }
        val chatsByThreadId = if (threadIds.isEmpty()) {
            emptyMap()
        } else {
            chatRepository.findAllByThreadIdIn(threadIds)
                .sortedWith(compareBy({ it.createdAt }, { it.id.toString() }))
                .groupBy { it.threadId }
        }

        return threadPage.map { thread ->
            ThreadWithChatsView(
                threadId = thread.id,
                userId = thread.userId,
                createdAt = thread.createdAt,
                chats = chatsByThreadId[thread.id].orEmpty().map { chat ->
                    ChatSummary(
                        id = chat.id,
                        threadId = chat.threadId,
                        question = chat.question,
                        answer = chat.answer,
                        model = chat.model,
                        createdAt = chat.createdAt,
                    )
                },
            )
        }
    }

    @Transactional
    fun deleteThread(threadId: UUID, userId: UUID) {
        val thread = threadRepository.findByIdOrNull(threadId)
            ?: throw BusinessException(ErrorCode.THREAD_NOT_FOUND)

        if (thread.userId != userId) {
            throw BusinessException(ErrorCode.FORBIDDEN)
        }

        val chats = chatRepository.findAllByThreadIdOrderByCreatedAtAsc(threadId)
        if (chats.isNotEmpty()) {
            feedbackRepository.deleteAllByChatIdIn(chats.map { it.id })
        }
        chatRepository.deleteAllByThreadId(threadId)
        threadRepository.delete(thread)
    }

    private fun buildAiRequest(threadId: UUID, question: String, model: String?, stream: Boolean): AiRequest {
        val priorChats = chatRepository.findAllByThreadIdOrderByCreatedAtAsc(threadId)
        return AiRequest(
            messages = buildConversationMessages(priorChats, question),
            model = model,
            stream = stream,
        )
    }

    private fun resolveThreadId(userId: UUID, questionArrivedAt: Instant): UUID =
        transactionTemplate.execute {
            val latestThread = threadRepository.findFirstByUserIdOrderByLastQuestionAtDesc(userId)
            val thread = if (latestThread != null &&
                questionArrivedAt.isBefore(latestThread.lastQuestionAt.plus(threadTimeout))
            ) {
                latestThread.apply { updateLastQuestionAt(questionArrivedAt) }
            } else {
                Thread.create(id = UUID.randomUUID(), userId = userId, now = questionArrivedAt)
            }

            threadRepository.save(thread).id
        } ?: error("Failed to resolve thread")

    private fun persistChat(threadId: UUID, question: String, aiResponse: AiResponse): Chat =
        transactionTemplate.execute {
            chatRepository.save(
                Chat.create(
                    id = UUID.randomUUID(),
                    threadId = threadId,
                    question = question,
                    answer = aiResponse.content,
                    model = aiResponse.model,
                )
            )
        } ?: error("Failed to persist chat")

    private fun buildConversationMessages(chats: List<Chat>, question: String): List<AiRequest.AiMessage> = buildList {
        add(AiRequest.AiMessage(role = "developer", content = assistantInstructions))
        chats.forEach { chat ->
            add(AiRequest.AiMessage(role = "user", content = chat.question))
            add(AiRequest.AiMessage(role = "assistant", content = chat.answer))
        }
        add(AiRequest.AiMessage(role = "user", content = question))
    }
}
