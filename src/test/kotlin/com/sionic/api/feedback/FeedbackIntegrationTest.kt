package com.sionic.api.feedback

import com.fasterxml.jackson.databind.ObjectMapper
import com.sionic.domain.auth.repository.AuthEventRepository
import com.sionic.domain.chat.entity.Chat
import com.sionic.domain.chat.repository.ChatRepository
import com.sionic.domain.feedback.entity.Feedback
import com.sionic.domain.feedback.enums.FeedbackStatus
import com.sionic.domain.feedback.repository.FeedbackRepository
import com.sionic.domain.thread.entity.Thread
import com.sionic.domain.thread.repository.ThreadRepository
import com.sionic.domain.user.entity.User
import com.sionic.domain.user.repository.UserRepository
import com.sionic.global.auth.JwtProvider
import com.sionic.support.IntegrationTest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@AutoConfigureMockMvc
class FeedbackIntegrationTest : IntegrationTest() {

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var mockMvc: MockMvc

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var objectMapper: ObjectMapper

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var userRepository: UserRepository

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var threadRepository: ThreadRepository

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var chatRepository: ChatRepository

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var feedbackRepository: FeedbackRepository

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var authEventRepository: AuthEventRepository

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var jwtProvider: JwtProvider

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var passwordEncoder: PasswordEncoder

    init {
        afterTest {
            feedbackRepository.deleteAll()
            chatRepository.deleteAll()
            threadRepository.deleteAll()
            authEventRepository.deleteAll()
            userRepository.deleteAll()
        }

        given("POST /api/v1/feedbacks") {
            `when`("본인 chat에 feedback을 남기면") {
                val user = createMember("feedback-owner@test.com")
                val token = bearerToken(user)
                val chat = createChatFor(user)

                val result = mockMvc.post("/api/v1/feedbacks") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"chatId":"${chat.id}","isPositive":true}"""
                }.andReturn()

                then("201과 PENDING 상태가 반환된다") {
                    result.response.status shouldBe 201
                    val body = objectMapper.readTree(result.response.contentAsString)
                    body.path("data").path("status").asText() shouldBe "PENDING"
                    feedbackRepository.count() shouldBe 1
                }
            }

            `when`("없는 chat에 feedback을 남기면") {
                val user = createMember("feedback-missing@test.com")
                val token = bearerToken(user)

                val result = mockMvc.post("/api/v1/feedbacks") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"chatId":"${UUID.randomUUID()}","isPositive":true}"""
                }.andReturn()

                then("404가 반환된다") {
                    result.response.status shouldBe 404
                    objectMapper.readTree(result.response.contentAsString).path("code").asText() shouldBe "T002"
                }
            }

