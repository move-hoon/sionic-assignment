package com.sionic.domain.auth.entity

import com.sionic.domain.auth.enums.AuthEventType
import com.sionic.domain.BaseTimeEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import java.sql.Types
import java.util.UUID

@Entity
@Table(name = "auth_events")
open class AuthEvent private constructor(
    @Id
    @JdbcTypeCode(Types.VARCHAR)
    @Column(length = 36, updatable = false, nullable = false)
    open val id: UUID,

    @JdbcTypeCode(Types.VARCHAR)
    @Column(name = "user_id", length = 36, updatable = false, nullable = false)
    open val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open val type: AuthEventType,
) : BaseTimeEntity() {

    companion object {
        fun create(id: UUID, userId: UUID, type: AuthEventType): AuthEvent =
            AuthEvent(id = id, userId = userId, type = type)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuthEvent) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
