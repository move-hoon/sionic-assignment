package com.sionic.global.auth

import com.sionic.domain.user.enums.Role
import java.util.UUID

data class AuthUser(
    val id: UUID,
    val role: Role,
) {
    val isAdmin: Boolean get() = role == Role.ADMIN
}
