---
title: Spring Boot Guidelines
description: Conventions for Spring Boot development in this project
date: 2025-12-11
---

# Spring Boot Guidelines

**Purpose:** Conventions for Spring Boot development in this project.
**Stack:** Spring Boot 3.5 + WebFlux + Kotlin Coroutines + jOOQ

---

## 1. Prefer Constructor Injection over Field/Setter Injection

Declare all mandatory dependencies as `val` properties and inject them through the constructor. Spring auto-detects a single constructor, so `@Autowired` is unnecessary. Avoid field/setter injection in production code.

**Why this matters:**

- Making dependencies `val` ensures the object is always properly initialized using plain Kotlin language features, without relying on framework-specific mechanisms.
- You can write unit tests without reflection-based initialization or complex mocking.
- Constructor injection clearly communicates class dependencies from the signature alone.
- Spring Boot provides builder extensions (e.g., `WebClient.Builder`) that allow customization at construction time.

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    webClientBuilder: WebClient.Builder
) {
    private val webClient: WebClient = webClientBuilder
        .baseUrl("http://catalog-service.com")
        .filter(clientCredentialsFilter())
        .build()

    // ... methods
}
```

---

## 2. Prefer Internal Visibility for Spring Components

Declare Controllers, their request-handling methods, `@Configuration` classes, and `@Bean` methods with `internal` visibility whenever possible. There's no obligation to make everything `public`.

**Why this matters:**

- Keeping classes and methods `internal` reinforces encapsulation by hiding implementation details from other modules.
- Spring Boot's classpath scanning still detects and invokes `internal` components, so you can safely restrict visibility while the framework wires up beans and handles requests.

**Note:** In EAF modules that expose public APIs (e.g., `eaf-core` domain primitives), use `public` with Kotlin's explicit API mode.

---

## 3. Organize Configuration with Typed Properties

Group application-specific configuration properties with a common prefix in `application.yml`. Bind them to `@ConfigurationProperties` classes with validation annotations so the application fails fast on invalid configuration. Prefer environment variables over profiles for environment-specific values.

**Why this matters:**

- Centralizing configuration in a single `@ConfigurationProperties` bean groups property names and validation rules together.
- Using `@Value("${…}")` scattered across components forces updates at each injection point when keys change.
- Overusing profiles with multiple combinations makes the effective configuration hard to reason about.

```kotlin
@ConfigurationProperties(prefix = "dvmm.vmware")
@Validated
data class VmwareProperties(
    @field:NotBlank
    val vcenterUrl: String,

    @field:NotBlank
    val username: String,

    @field:NotBlank
    val datacenterName: String
)
```

---

## 4. Define Clear Transaction Boundaries

Define each service-layer method as a transactional unit. Annotate query-only methods with `@Transactional(readOnly = true)` and data-modifying methods with `@Transactional`. Keep the code inside each transaction as brief as possible.

**Why this matters:**

- **Single Unit of Work:** Group all database operations for a use case into one atomic unit.
- **Connection Reuse:** A `@Transactional` method runs on a single database connection, avoiding pool overhead.
- **Read-only Optimizations:** `readOnly = true` hints to the driver to optimize for reads.
- **Reduced Contention:** Brief transactions minimize lock duration in high-traffic applications.

**Note:** With jOOQ (our persistence layer), transactions are managed via `DSLContext.transactionCoroutine {}` for reactive code or `@Transactional` for blocking contexts.

```kotlin
@Service
class VmRequestQueryService(
    private val repository: VmRequestProjectionRepository
) {
    @Transactional(readOnly = true)
    suspend fun findById(id: VmRequestId): VmRequestProjection? {
        return repository.findById(id)
    }
}
```

---

## 5. Separate Web Layer from Persistence Layer

Don't expose domain entities or jOOQ records directly as responses in controllers. Define explicit request and response DTOs. Apply Jakarta Validation annotations on request DTOs to enforce input rules.

**Why this matters:**

- Returning entities or records couples your public API to your database schema.
- DTOs declare exactly which fields clients can send or receive, improving clarity and security.
- Per-use-case DTOs allow targeted validation without complex validation groups.

**Our Hexagonal Architecture already enforces this:** The `dvmm-api` module defines DTOs, while `dvmm-domain` contains entities, and `dvmm-infrastructure` holds jOOQ records. These layers cannot intermingle.

```kotlin
// Request DTO in dvmm-api
data class CreateVmRequestDto(
    @field:NotBlank
    @field:Size(min = 3, max = 50)
    val vmName: String,

    @field:Min(1)
    @field:Max(64)
    val cpuCores: Int,

    @field:Min(1)
    @field:Max(512)
    val memoryGb: Int
)

