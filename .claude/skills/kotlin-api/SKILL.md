---
name: kotlin-api
description: Use this skill when building REST API endpoints, controllers, DTOs, or exception handlers in Kotlin + Spring Boot. Applies whenever creating @RestController classes, request/response DTOs, validation logic, global error handling, or API versioning. Covers the project's BusinessException + ErrorResponse contract, nullable-field DTO validation pattern, and security-first API design.
---

# Kotlin REST API Skill

## Package Structure
```
com.sionic
├── domain/         # Entity, Repository, Service per bounded context
├── api/            # Controller, Request/Response DTOs
├── common/         # Exception, ProblemDetail handlers
└── config/         # Spring configuration
```

## Controller Rules
- `@RestController` + `@RequestMapping("/api/v1/...")` (URI versioning)
- REST conventions:
  - **Plural nouns** (`/orders`, NOT `/order`)
  - URI versioning (`/api/v1/`)
- `@Valid` MUST be applied to request bodies
- Return `ResponseEntity<T>`
- Pagination: accept `Pageable`, return `Page<T>`

### Status Codes
| Code | Use case |
|------|----------|
| 200 OK | Success (read, update) |
| 201 Created | Resource created |
| 400 Bad Request | Validation failure |
| 409 Conflict | Business rule violation |

### Example
```kotlin
@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {
    @PostMapping
    fun create(@RequestBody @Valid request: CreateUserRequest): ResponseEntity<UserResponse> {
        val user = userService.create(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user))
    }

    @GetMapping
    fun list(pageable: Pageable): ResponseEntity<Page<UserResponse>> =
        ResponseEntity.ok(userService.list(pageable).map(UserResponse::from))
}
```

## DTO Rules

### Request DTO — "Golden Standard" (Nullable + Validation)
New request DTOs MUST use the nullable-field + validation pattern:

```kotlin
data class CreateUserRequest(
    @field:NotBlank(message = "email is required")
    @field:Email
    val email: String?,

    @field:NotBlank
    @field:Size(min = 2, max = 50)
    val name: String?,
) {
    fun validatedEmail(): String = email!!
    fun validatedName(): String = name!!
}
```

**Why:**
- `@field:` prefix is **MANDATORY** for `data class` (Kotlin property targeting)
- Nullable types avoid NPE during deserialization when field is missing
- Helper methods (`validatedName() = name!!`) are the **ONLY place `!!` is allowed**
- Validation errors return 400 with field-level detail

### Response DTO
- Non-null `data class` exclusively
- Use `companion object { fun from(entity): ResponseDto }` factory pattern
- 일반 클래스(비 data class)로 정적 팩토리만 노출할 경우 `private constructor`로 생성 경로 강제
- Hide sensitive fields with `@JsonIgnore`

```kotlin
data class UserResponse(
    val id: Long,
    val email: String,
    val name: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User) = UserResponse(
            id = user.id!!,
            email = user.email,
            name = user.name,
            createdAt = user.createdAt,
        )
    }
}
```

## Validation
- `@field:NotBlank`, `@field:NotNull`, `@field:Size`, `@field:Email`, `@field:Valid`
- `@field:` prefix **MANDATORY** for data classes
- Validate at controller boundary, never trust downstream

## Exception Handling
이 프로젝트는 기존 `BusinessException + ErrorResponse` 계약을 사용합니다. ProblemDetail 미사용.

**에러 응답 구조**: `ErrorResponse(code: String, message: String)`

**에러 흐름**:
```
throw BusinessException(ErrorCode.USER_NOT_FOUND)
  → GlobalExceptionHandler.handleBusinessException()
  → ResponseEntity<ErrorResponse>(status=404, body=ErrorResponse("U001", "사용자를 찾을 수 없습니다."))
```

**도메인 ErrorCode 추가 방법** (`ErrorCode.kt` enum에 추가):
```kotlin
// User
USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U002", "이미 사용 중인 이메일입니다."),
INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "U003", "이메일 또는 비밀번호가 올바르지 않습니다."),

// Thread / Chat
THREAD_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "스레드를 찾을 수 없습니다."),
CHAT_NOT_FOUND(HttpStatus.NOT_FOUND, "C010", "대화를 찾을 수 없습니다."),

// Feedback
FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "F001", "피드백을 찾을 수 없습니다."),
FEEDBACK_DUPLICATE(HttpStatus.CONFLICT, "F002", "이미 해당 대화에 피드백을 작성했습니다."),
```

**성공 응답**: `ApiResponse.ok(data)` 또는 `ApiResponse.ok()` 사용

## Security (CRITICAL)

### Sensitive Data
- `@JsonIgnore` on password, token, apiKey fields in entities
- **NEVER** log passwords, tokens, API keys, PII
- API keys via environment variables, never hardcoded

### SQL Injection
- JPA/QueryDSL parameter binding ONLY
- String templates in `@Query` are FORBIDDEN

### Method-level Authorization
- `@PreAuthorize("hasRole('ADMIN')")` for sensitive endpoints

## Logging
```kotlin
private val logger = KotlinLogging.logger {}

logger.info { "User created: id=${user.id}" }    // state changes
logger.warn { "Duplicate email attempt: $email" } // business anomalies
logger.error(e) { "Failed to send email" }        // actionable failures
```
- `System.out.println` is FORBIDDEN

## Observability
- MDC correlation ID propagation in coroutines: `withContext(MDCContext())` when switching dispatchers
- `@Timed` on critical paths for metrics

## Async & Coroutines
- Prefer `suspend fun` over `@Async` or `CompletableFuture`
- Blocking I/O: `withContext(Dispatchers.IO) { ... }`
- Streaming/SSE: `kotlinx.coroutines.flow.Flow` (prefer over Reactor `Flux`)
- Backpressure: `.buffer()` operator

## Forbidden Patterns
- Controllers accessing repositories directly (go through service)
- Entities returned from controllers (use Response DTO)
- `!!` outside DTO validation helpers
- Error responses that bypass the project's `BusinessException + ErrorResponse` contract
- Hardcoded secrets
