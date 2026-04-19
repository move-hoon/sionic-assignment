package com.sionic.global.config

import com.sionic.domain.user.entity.User
import com.sionic.domain.user.repository.UserRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AdminSeedRunner(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${admin.email}")
    private val adminEmail: String,

    @Value("\${admin.password}")
    private val adminPassword: String,

    @Value("\${admin.name:Admin}")
    private val adminName: String
) : ApplicationRunner {

    private val log = KotlinLogging.logger {}

    override fun run(args: ApplicationArguments) {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info { "Admin already exists, skipping seed" }
            return
        }
        val admin = User.createAdmin(
            id = UUID.randomUUID(),
            email = adminEmail,
            encodedPassword = passwordEncoder.encode(adminPassword),
            name = adminName,
        )
        userRepository.save(admin)
        log.info { "Admin seed completed" }
    }
}
