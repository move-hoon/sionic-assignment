---
name: kotlin-entity
description: Use this skill when creating or modifying JPA entities, repositories, or database access code in Kotlin + Spring Boot. Applies whenever touching @Entity classes, JpaRepository interfaces, transactional services, or designing DB schemas. Covers entity equality, lazy loading, N+1 prevention, locking strategies, and value class ID patterns.
---

# Kotlin JPA Entity & Persistence Skill

## Imports (Non-Negotiable)
- `jakarta.*` namespace ONLY (`javax.*` is FORBIDDEN)
- `java.time.*` (`Instant` preferred for persisted timestamps; `java.util.Date`/`Calendar` FORBIDDEN)
- Kotlin nullable types (`T?`) — `java.util.Optional` FORBIDDEN

## Entity Class Rules

### Structure
- Use `open class` for JPA entities — `data class` is **FORBIDDEN** (lazy loading + hashCode issues)
- Ensure `kotlin("plugin.jpa")` and `kotlin("plugin.allopen")` are configured
- `allOpen` annotations: `@Entity`, `@MappedSuperclass`, `@Embeddable`

### val vs var Policy
- **`val`**: 변경이 잘 일어나지 않는 불변 필드 (id, 식별자, 가입 후 불변값)
- **`var` + `protected set`**: 변경 가능성이 있는 필드 — 외부 직접 수정 차단, 엔티티 내부 도메인 메서드로만 변경
- 외부 수정을 막는 이유: 비즈니스 규칙이 엔티티 내부에 응집되도록 (Rich Domain Model)

### 엔티티 선언 규칙 (중요)
1. **생성자는 `private constructor`** — 정적 팩토리 메서드로만 생성 경로 강제
2. **`val` 필드는 생성자 파라미터에 선언** (어노테이션과 함께)
3. **`var` 필드는 생성자 파라미터에 `var`/`val` 없이 평문 파라미터로 받고, 본문(body)에서 `var`로 선언 후 해당 파라미터로 초기화**
   - 본문 `var`에 `@Column` 등 어노테이션 부착
   - `protected set`으로 외부 setter 차단

### 표준 예시
```kotlin
@Entity
@Table(name = "books")
@SQLDelete(sql = "UPDATE books SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
class BookEntity private constructor(
    @Id
    @JdbcTypeCode(Types.VARCHAR)
    @Column(length = 36, updatable = false, nullable = false)
    val id: UUID,

    @Column(length = 13, updatable = false, nullable = false, unique = true)
    val isbn13: String,

    title: String,
    author: String,
    publicationYear: Int? = null,
) : BaseTimeEntity() {

    @Column(nullable = false, length = 255)
    var title: String = title
        protected set

    @Column(length = 255)
    var author: String = author
        protected set

    @Column(name = "publication_year")
    var publicationYear: Int? = publicationYear
        protected set

    companion object {
        fun create(
            id: UUID,
            isbn13: String,
            title: String,
            author: String,
            publicationYear: Int? = null,
        ): BookEntity = BookEntity(
            id = id,
            isbn13 = isbn13,
            title = title,
            author = author,
            publicationYear = publicationYear,
        )
    }

    fun updateTitle(newTitle: String) {
        require(newTitle.isNotBlank())
        this.title = newTitle
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BookEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
```

### 금지 패턴
```kotlin
// ❌ 생성자 파라미터에 var 직접 선언 (외부 setter 노출)
class BookEntity(
    var title: String,
)

// ❌ 본문에서 기본값으로 초기화 (생성자 값이 무시됨)
class BookEntity private constructor() {
    var title: String = ""
        protected set
}
```

### Equality
- `equals()` / `hashCode()`는 **PK(`id`) 단독 기반**
- 가변 필드 / 컬렉션은 equality 포함 금지

### ID 타입 안정성
`@JvmInline value class`로 Primitive Obsession 방지:
```kotlin
@JvmInline
value class BookId(val value: UUID)
```

## BaseTimeEntity (공통 감사 필드)
이 프로젝트의 엔티티는 `BaseTimeEntity`를 상속하여 `createdAt`, `updatedAt`을 가집니다.

```kotlin
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseTimeEntity {
    @CreatedDate
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    @LastModifiedDate
    @Column(nullable = false)
    lateinit var updatedAt: Instant
        protected set
}
```

`@EnableJpaAuditing`을 Config 클래스에 추가해야 감사 필드가 자동 주입됩니다:
```kotlin
@Configuration
@EnableJpaAuditing
class JpaAuditingConfig
```

## Delete Policy
이 프로젝트 기본 정책은 soft delete가 아니라 **hard delete** 입니다.

- 외래키를 엔티티 연관관계로 두지 않는 경우, 서비스 레이어에서 삭제 순서를 직접 관리해야 합니다.
- 예: `feedback -> chat -> thread`
- JPA cascade에 기대지 않는다면, 서비스 메서드에서 명시적으로 정리하는 것이 계약입니다.

## Repository Rules
- Extend `JpaRepository<Entity, Long>`
- Single record lookup: `findByIdOrNull()` (not `findById()`) — null-safe
- Complex queries: JPQL `@Query` with parameter binding
- **Raw SQL string concatenation is STRICTLY FORBIDDEN** (SQL injection risk)

```kotlin
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?

    @Query("SELECT u FROM User u WHERE u.status = :status")
    fun findByStatus(@Param("status") status: UserStatus): List<User>
}
```

## Service Layer & Transactions
- Constructor injection ONLY (val properties) — `@Autowired` field injection FORBIDDEN in production code
- Class-level `@Transactional(readOnly = true)` as default
- Override with `@Transactional` on write methods only
- Transactions MUST be SHORT — **NO external API calls inside transactions**

```kotlin
@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
) {
    @Transactional
    fun create(request: CreateUserRequest): User { ... }
}
```

## Performance (Critical)

### N+1 Prevention
Lazy loading without fetch strategy is a **CODE SMELL**. ALWAYS use:
- `@EntityGraph` (preferred for Spring Data)
- `JOIN FETCH` in JPQL (for complex cases)

```kotlin
@EntityGraph(attributePaths = ["orders"])
fun findWithOrdersById(id: Long): User?
```

### Projection
Use interface-based projection for read-only views — avoids entity hydration overhead.

### Batch Fetch
Configure `spring.jpa.properties.hibernate.default_batch_fetch_size` in application.yml.

## Concurrency & Locking

| Scenario | Strategy |
|----------|----------|
| Low contention updates (profile edits) | `@Version` (optimistic lock) |
| High contention (money, inventory) | `PESSIMISTIC_WRITE` + `jakarta.persistence.lock.timeout` |
| Cluster-wide coordination | Redisson distributed lock |
| Single-JVM async | Kotlin `Mutex` (not `ReentrantLock`) |

```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
fun findByIdForUpdate(id: Long): Account?
```

## Null Safety
- `!!` operator **FORBIDDEN** in entity/repository/service logic
- Use `?: throw` or `?.let` for null handling
- Reflect DB column `nullable` accurately in Kotlin type

## Forbidden Patterns
- `data class` for entities
- `findById()` (use `findByIdOrNull()`)
- String concatenation in `@Query`
- `@Autowired` field injection in `src/main`
- External API calls inside `@Transactional`
- `java.util.Optional` return types
