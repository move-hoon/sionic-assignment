package com.sionic.domain.thread.repository

import com.sionic.domain.thread.entity.Thread
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.UUID

interface ThreadRepository : JpaRepository<Thread, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstByUserIdOrderByLastQuestionAtDesc(userId: UUID): Thread?

    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<Thread>

    fun findAllBy(pageable: Pageable): Page<Thread>
}
