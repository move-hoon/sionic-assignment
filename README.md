# Backend Assignment

## Tech Stack

- Kotlin 1.9.25 / Spring Boot 3.5.13
- Java 21
- PostgreSQL 15.8
- Gradle Kotlin DSL

## Getting Started

### Prerequisites

- JDK 21
- PostgreSQL 15.8

### Run

```bash
./gradlew bootRun
```

## Project Structure

```
src/main/kotlin/com/sionic/
├── domain/          # 기능별 도메인 패키지
└── global/
    ├── client/      # 외부 AI API 클라이언트
    ├── config/      # Spring 설정
    ├── exception/   # 예외 처리 (ErrorCode, GlobalExceptionHandler)
    ├── response/    # 공통 응답 포맷
    └── util/        # 공통 유틸리티
```

## API Response Format

**Success**
```json
{ "success": true, "data": { ... } }
```

**Error**
```json
{ "code": "C002", "message": "요청한 리소스를 찾을 수 없습니다." }
```
