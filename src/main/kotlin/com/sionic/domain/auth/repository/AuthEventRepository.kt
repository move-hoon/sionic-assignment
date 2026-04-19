package com.sionic.domain.auth.repository

import com.sionic.domain.auth.entity.AuthEvent
import com.sionic.domain.auth.enums.AuthEventType
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface AuthEventRepository : JpaRepository<AuthEvent, UUID> {
    fun countByTypeAndCreatedAtAfter(type: AuthEventType, after: Instant): Long
}
