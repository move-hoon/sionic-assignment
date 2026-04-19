package com.sionic.global.auth

import com.sionic.domain.user.enums.Role
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtProvider(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.expiration-ms:86400000}") private val expirationMs: Long,
) {
    private val log = KotlinLogging.logger {}
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun createToken(userId: UUID, role: Role): String =
        Jwts.builder()
            .subject(userId.toString())
            .claim("role", role.name)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(key)
            .compact()

    fun parse(token: String): AuthUser? = try {
        val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        AuthUser(
            id = UUID.fromString(claims.subject),
            role = Role.valueOf(claims.get("role", String::class.java)),
        )
    } catch (e: JwtException) {
        log.warn { "Invalid JWT: ${e.message}" }
        null
    } catch (e: Exception) {
        log.warn { "JWT parse error: ${e.message}" }
        null
    }
}
