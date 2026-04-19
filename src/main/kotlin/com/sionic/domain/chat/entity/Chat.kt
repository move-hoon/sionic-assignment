package com.sionic.domain.chat.entity

import com.sionic.domain.BaseTimeEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import java.sql.Types
import java.util.UUID

@Entity
@Table(name = "chats")
open class Chat private constructor(
    @Id
    @JdbcTypeCode(Types.VARCHAR)
    @Column(length = 36, updatable = false, nullable = false)
    open val id: UUID,

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "thread_id", length = 36, updatable = false, nullable = false)
    open val threadId: UUID,

    question: String,
    answer: String,
    model: String?,
) : BaseTimeEntity() {

    @Column(nullable = false, columnDefinition = "TEXT")
    open val question: String = question

    @Column(nullable = false, columnDefinition = "TEXT")
    open var answer: String = answer
        protected set

    @Column(length = 100)
    open val model: String? = model

    companion object {
        fun create(id: UUID, threadId: UUID, question: String, answer: String, model: String? = null): Chat =
            Chat(id = id, threadId = threadId, question = question, answer = answer, model = model)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Chat) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
