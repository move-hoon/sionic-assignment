package com.sionic.domain.feedback.entity

import com.sionic.domain.BaseTimeEntity
import com.sionic.domain.feedback.enums.FeedbackStatus
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import java.sql.Types
import java.util.UUID

@Entity
@Table(
    name = "feedbacks",
    uniqueConstraints = [UniqueConstraint(name = "uk_feedback_user_chat", columnNames = ["user_id", "chat_id"])]
)
open class Feedback private constructor(
    @Id
    @JdbcTypeCode(Types.VARCHAR)
    @Column(length = 36, updatable = false, nullable = false)
    open val id: UUID,

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "user_id", length = 36, updatable = false, nullable = false)
    open val userId: UUID,

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "chat_id", length = 36, updatable = false, nullable = false)
    open val chatId: UUID,

    @Column(nullable = false)
    open val isPositive: Boolean,
) : BaseTimeEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var status: FeedbackStatus = FeedbackStatus.PENDING
        protected set

    open fun updateStatus(newStatus: FeedbackStatus) {
        status = newStatus
    }

    companion object {
        fun create(id: UUID, userId: UUID, chatId: UUID, isPositive: Boolean): Feedback =
            Feedback(id = id, userId = userId, chatId = chatId, isPositive = isPositive)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Feedback) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
