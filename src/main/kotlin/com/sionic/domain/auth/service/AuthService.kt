package com.sionic.domain.auth.service

import com.sionic.domain.auth.entity.AuthEvent
import com.sionic.domain.auth.enums.AuthEventType
import com.sionic.domain.auth.repository.AuthEventRepository
import com.sionic.domain.user.entity.User
import com.sionic.domain.user.repository.UserRepository
import com.sionic.global.auth.JwtProvider
import com.sionic.global.exception.BusinessException
import com.sionic.global.exception.ErrorCode
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val authEventRepository: AuthEventRepository,
    private val jwtProvider: JwtProvider,
    private val passwordEncoder: PasswordEncoder,
) {

    fun register(email: String, password: String, name: String): String {
        if (userRepository.existsByEmail(email)) {
            throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        }

        val encodedPassword = passwordEncoder.encode(password)
        val user = User.create(id = UUID.randomUUID(), email = email, encodedPassword = encodedPassword, name = name)
        val savedUser = userRepository.save(user)

        authEventRepository.save(
            AuthEvent.create(id = UUID.randomUUID(), userId = savedUser.id, type = AuthEventType.REGISTER)
        )

        return jwtProvider.createToken(userId = savedUser.id, role = savedUser.role)
    }

    fun login(email: String, password: String): String {
        val user = userRepository.findByEmail(email)
            ?: throw BusinessException(ErrorCode.INVALID_PASSWORD)

        if (!passwordEncoder.matches(password, user.password)) {
            throw BusinessException(ErrorCode.INVALID_PASSWORD)
        }

        authEventRepository.save(
            AuthEvent.create(id = UUID.randomUUID(), userId = user.id, type = AuthEventType.LOGIN)
        )

        return jwtProvider.createToken(userId = user.id, role = user.role)
    }
}
