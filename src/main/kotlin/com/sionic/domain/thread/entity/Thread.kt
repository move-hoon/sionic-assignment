package com.sionic.domain.thread.entity

import com.sionic.domain.BaseTimeEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import java.sql.Types
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "threads")
open class Thread private constructor(
    @Id
    @JdbcTypeCode(Types.VARCHAR)
    @Column(length = 36, updatable = false, nullable = false)
    open val id: UUID,

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "user_id", length = 36, updatable = false, nullable = false)
    open val userId: UUID,

    lastQuestionAt: Instant,
) : BaseTimeEntity() {

    @Column(name = "last_question_at", nullable = false)
    open var lastQuestionAt: Instant = lastQuestionAt
        protected set

    open fun updateLastQuestionAt(time: Instant) {
        lastQuestionAt = time
    }

    companion object {
        fun create(id: UUID, userId: UUID, now: Instant): Thread =
            Thread(id = id, userId = userId, lastQuestionAt = now)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Thread) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
