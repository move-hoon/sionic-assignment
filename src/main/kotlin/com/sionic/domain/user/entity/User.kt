package com.sionic.domain.user.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import com.sionic.domain.user.enums.Role
import com.sionic.domain.BaseTimeEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import java.sql.Types
import java.util.UUID

@Entity
@Table(name = "users")
open class User private constructor(
    @Id
    @JdbcTypeCode(Types.VARCHAR)
    @Column(length = 36, updatable = false, nullable = false)
    open val id: UUID,

    @Column(nullable = false, unique = true, length = 255)
    open val email: String,

    password: String,
    name: String,
    role: Role,
) : BaseTimeEntity() {

    @JsonIgnore
    @Column(nullable = false)
    open var password: String = password
        protected set

    @Column(nullable = false, length = 100)
    open var name: String = name
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var role: Role = role
        protected set

    open fun updatePassword(encodedPassword: String) {
        password = encodedPassword
    }

    companion object {
        fun create(id: UUID, email: String, encodedPassword: String, name: String): User =
            User(id = id, email = email, password = encodedPassword, name = name, role = Role.MEMBER)

        fun createAdmin(id: UUID, email: String, encodedPassword: String, name: String): User =
            User(id = id, email = email, password = encodedPassword, name = name, role = Role.ADMIN)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
