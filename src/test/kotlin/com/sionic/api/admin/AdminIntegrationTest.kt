package com.sionic.api.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.sionic.domain.auth.entity.AuthEvent
import com.sionic.domain.auth.enums.AuthEventType
import com.sionic.domain.auth.repository.AuthEventRepository
import com.sionic.domain.chat.entity.Chat
import com.sionic.domain.chat.repository.ChatRepository
import com.sionic.domain.thread.entity.Thread
import com.sionic.domain.thread.repository.ThreadRepository
import com.sionic.domain.user.entity.User
import com.sionic.domain.user.repository.UserRepository
import com.sionic.global.auth.JwtProvider
import com.sionic.support.IntegrationTest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
class AdminIntegrationTest : IntegrationTest() {

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var mockMvc: MockMvc

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var objectMapper: ObjectMapper

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var userRepository: UserRepository

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var authEventRepository: AuthEventRepository

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var threadRepository: ThreadRepository

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var chatRepository: ChatRepository

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var jwtProvider: JwtProvider

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var passwordEncoder: PasswordEncoder

    init {
        afterTest {
            chatRepository.deleteAll()
            threadRepository.deleteAll()
            authEventRepository.deleteAll()
            userRepository.deleteAll()
        }

        given("GET /api/v1/admin/activity") {
            `when`("admin이 최근 24시간 activity를 조회하면") {
                val admin = createAdmin("admin-activity@test.com")
                val member = createMember("member-activity@test.com")

                authEventRepository.save(AuthEvent.create(UUID.randomUUID(), member.id, AuthEventType.REGISTER))
                authEventRepository.save(AuthEvent.create(UUID.randomUUID(), member.id, AuthEventType.LOGIN))

                val thread = threadRepository.save(Thread.create(UUID.randomUUID(), member.id, Instant.now()))
                chatRepository.save(
                    Chat.create(
                        id = UUID.randomUUID(),
                        threadId = thread.id,
                        question = "질문",
                        answer = "답변",
                        model = "test-model",
                    )
                )

                val result = mockMvc.get("/api/v1/admin/activity") {
                    header("Authorization", bearerToken(admin))
                }.andReturn()

                then("24시간 집계값 3개를 반환한다") {
                    result.response.status shouldBe 200
                    val data = objectMapper.readTree(result.response.contentAsString).path("data")
                    data.path("signupCount").asLong() shouldBe 1L
                    data.path("loginCount").asLong() shouldBe 1L
                    data.path("chatCount").asLong() shouldBe 1L
                }
            }

            `when`("member가 접근하면") {
                val member = createMember("member-no-admin@test.com")

                val result = mockMvc.get("/api/v1/admin/activity") {
                    header("Authorization", bearerToken(member))
                }.andReturn()

                then("403이 반환된다") {
                    result.response.status shouldBe 403
                    objectMapper.readTree(result.response.contentAsString).path("code").asText() shouldBe "C004"
                }
            }
        }
    }

    private fun createMember(email: String): User =
        userRepository.save(
            User.create(
                id = UUID.randomUUID(),
                email = email,
                encodedPassword = passwordEncoder.encode("Test1234!"),
                name = "멤버",
            )
        )

    private fun createAdmin(email: String): User =
        userRepository.save(
            User.createAdmin(
                id = UUID.randomUUID(),
                email = email,
                encodedPassword = passwordEncoder.encode("Admin1234!"),
                name = "관리자",
            )
        )

    private fun bearerToken(user: User): String =
        "Bearer ${jwtProvider.createToken(user.id, user.role)}"
}
