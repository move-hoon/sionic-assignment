package com.sionic.domain.admin.service

import com.sionic.domain.auth.enums.AuthEventType
import com.sionic.domain.auth.repository.AuthEventRepository
import com.sionic.domain.chat.repository.ChatRepository
import com.sionic.domain.thread.repository.ThreadRepository
import com.sionic.domain.user.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class AdminService(
    private val authEventRepository: AuthEventRepository,
    private val chatRepository: ChatRepository,
    private val threadRepository: ThreadRepository,
    private val userRepository: UserRepository,
) {
    fun getActivity(): Map<String, Long> {
        val last24Hours = Instant.now().minus(24, ChronoUnit.HOURS)

        val signupCount = authEventRepository.countByTypeAndCreatedAtAfter(AuthEventType.REGISTER, last24Hours)
        val loginCount = authEventRepository.countByTypeAndCreatedAtAfter(AuthEventType.LOGIN, last24Hours)
        val chatCount = chatRepository.countByCreatedAtAfter(last24Hours)

        return mapOf(
            "signupCount" to signupCount,
            "loginCount" to loginCount,
            "chatCount" to chatCount,
        )
    }

    fun generateReport(): String {
        val last24Hours = Instant.now().minus(24, ChronoUnit.HOURS)
        val chats = chatRepository.findAllByCreatedAtAfter(last24Hours)

        val csvHeader = "userId,userEmail,userName,threadId,question,answer,createdAt"
        val csvRows = chats.map { chat ->
            val thread = threadRepository.findByIdOrNull(chat.threadId)
            val user = thread?.let { userRepository.findByIdOrNull(it.userId) }
            val threadId = chat.threadId

            val escapedQuestion = escapeCsv(chat.question)
            val escapedAnswer = escapeCsv(chat.answer)

            "${user?.id},${escapeCsv(user?.email ?: "")},${escapeCsv(user?.name ?: "")},$threadId,$escapedQuestion,$escapedAnswer,${chat.createdAt}"
        }

        return (listOf(csvHeader) + csvRows).joinToString("\n")
    }

    private fun escapeCsv(value: String?): String {
        if (value == null) return ""
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
