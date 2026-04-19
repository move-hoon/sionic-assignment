---
name: kotlin-testing
description: Use this skill when writing unit tests, integration tests, or architecture tests in Kotlin + Spring Boot. Applies whenever creating test classes, mocking dependencies, setting up test infrastructure, or validating layer boundaries. Covers Kotest BDD style, MockK, TestContainers for real DB testing, and ArchUnit for structural enforcement.
---

# Kotlin Testing Skill

## Test Framework Policy
- **Preferred:** Kotest (`BehaviorSpec` for BDD: `given / when / then`)
- **Allowed:** JUnit 5
- **Forbidden:** JUnit 4, Mockito, H2 for integration tests

## Mocking
- MockK **exclusively**
- `every { ... }` for regular functions
- `coEvery { ... }` for `suspend` functions
- `verify { ... }` / `coVerify { ... }` for assertions

```kotlin
class UserServiceTest : BehaviorSpec({
    val userRepository = mockk<UserRepository>()
    val userService = UserService(userRepository)

    given("a valid email") {
        every { userRepository.findByEmail("a@b.com") } returns null
        every { userRepository.save(any()) } answers { firstArg() }

        `when`("creating user") {
            val result = userService.create(CreateUserRequest("a@b.com", "name"))

            then("user is persisted") {
                result.email shouldBe "a@b.com"
                verify { userRepository.save(any()) }
            }
        }
    }
})
```

## Integration Tests
**TestContainers** for real infrastructure (DB, Redis, Kafka) — H2 is FORBIDDEN.

```kotlin
@SpringBootTest
@Testcontainers
class UserIntegrationTest : BehaviorSpec() {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:15.8").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
```

## Test Data Generation
- **Instancio** or **Fixture Monkey** (Kotlin support)
- Avoid hand-rolled builders for complex entities

```kotlin
val user = Instancio.of(User::class.java)
    .set(field(User::email), "test@example.com")
    .create()
```

## Injection in Tests
- `@Autowired` field injection is **ALLOWED ONLY in `src/test`**
- Production code (`src/main`) still requires constructor injection

## Architecture Tests (ArchUnit)
Enforce layer boundaries structurally:

```kotlin
class ArchitectureTest {
    private val classes = ClassFileImporter().importPackages("com.sionic")

    @Test
    fun `api layer must not depend on infrastructure`() {
        noClasses().that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .check(classes)
    }

    @Test
    fun `controllers must be in api package`() {
        classes().that().areAnnotatedWith(RestController::class.java)
            .should().resideInAPackage("..api..")
            .check(classes)
    }
}
```

## Test Naming
- Kotest: natural language via `given/when/then`
- JUnit 5: backtick descriptions — `` `returns 404 when user not found`() ``

## Coverage Priority
1. **Domain logic** (service methods, business rules) — highest priority
2. **Controllers** (request → response flow, validation, error mapping)
3. **Repositories** (only custom queries, not `findById`)
4. **Architecture** (ArchUnit layer rules)

## Forbidden Patterns
- Mockito (use MockK)
- JUnit 4 (use Kotest or JUnit 5)
- H2 for integration tests (use TestContainers)
- `@MockBean` when unit-level mocking suffices (slows Spring Context)
- Testing getters/setters
- Shared mutable state between tests
