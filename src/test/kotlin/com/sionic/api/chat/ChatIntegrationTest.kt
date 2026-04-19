package com.sionic.api.chat

import com.fasterxml.jackson.databind.ObjectMapper
import com.sionic.domain.auth.repository.AuthEventRepository
import com.sionic.domain.chat.repository.ChatRepository
import com.sionic.domain.feedback.entity.Feedback
import com.sionic.domain.feedback.repository.FeedbackRepository
import com.sionic.domain.thread.repository.ThreadRepository
import com.sionic.domain.user.entity.User
import com.sionic.domain.user.repository.UserRepository
import com.sionic.global.auth.JwtProvider
import com.sionic.support.FakeAiClientRecorder
import com.sionic.support.IntegrationTest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Clock
import java.time.Duration
import java.util.UUID

@AutoConfigureMockMvc
class ChatIntegrationTest : IntegrationTest() {

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

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var clock: Clock

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var fakeAiClientRecorder: FakeAiClientRecorder

    init {
        afterTest {
            fakeAiClientRecorder.clear()
            feedbackRepository.deleteAll()
            chatRepository.deleteAll()
            threadRepository.deleteAll()
            authEventRepository.deleteAll()
            userRepository.deleteAll()
        }

        given("POST /api/v1/chats") {
            `when`("첫 질문을 보내면") {
                val user = createMember("chat-create@test.com")
                val token = bearerToken(user)

                val result = mockMvc.post("/api/v1/chats") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"안녕하세요"}"""
                }.andReturn()

                then("201과 저장된 chat/thread 정보가 반환된다") {
                    result.response.status shouldBe 201
                    val body = objectMapper.readTree(result.response.contentAsString)
                    body.path("success").asBoolean() shouldBe true
                    body.path("data").path("question").asText() shouldBe "안녕하세요"
                    body.path("data").path("answer").asText().shouldContain("stubbed-answer")
                    threadRepository.count() shouldBe 1
                    chatRepository.count() shouldBe 1
                }
            }

            `when`("모델을 지정하면 OpenAI 요청과 저장값에 반영된다") {
                val user = createMember("chat-model@test.com")
                val token = bearerToken(user)

                val result = mockMvc.post("/api/v1/chats") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"모델 테스트","model":"gpt-5.4"}"""
                }.andReturn()

                then("응답과 저장된 chat, AI 요청 모두 같은 모델을 사용한다") {
                    result.response.status shouldBe 201
                    val body = objectMapper.readTree(result.response.contentAsString)
                    body.path("data").path("model").asText() shouldBe "gpt-5.4"
                    chatRepository.findAll().single().model shouldBe "gpt-5.4"
                    fakeAiClientRecorder.requests.last().model shouldBe "gpt-5.4"
                }
            }