// Response DTO in dvmm-api
data class VmRequestResponseDto(
    val id: UUID,
    val vmName: String,
    val status: String,
    val createdAt: Instant
)
```

---

## 6. Follow REST API Design Principles

- **Versioned, resource-oriented URLs:** Structure endpoints as `/api/v{version}/resources` (e.g., `/api/v1/vm-requests`).
- **Consistent patterns:** Use uniform conventions for collections and sub-resources.
- **Explicit HTTP status codes:** Use `ResponseEntity<T>` or Spring's functional endpoints to return correct status codes (200 OK, 201 Created, 404 Not Found).
- **Pagination:** Use pagination for collection resources that may contain unbounded items.
- **JSON conventions:** Use a JSON object as the top-level structure. Use consistent casing (`camelCase` in this project).

**Reference:** [Zalando RESTful API Guidelines](https://opensource.zalando.com/restful-api-guidelines/)

---

## 7. Use Command Objects for Business Operations

Create purpose-built command records to wrap input data. Accept these commands in service methods to drive creation or update workflows.

**This aligns with our CQRS architecture.** Commands live in `dvmm-application` and clearly communicate what input data is expected.

```kotlin
// Command in dvmm-application
data class CreateVmRequestCommand(
    val tenantId: TenantId,
    val requesterId: UserId,
    val vmName: VmName,
    val cpuCores: CpuCores,
    val memoryGb: MemoryGb,
    val purpose: Purpose
)

// Handler in dvmm-application
class CreateVmRequestHandler(
    private val eventStore: EventStore
) {
    suspend fun handle(command: CreateVmRequestCommand): Result<VmRequestId, CreateError> {
        // ...
    }
}
```

---

## 8. Centralize Exception Handling

Define a global handler class annotated with `@RestControllerAdvice` using `@ExceptionHandler` methods to handle specific exceptions. Return consistent error responses using the ProblemDetails format ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)).

**Why this matters:**

- Always handle exceptions and return standard error responses instead of propagating raw exceptions.
- Centralizing in a `GlobalExceptionHandler` avoids duplicating try/catch logic across controllers.

**Security note:** Resource access errors MUST return 404 (not 403) to prevent tenant enumeration attacks. See [CLAUDE.md](../CLAUDE.md#security-patterns-multi-tenant) for details.

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "Resource not found"
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }
}
```

---

## 9. Actuator Security

Expose only essential actuator endpoints (`/health`, `/info`, `/metrics`) without authentication. All other actuator endpoints must be secured.

**Why this matters:**

- Health and metrics endpoints are critical for monitoring tools (Prometheus, Kubernetes probes).
- Sensitive endpoints like `/actuator/env` or `/actuator/beans` expose internal details and must be protected.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
```

---

## 10. Internationalization with ResourceBundles

Externalize all user-facing text (labels, prompts, messages) into ResourceBundles rather than hardcoding them.

**Why this matters:**

- Hardcoded strings make multi-language support difficult.
- ResourceBundle files per locale allow easy translation management.
- Spring loads the appropriate bundle based on user locale.

---

## 11. Use Testcontainers for Integration Tests

Spin up real services (databases, message brokers) in integration tests to mirror production environments.

**This is already our standard practice.** See [CLAUDE.md](../CLAUDE.md#jooq-code-generation) for details on our jOOQ code generation that also uses Testcontainers.

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class VmRequestIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("eaf_test")
    }

    // ...
}
```

---

## 12. Use Random Port for Integration Tests

Start the application on a random available port to avoid conflicts in CI/CD environments where multiple builds run in parallel.

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MyIntegrationTest {
    // ...
}
```

---

## 13. Logging Best Practices

- **Use a proper logging framework:** Never use `println()` for application logging. Use SLF4J with Logback.
- **Protect sensitive data:** Never log credentials, personal information, or confidential details.
- **Guard expensive log calls:** When building verbose messages at DEBUG or TRACE level, use lazy evaluation.

```kotlin
// Using kotlin-logging (our standard)
private val logger = KotlinLogging.logger {}

// Lazy evaluation - lambda only executes if debug is enabled
logger.debug { "Detailed state: ${computeExpensiveDetails()}" }

// For error logging with exceptions
logger.error(exception) { "Operation failed for request $requestId" }
```

**Why this matters:**

- Logging frameworks provide flexible verbosity control per environment.
- Rich metadata (class names, thread IDs, MDC context) aids diagnosis.
- Structured JSON logs integrate with ELK, Loki, or other analysis tools.

---

## Not Applicable to This Project

The following guidelines from standard Spring Boot best practices do not apply to our stack:

### Open Session in View (OSIV)

We use **jOOQ**, not JPA/Hibernate. OSIV is a Hibernate-specific pattern that doesn't exist in jOOQ. Our explicit SQL queries naturally avoid N+1 problems.

### JPA Entity Mapping

We use jOOQ's type-safe SQL generation. See [CLAUDE.md](../CLAUDE.md#jooq-code-generation) for jOOQ code generation details and the Testcontainers + Flyway setup.

---

## Quick Reference

| Guideline | Status |
|-----------|--------|
| Constructor injection | **Required** |
| Internal visibility for Spring components | Preferred |
| `@ConfigurationProperties` for config | **Required** |
| Transaction boundaries | **Required** |
| DTOs for API layer | **Enforced by architecture** |
| REST API conventions | **Required** |
| Command objects (CQRS) | **Enforced by architecture** |
| Centralized exception handling | **Required** |
| Actuator security | **Required** |
| Testcontainers | **Required** |
| Random port for tests | **Required** |
| kotlin-logging | **Required** |
