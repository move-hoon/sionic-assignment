package com.sionic.global.exception

import jakarta.validation.ConstraintViolationException
import jakarta.validation.constraints.NotBlank
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import jakarta.validation.Valid

data class SampleRequest(@field:NotBlank val name: String = "")

@RestController
class FakeController {

    @GetMapping("/test/not-found")
    fun notFound(): Unit = throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

    @GetMapping("/test/unauthorized")
    fun unauthorized(): Unit = throw BusinessException(ErrorCode.UNAUTHORIZED)

    @GetMapping("/test/forbidden")
    fun forbidden(): Unit = throw BusinessException(ErrorCode.FORBIDDEN)

    @GetMapping("/test/server-error")
    fun serverError(): Unit = throw RuntimeException("예상치 못한 에러")

    @GetMapping("/test/constraint-violation")
    fun constraintViolation(): Unit = throw ConstraintViolationException("name: 공백일 수 없습니다", emptySet())

    @PostMapping("/test/valid")
    fun valid(@Valid @RequestBody request: SampleRequest): Unit = Unit
}

@WebMvcTest(controllers = [FakeController::class])
class GlobalExceptionHandlerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `RESOURCE_NOT_FOUND는 404와 C002를 반환한다`() {
        mockMvc.get("/test/not-found") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("C002") }
            jsonPath("$.message") { exists() }
        }
    }

    @Test
    fun `UNAUTHORIZED는 401과 C003을 반환한다`() {
        mockMvc.get("/test/unauthorized") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("C003") }
        }
    }

    @Test
    fun `FORBIDDEN은 403과 C004를 반환한다`() {
        mockMvc.get("/test/forbidden") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("C004") }
        }
    }

    @Test
    fun `처리되지 않은 예외는 500과 C005를 반환한다`() {
        mockMvc.get("/test/server-error") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("C005") }
        }
    }

    @Test
    fun `ConstraintViolationException은 400과 C001을 반환한다`() {
        mockMvc.get("/test/constraint-violation") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
        }
    }

    @Test
    fun `@Valid 검증 실패는 400과 필드 에러 메시지를 반환한다`() {
        mockMvc.post("/test/valid") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": ""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
            jsonPath("$.message") { value(containsString("name")) }
        }
    }

    @Test
    fun `잘못된 JSON 형식은 400과 C001을 반환한다`() {
        mockMvc.post("/test/valid") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"invalid json"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("C001") }
        }
    }

    @Test
    fun `존재하지 않는 URL은 404와 C002를 반환한다`() {
        mockMvc.get("/test/does-not-exist") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("C002") }
        }
    }

    @Test
    fun `지원하지 않는 HTTP 메서드는 405와 C009를 반환한다`() {
        mockMvc.post("/test/not-found") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isMethodNotAllowed() }
            jsonPath("$.code") { value("C009") }
        }
    }
}
