# Sionic API Server

Backend 사전 과제 — AI 챗봇 서비스 API.
- 총 작업 시간: **3시간** (문서 포함)
- 시연 목표: API를 통해 AI를 활용할 수 있음을 증명 + 확장 가능 구조
- 기능 요구사항 상세: `.claude/skills/assignment/SKILL.md` (자동 로드됨)

## Tech Stack
- **Language**: Kotlin 1.9.25 (JDK 21)
- **Framework**: Spring Boot 3.5.13
- **Persistence**: Spring Data JPA + Hibernate 6.6
- **Database**: PostgreSQL 15.8 (Docker, localhost:5433)
- **Validation**: spring-boot-starter-validation (jakarta.validation)
- **Logging**: kotlin-logging-jvm (Slf4j)
- **AI Client**: Gemini (gemini-2.5-flash) via RestClient
- **Build**: Gradle (Kotlin DSL)
- **Testing**: Kotest (BehaviorSpec) + MockK + TestContainers

## Project Structure (단일 모듈)
```
com.sionic
├── ApiApplication.kt
├── domain/                         # 비즈니스 도메인 (Aggregate별 하위 패키지)
│   └── <aggregate>/
│       ├── entity/                 # JPA Entity
│       ├── repository/             # JpaRepository
│       ├── service/                # 도메인 서비스
│       └── controller/             # REST Controller + DTO
└── global/                         # 공통 인프라
    ├── client/                     # 외부 API 클라이언트
    │   ├── AiClient.kt             # AI 추상 인터페이스
    │   ├── GeminiAiClient.kt       # Gemini 구현체
    │   ├── dto/                    # AI 공통 Request/Response
    │   └── gemini/                 # Gemini 전용 Request/Response
    ├── config/                     # Spring 설정 (RestClient, JPA Auditing 등)
    ├── exception/                  # BusinessException, ErrorCode, GlobalExceptionHandler
    ├── response/                   # ApiResponse, ErrorResponse
    └── util/
```

**레이어 규칙:**
- `domain/*` → `global/*` 의존 가능
- `global/*` → `domain/*` 의존 **금지** (역방향 의존성 차단)
- 도메인 간 직접 호출은 Service 레이어를 통해서만

## Database Access
- DB URL: `jdbc:postgresql://localhost:5433/mydb`
- 실행: `docker run -d --name postgres -e POSTGRES_DB=mydb -e POSTGRES_USER=user -e POSTGRES_PASSWORD=password -p 5433:5432 postgres:15.8`
- Secrets: `src/main/resources/application-secret.properties` (gitignored)

## Build & Run
- 실행: IntelliJ Run Configuration (`ApiApplication.kt`) 또는 `./gradlew bootRun`
- 테스트: `./gradlew test`
- 클래스만 컴파일: `./gradlew classes`

## Skills (Claude Code)
`.claude/skills/` 하위 skill은 관련 작업 시 자동 로드됩니다:
- `assignment` — 과제 기능 요구사항 (도메인 스키마, 권한, 비즈니스 룰)
- `kotlin-entity` — JPA 엔티티/리포지토리/트랜잭션/동시성
- `kotlin-api` — Controller/DTO/예외처리/보안
- `kotlin-testing` — Kotest/MockK/TestContainers/ArchUnit

## Key Conventions (핵심 요약)
- **Entity**: `open class` + `val`(불변) / `var protected set`(가변) + `private constructor` + 정적 팩토리 메서드
- **BaseTimeEntity**: `createdAt`, `updatedAt` (`Instant` / UTC) — 모든 엔티티가 상속 (hard delete 정책, soft delete 미사용)
- **Delete Policy**: Hard delete + cascade (soft delete 미적용)
- **Repository**: `findByIdOrNull` 사용, `findById` 금지
- **DTO**: Request는 nullable + `@field:` validation + `validatedX()` helper / Response는 `data class`
- **Exception**: `BusinessException(ErrorCode)` → `GlobalExceptionHandler` → `ErrorResponse(code, message)` (ProblemDetail 미사용)
- **Success Response**: `ApiResponse.ok(data)` 또는 `ApiResponse.ok()`
- **DI**: 생성자 주입 only (`@Autowired` 필드 주입은 `src/test`에서만 허용)
- **Null Safety**: `!!` 금지 (DTO validation helper 예외)
