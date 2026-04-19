package com.sionic.api.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.sionic.domain.auth.repository.AuthEventRepository
import com.sionic.domain.user.repository.UserRepository
import com.sionic.support.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@AutoConfigureMockMvc
class AuthIntegrationTest : IntegrationTest() {

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var objectMapper: ObjectMapper

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var authEventRepository: AuthEventRepository

    init {
        afterTest {
            authEventRepository.deleteAll()
            userRepository.deleteAll()
        }

        given("회원가입") {
            `when`("이메일, 패스워드, 이름을 전달하면") {
                val result = mockMvc.post("/api/v1/auth/register") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"new@example.com","password":"Test1234!","name":"홍길동"}"""
                }.andReturn()

                then("201과 JWT 토큰이 반환된다") {
                    result.response.status shouldBe 201
                    val token = objectMapper.readTree(result.response.contentAsString)
                        .path("data").path("token").asText()
                    token.shouldNotBeBlank()
                }
            }

            `when`("이미 가입된 이메일로 요청하면") {
                mockMvc.post("/api/v1/auth/register") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"dup@example.com","password":"Test1234!","name":"중복"}"""
                }
                val result = mockMvc.post("/api/v1/auth/register") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"dup@example.com","password":"Test1234!","name":"중복2"}"""
                }.andReturn()

                then("409 Conflict와 U002 코드가 반환된다") {
                    result.response.status shouldBe 409
                    objectMapper.readTree(result.response.contentAsString)
                        .path("code").asText() shouldBe "U002"
                }
            }

            `when`("필수 필드가 누락되면") {
                val result = mockMvc.post("/api/v1/auth/register") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"missing@example.com"}"""
                }.andReturn()

                then("400 Bad Request가 반환된다") {
                    result.response.status shouldBe 400
                }
            }
        }

        given("로그인") {
            beforeTest {
                mockMvc.post("/api/v1/auth/register") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"login@example.com","password":"Test1234!","name":"로그인유저"}"""
                }
            }

            `when`("올바른 이메일과 패스워드를 전달하면") {
                val result = mockMvc.post("/api/v1/auth/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"login@example.com","password":"Test1234!"}"""
                }.andReturn()

                then("200과 JWT 토큰이 반환된다") {
                    result.response.status shouldBe 200
                    val token = objectMapper.readTree(result.response.contentAsString)
                        .path("data").path("token").asText()
                    token.shouldNotBeBlank()
                }
            }

            `when`("잘못된 패스워드로 요청하면") {
                val result = mockMvc.post("/api/v1/auth/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"login@example.com","password":"Wrong1234!"}"""
                }.andReturn()

                then("401과 U003 코드가 반환된다") {
                    result.response.status shouldBe 401
                    objectMapper.readTree(result.response.contentAsString)
                        .path("code").asText() shouldBe "U003"
                }
            }
        }

        given("JWT 인증") {
            lateinit var token: String

            beforeTest {
                mockMvc.post("/api/v1/auth/register") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"auth@example.com","password":"Test1234!","name":"인증유저"}"""
                }
                val loginResult = mockMvc.post("/api/v1/auth/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"auth@example.com","password":"Test1234!"}"""
                }.andReturn()
                token = objectMapper.readTree(loginResult.response.contentAsString)
                    .path("data").path("token").asText()
                token.shouldNotBeBlank()
            }

            `when`("토큰 없이 보호된 엔드포인트에 접근하면") {
                val result = mockMvc.get("/api/v1/chats").andReturn()

                then("401과 C003 코드가 반환된다") {
                    result.response.status shouldBe 401
                    objectMapper.readTree(result.response.contentAsString)
                        .path("code").asText() shouldBe "C003"
                }
            }

            `when`("유효한 토큰으로 접근하면") {
                val result = mockMvc.get("/api/v1/chats") {
                    header("Authorization", "Bearer $token")
                }.andReturn()

                then("200이 반환된다") {
                    result.response.status shouldBe 200
                }
            }

            `when`("변조된 토큰으로 접근하면") {
                val result = mockMvc.get("/api/v1/chats") {
                    header("Authorization", "Bearer invalid.token.here")
                }.andReturn()

                then("401과 C003 코드가 반환된다") {
                    result.response.status shouldBe 401
                    objectMapper.readTree(result.response.contentAsString)
                        .path("code").asText() shouldBe "C003"
                }
            }
        }
    }
}