            `when`("30분 이내에 다시 질문하면") {
                val user = createMember("chat-reuse@test.com")
                val token = bearerToken(user)

                val first = mockMvc.post("/api/v1/chats") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"첫 번째 질문"}"""
                }.andReturn()
                val firstThreadId = objectMapper.readTree(first.response.contentAsString)
                    .path("data").path("threadId").asText()

                val second = mockMvc.post("/api/v1/chats") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"두 번째 질문"}"""
                }.andReturn()
                val secondThreadId = objectMapper.readTree(second.response.contentAsString)
                    .path("data").path("threadId").asText()

                then("기존 thread를 재사용한다") {
                    second.response.status shouldBe 201
                    secondThreadId shouldBe firstThreadId
                    threadRepository.count() shouldBe 1
                    chatRepository.count() shouldBe 2
                }
            }

            `when`("후속 질문은 OpenAI 메시지 순서대로 이전 대화를 포함한다") {
                val user = createMember("chat-history@test.com")
                val token = bearerToken(user)

                mockMvc.post("/api/v1/chats") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"첫 번째 질문"}"""
                }.andReturn()

                fakeAiClientRecorder.clear()

                mockMvc.post("/api/v1/chats") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"두 번째 질문"}"""
                }.andReturn()

                then("developer, user, assistant, user 순으로 전달된다") {
                    val messages = fakeAiClientRecorder.requests.single().messages
                    messages.map { it.role } shouldBe listOf("developer", "user", "assistant", "user")
                    messages[1].content shouldBe "첫 번째 질문"
                    messages[2].content shouldBe "stubbed-answer:첫 번째 질문"
                    messages[3].content shouldBe "두 번째 질문"
                }
            }

            `when`("30분이 지난 뒤 다시 질문하면") {
                val user = createMember("chat-new-thread@test.com")
                val token = bearerToken(user)

                val first = mockMvc.post("/api/v1/chats") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"첫 번째 질문"}"""
                }.andReturn()
                val firstThreadId = objectMapper.readTree(first.response.contentAsString)
                    .path("data").path("threadId").asText()

                val latestThread = threadRepository.findAll().single()
                latestThread.updateLastQuestionAt(clock.instant().minus(Duration.ofMinutes(31)))
                threadRepository.save(latestThread)

                val second = mockMvc.post("/api/v1/chats") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"시간 지난 질문"}"""
                }.andReturn()
                val secondThreadId = objectMapper.readTree(second.response.contentAsString)
                    .path("data").path("threadId").asText()

                then("새 thread가 생성된다") {
                    second.response.status shouldBe 201
                    (secondThreadId == firstThreadId) shouldBe false
                    threadRepository.count() shouldBe 2
                }
            }

            `when`("정확히 30분 뒤 다시 질문하면") {
                val user = createMember("chat-exact-boundary@test.com")
                val token = bearerToken(user)

                val first = mockMvc.post("/api/v1/chats") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"경계값 첫 질문"}"""
                }.andReturn()
                val firstThreadId = objectMapper.readTree(first.response.contentAsString)
                    .path("data").path("threadId").asText()

                val latestThread = threadRepository.findAll().single()
                latestThread.updateLastQuestionAt(clock.instant().minus(Duration.ofMinutes(30)))
                threadRepository.save(latestThread)

                val second = mockMvc.post("/api/v1/chats") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"경계값 두 번째 질문"}"""
                }.andReturn()
                val secondThreadId = objectMapper.readTree(second.response.contentAsString)
                    .path("data").path("threadId").asText()

                then("새 thread가 생성된다") {
                    second.response.status shouldBe 201
                    (secondThreadId == firstThreadId) shouldBe false
                    threadRepository.count() shouldBe 2
                    chatRepository.count() shouldBe 2
                }
            }

            `when`("스트리밍 요청을 보내면 SSE 응답과 저장된 chat이 생성된다") {
                val user = createMember("chat-stream@test.com")
                val token = bearerToken(user)

                val result = mockMvc.post("/api/v1/chats") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"스트리밍 테스트","isStreaming":true,"model":"gpt-5.4"}"""
                }.andReturn()

                then("delta 이벤트와 complete 이벤트가 전달되고 chat이 한 번 저장된다") {
                    Thread.sleep(200)
                    result.response.status shouldBe 200
                    result.response.contentType?.shouldContain(MediaType.TEXT_EVENT_STREAM_VALUE)
                    chatRepository.count() shouldBe 1
                    val storedChat = chatRepository.findAll().single()
                    storedChat.answer shouldBe "stubbed-answer:스트리밍 테스트"
                    storedChat.model shouldBe "gpt-5.4"
                    fakeAiClientRecorder.requests.last().stream shouldBe true
                }
            }
        }

        given("GET /api/v1/chats") {
            `when`("thread 단위 pagination과 정렬을 요청하면") {
                val user = createMember("chat-list@test.com")
                val token = bearerToken(user)

                val first = mockMvc.post("/api/v1/chats") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"첫 thread 질문"}"""
                }.andReturn()
                val oldestThreadId = objectMapper.readTree(first.response.contentAsString)
                    .path("data").path("threadId").asText()

                val latestThread = threadRepository.findAll().single()
                latestThread.updateLastQuestionAt(clock.instant().minus(Duration.ofMinutes(31)))
                threadRepository.save(latestThread)

                val second = mockMvc.post("/api/v1/chats") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"두 번째 thread 질문"}"""
                }.andReturn()
                val newestThreadId = objectMapper.readTree(second.response.contentAsString)
                    .path("data").path("threadId").asText()

                val descResult = mockMvc.get("/api/v1/chats?page=0&size=1&direction=DESC") {
                    header("Authorization", token)
                }.andReturn()

                val ascResult = mockMvc.get("/api/v1/chats?page=0&size=1&direction=ASC") {
                    header("Authorization", token)
                }.andReturn()

                then("thread 기준으로 페이지네이션되고 createdAt 정렬이 적용된다") {
                    descResult.response.status shouldBe 200
                    ascResult.response.status shouldBe 200
                    threadRepository.count() shouldBe 2

                    val descBody = objectMapper.readTree(descResult.response.contentAsString)
                    descBody.path("data").path("content").size() shouldBe 1
                    descBody.path("data").path("content").get(0).path("threadId").asText() shouldBe newestThreadId

                    val ascBody = objectMapper.readTree(ascResult.response.contentAsString)
                    ascBody.path("data").path("content").size() shouldBe 1
                    ascBody.path("data").path("content").get(0).path("threadId").asText() shouldBe oldestThreadId
                }
            }

            `when`("admin이 목록을 조회하면 전체 사용자의 thread를 본다") {
                val admin = createAdmin("chat-admin@test.com")
                val userA = createMember("chat-admin-a@test.com")
                val userB = createMember("chat-admin-b@test.com")

                createChat(userA, "userA question")

                val userAThread = threadRepository.findAll().single { it.userId == userA.id }
                userAThread.updateLastQuestionAt(clock.instant().minus(Duration.ofMinutes(31)))
                threadRepository.save(userAThread)

                createChat(userB, "userB question")

                val result = mockMvc.get("/api/v1/chats?page=0&size=10&direction=DESC") {
                    header("Authorization", bearerToken(admin))
                }.andReturn()

                then("전체 thread가 thread 단위로 반환된다") {
                    result.response.status shouldBe 200
                    val content = objectMapper.readTree(result.response.contentAsString).path("data").path("content")
                    content.size() shouldBe 2
                }
            }

            `when`("direction이 잘못되면") {
                val user = createMember("chat-direction@test.com")
                val token = bearerToken(user)

                val result = mockMvc.get("/api/v1/chats?direction=WRONG") {
                    header("Authorization", token)
                }.andReturn()

                then("400이 반환된다") {
                    result.response.status shouldBe 400
                    objectMapper.readTree(result.response.contentAsString).path("code").asText() shouldBe "C001"
                }
            }
        }

        given("DELETE /api/v1/threads/{threadId}") {
            `when`("본인 thread를 삭제하면") {
                val user = createMember("chat-delete@test.com")
                val token = bearerToken(user)

                val first = mockMvc.post("/api/v1/chats") {
                    header("Authorization", token)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"삭제 대상 질문"}"""
                }.andReturn()
                val threadId = UUID.fromString(
                    objectMapper.readTree(first.response.contentAsString).path("data").path("threadId").asText()
                )
                feedbackRepository.save(
                    Feedback.create(
                        id = UUID.randomUUID(),
                        userId = user.id,
                        chatId = chatRepository.findAllByThreadIdOrderByCreatedAtAsc(threadId).single().id,
                        isPositive = true,
                    )
                )

                val result = mockMvc.delete("/api/v1/threads/$threadId") {
                    header("Authorization", token)
                }.andReturn()

                then("200이 반환되고 관련 chats가 정리된다") {
                    result.response.status shouldBe 200
                    threadRepository.findById(threadId).isPresent shouldBe false
                    chatRepository.findAllByThreadIdOrderByCreatedAtAsc(threadId) shouldHaveSize 0
                    feedbackRepository.count() shouldBe 0
                }
            }

            `when`("다른 사용자의 thread를 삭제하면") {
                val owner = createMember("chat-owner@test.com")
                val attacker = createMember("chat-attacker@test.com")
                val ownerToken = bearerToken(owner)
                val attackerToken = bearerToken(attacker)

                val created = mockMvc.post("/api/v1/chats") {
                    header("Authorization", ownerToken)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"owner thread"}"""
                }.andReturn()
                val threadId = objectMapper.readTree(created.response.contentAsString)
                    .path("data").path("threadId").asText()

                val result = mockMvc.delete("/api/v1/threads/$threadId") {
                    header("Authorization", attackerToken)
                }.andReturn()

                then("403이 반환된다") {
                    result.response.status shouldBe 403
                    objectMapper.readTree(result.response.contentAsString).path("code").asText() shouldBe "C004"
                }
            }

            `when`("없는 thread를 삭제하면") {
                val user = createMember("chat-missing-thread@test.com")
                val token = bearerToken(user)

                val result = mockMvc.delete("/api/v1/threads/${UUID.randomUUID()}") {
                    header("Authorization", token)
                }.andReturn()

                then("404가 반환된다") {
                    result.response.status shouldBe 404
                    objectMapper.readTree(result.response.contentAsString).path("code").asText() shouldBe "T001"
                }
            }

            `when`("admin이 다른 사용자의 thread를 삭제하면") {
                val owner = createMember("chat-owner-admin-block@test.com")
                val admin = createAdmin("chat-admin-block@test.com")

                val created = mockMvc.post("/api/v1/chats") {
                    header("Authorization", bearerToken(owner))
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"question":"owner thread for admin"}"""
                }.andReturn()
                val threadId = objectMapper.readTree(created.response.contentAsString)
                    .path("data").path("threadId").asText()

                val result = mockMvc.delete("/api/v1/threads/$threadId") {
                    header("Authorization", bearerToken(admin))
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
                name = "테스트유저",
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

    private fun createChat(user: User, question: String) {
        mockMvc.post("/api/v1/chats") {
            header("Authorization", bearerToken(user))
            contentType = MediaType.APPLICATION_JSON
            content = """{"question":"$question"}"""
        }.andReturn()
    }

    private fun bearerToken(user: User): String =
        "Bearer ${jwtProvider.createToken(user.id, user.role)}"
}