            `when`("다른 사용자의 chat에 member가 feedback을 남기면") {
                val owner = createMember("feedback-owner2@test.com")
                val attacker = createMember("feedback-attacker@test.com")
                val token = bearerToken(attacker)
                val chat = createChatFor(owner)

                val result = mockMvc.post("/api/v1/feedbacks") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"chatId":"${chat.id}","isPositive":false}"""
                }.andReturn()

                then("403이 반환된다") {
                    result.response.status shouldBe 403
                    objectMapper.readTree(result.response.contentAsString).path("code").asText() shouldBe "C004"
                }
            }

            `when`("중복 feedback을 생성하면") {
                val user = createMember("feedback-dup@test.com")
                val token = bearerToken(user)
                val chat = createChatFor(user)

                mockMvc.post("/api/v1/feedbacks") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"chatId":"${chat.id}","isPositive":true}"""
                }

                val result = mockMvc.post("/api/v1/feedbacks") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"chatId":"${chat.id}","isPositive":false}"""
                }.andReturn()

                then("409가 반환된다") {
                    result.response.status shouldBe 409
                    objectMapper.readTree(result.response.contentAsString).path("code").asText() shouldBe "F002"
                }
            }
        }

        given("GET /api/v1/feedbacks") {
            `when`("member가 목록을 조회하면 본인 feedback만 본다") {
                val userA = createMember("feedback-list-a@test.com")
                val userB = createMember("feedback-list-b@test.com")
                val token = bearerToken(userA)
                val chatA = createChatFor(userA)
                val chatB = createChatFor(userB)

                feedbackRepository.save(
                    Feedback.create(UUID.randomUUID(), userA.id, chatA.id, true)
                )
                feedbackRepository.save(
                    Feedback.create(UUID.randomUUID(), userB.id, chatB.id, false)
                )

                val result = mockMvc.get("/api/v1/feedbacks?page=0&size=10&direction=DESC") {
                    header("Authorization", token)
                }.andReturn()

                then("본인 feedback만 반환된다") {
                    result.response.status shouldBe 200
                    val content = objectMapper.readTree(result.response.contentAsString).path("data").path("content")
                    content.size() shouldBe 1
                    content.get(0).path("userId").asText() shouldBe userA.id.toString()
                }
            }

            `when`("admin이 positive filter로 조회하면 전체에서 필터링된다") {
                val admin = createAdmin("feedback-admin@test.com")
                val userA = createMember("feedback-admin-a@test.com")
                val userB = createMember("feedback-admin-b@test.com")
                val token = bearerToken(admin)
                val chatA = createChatFor(userA)
                val chatB = createChatFor(userB)

                feedbackRepository.save(Feedback.create(UUID.randomUUID(), userA.id, chatA.id, true))
                feedbackRepository.save(Feedback.create(UUID.randomUUID(), userB.id, chatB.id, false))

                val result = mockMvc.get("/api/v1/feedbacks?page=0&size=10&direction=DESC&isPositive=true") {
                    header("Authorization", token)
                }.andReturn()

                then("필터링된 결과만 반환된다") {
                    result.response.status shouldBe 200
                    val content = objectMapper.readTree(result.response.contentAsString).path("data").path("content")
                    content.size() shouldBe 1
                    content.get(0).path("isPositive").asBoolean() shouldBe true
                }
            }

            `when`("direction이 잘못되면") {
                val user = createMember("feedback-direction@test.com")
                val token = bearerToken(user)

                val result = mockMvc.get("/api/v1/feedbacks?direction=WRONG") {
                    header("Authorization", token)
                }.andReturn()

                then("400이 반환된다") {
                    result.response.status shouldBe 400
                    objectMapper.readTree(result.response.contentAsString).path("code").asText() shouldBe "C001"
                }
            }
        }

        given("PATCH /api/v1/feedbacks/{feedbackId}/status") {
            `when`("admin이 상태를 변경하면") {
                val admin = createAdmin("feedback-status-admin@test.com")
                val token = bearerToken(admin)
                val user = createMember("feedback-status-user@test.com")
                val chat = createChatFor(user)
                val feedback = feedbackRepository.save(
                    Feedback.create(UUID.randomUUID(), user.id, chat.id, true)
                )

                val result = mockMvc.patch("/api/v1/feedbacks/${feedback.id}/status") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"status":"RESOLVED"}"""
                }.andReturn()

                then("200과 변경된 상태가 반환된다") {
                    result.response.status shouldBe 200
                    objectMapper.readTree(result.response.contentAsString)
                        .path("data").path("status").asText() shouldBe "RESOLVED"
                }
            }

            `when`("member가 상태를 변경하려 하면") {
                val user = createMember("feedback-status-member@test.com")
                val token = bearerToken(user)
                val chat = createChatFor(user)
                val feedback = feedbackRepository.save(
                    Feedback.create(UUID.randomUUID(), user.id, chat.id, true)
                )

                val result = mockMvc.patch("/api/v1/feedbacks/${feedback.id}/status") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"status":"RESOLVED"}"""
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

    private fun createChatFor(user: User): Chat {
        val thread = threadRepository.save(
            Thread.create(
                id = UUID.randomUUID(),
                userId = user.id,
                now = Instant.now(),
            )
        )
        return chatRepository.save(
            Chat.create(
                id = UUID.randomUUID(),
                threadId = thread.id,
                question = "질문",
                answer = "답변",
                model = "test-model",
            )
        )
    }

    private fun bearerToken(user: User): String =
        "Bearer ${jwtProvider.createToken(user.id, user.role)}"
}
