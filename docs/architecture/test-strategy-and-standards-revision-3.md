# Test Strategy and Standards (Revision 3)

(Fulfills "Constitutional TDD").

## Core Testing Philosophy

**Hybrid Testing Strategy with Nullable Pattern Integration**

Based on performance analysis and nullable pattern implementation success (61.6% performance improvement), the testing distribution has been optimized:

* **Original Plan**: 70% integration, 10% unit, 20% E2E
* **Current Strategy**: 40-50% fast logic tests (nullable pattern), 30-40% critical integration, 10-20% E2E

**Nullable Pattern Integration Philosophy**: Use nullable infrastructure implementations to achieve fast business logic testing while preserving integration-first philosophy for critical paths.

## Testing Framework and Standards

* **Philosophy:** Constitutional TDD (RED-GREEN-Refactor); Inverted Pyramid (40-50% Integration); 85%+ Line Coverage; 80%+ Mutation Coverage.
* **Backend Framework:** **Kotest** (JUnit forbidden).
* **Testing Mandates (The "No Mocks" rule):**
  1. **Stateful Dependencies (DB/Auth):** **Testcontainers ONLY** (Postgres, Keycloak).
  2. **Stateless Dependencies (External APIs):** **Nullable Design Pattern** (Stubbed Adapters/Ports). (WireMock is forbidden).
  3. **In-Memory DBs (H2):** Explicitly forbidden.
* **Frontend Testing:** Jest + React Testing Library.
* **E2E Testing:** Playwright.
* **CI:** CI build runs *all* checks (Detekt, ktlint, OWASP check, all unit and integration tests).

## "Integration-First, No Mocks" Philosophy Enhanced with Nullable Pattern

The EAF implements a hybrid approach that maintains integration-first philosophy while leveraging nullable pattern for fast business logic testing.

**Testing Distribution (Hybrid Strategy):**
```
Fast Logic Tests (40-50%) - Nullable Pattern
Critical Integration Tests (30-40%) - Testcontainers
E2E Tests (10-20%) - Full Stack
```

### Core Principles

* **Hybrid Approach:** Nullable pattern for fast business logic; Testcontainers for critical integration paths
* **No Mocks Policy:** Strict avoidance of traditional mocking libraries (Mockito, MockK) - nullable pattern provides real infrastructure substitutes
* **Real Dependencies for Integration:** Critical paths use actual PostgreSQL, Redis, and Keycloak instances
* **Behavior Validation:** Tests validate observable outcomes using nullable pattern state inspection or integration test verification
* **State-Based Verification:** Nullable pattern provides `getRecordedEvents()`, `getStoredEventCount()` for test verification
* **Performance-Aware Testing:** Use decision matrix to select optimal test approach based on scenario requirements

**When to Use Nullable Pattern:**
- Business logic testing with infrastructure dependencies
- Performance-critical test scenarios (>5 second baseline)
- Domain validation requiring audit/metrics infrastructure
- Rapid feedback development cycles

**When to Use Integration Testing:**
- Cross-system boundary validation
- Database schema and constraint testing
- Security integration (JWT with actual cryptography)
- End-to-end workflow validation

## Security-Lite Profile for Fast Security Testing

The security-lite profile enables fast, isolated security testing without database or external dependencies, achieving 65% faster test execution while maintaining cryptographic integrity.

**Purpose:**
- Provide fast JWT/security tests without database, Flyway, or Keycloak dependencies
- Enable real cryptographic validation using local RSA keys
- Eliminate test flakiness from container startup issues
- Support CI/CD pipeline optimization

**Usage:**
```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test", "security-lite")
@Import(TestSecurityConfig::class)
class UnauthorizedAccessIntegrationTest : FunSpec({
    // Test with real JWT validation but no external dependencies
})
```

**Benefits:**
- Test execution: 35s (vs 100s with full stack)
- Zero external dependencies for security tests
- Real RSA 2048-bit cryptographic validation
- 100% test stability (no container timing issues)
- Parallel test execution capability

## JWT Authentication Testing Architecture

Security testing uses real cryptographic tokens and validation, never mocked security contexts.

**TestTokenGenerator:**
```kotlin
@Component
class TestTokenGenerator {
    private val keyPair: KeyPair = generateRSAKeyPair()

    fun generateToken(
        username: String,
        tenantId: String,
        roles: List<String>,
        expirationMinutes: Long = 60
    ): String {
        return Jwts.builder()
            .setSubject(username)
            .claim("tenant_id", tenantId)
            .claim("roles", roles)
            .setExpiration(Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expirationMinutes)))
            .signWith(keyPair.private, SignatureAlgorithm.RS256)
            .compact()
    }
}
```

## Mutation Testing Scope (Pitest)

Target packages (unit/module scope only):
* `com.axians.eaf.core..*`
* `com.axians.eaf.security..*`
* `com.axians.eaf.*.domain..*`
* `com.axians.eaf.*.application..*`

Excluded from mutation runs:
* Adapters/infrastructure (`..adapters..`, `..web..`, `..persistence..jpa..`)
* Integration/E2E tests, Testcontainers-based tests, generated code

## Testcontainers Integration Patterns

**Working Container Management Pattern:**
```kotlin
companion object {
    @Container
    @ServiceConnection
    @JvmStatic
    private val postgresContainer = PostgreSQLContainer("postgres:16.1")
        .withReuse(true)  // 50% faster subsequent runs

    init {
        postgresContainer.start()  // Explicit startup required
    }
}
```

## Performance Optimizations

Successfully integrated proven patterns:
* **Container Reuse**: Add `.withReuse(true)` for 50% faster subsequent test runs
* **Parallel Startup**: Start containers in parallel threads for 30% faster initialization
* **Total Improvement**: Up to 65% reduction in test execution time
* **Reliability**: 100% consistent container timing

## Critical Testing Anti-Patterns (Avoid These)

**Container Management Anti-Patterns:**
* ❌ @DynamicPropertySource with manual port mapping
* ❌ Per-test container lifecycle management
* ❌ Mixed JUnit/Kotest annotations
* ❌ In-memory database substitutes for integration tests

**Security Testing Anti-Patterns:**
* ❌ MockMvc for security endpoint testing
* ❌ @MockBean for security components
* ❌ Hardcoded JWT tokens without proper cryptographic signing
* ❌ Disabled security in test profiles

**Database Testing Anti-Patterns:**
* ❌ H2 in-memory database for "integration" tests
* ❌ @Sql scripts that don't match production schemas
* ❌ Test data that doesn't respect tenant isolation
* ❌ Transactional rollback hiding commit-time failures

**CRITICAL: JUnit/Kotest Mixing**
* ❌ **NEVER** mix JUnit and Kotest annotations. JUnit annotations like `@Disabled` are **completely ignored** by Kotest
* ✅ Use Kotest-specific patterns: file renaming (`Test.kt.disabled`), conditional execution (`.config(enabled = false)`), or class renaming

## Nullable Design Pattern Testing Standards

**Factory Pattern Implementation:**
```kotlin
// ✅ CORRECT - Factory pattern for nullable implementations
object EventStoreFactory {
    fun createNull(): EventStore = NullableEventStore.createNull()
}

class NullableEventStore private constructor() : EventStore {
    companion object {
        fun createNull(): NullableEventStore = NullableEventStore()
    }
}
```

**Configuration with Spring Boot:**
```kotlin
@TestConfiguration
@ConditionalOnProperty("eaf.testing.nullable.eventstore", havingValue = "true")
class NullableEventStoreConfig {
    @Bean
    @Primary
    fun eventStore(): EventStore = NullableEventStore.createNull()
}
```

**Anti-Patterns (PROHIBITED):**
* ❌ **Domain Logic Stubbing**: Never stub business logic - only infrastructure adapters
* ❌ **Behavioral Divergence**: Nullable implementations must preserve business logic behavior
* ❌ **Test Profile Mixing**: Don't mix nullable and production beans in same test context
* ❌ **Performance Regression**: Monitor for >10% performance degradation in CI pipeline

## Test Coverage and Quality Gates

**Coverage Requirements:**
* **Overall**: 85% line coverage minimum
* **Domain Layer**: 90% coverage
* **Integration Tests**: 100% of database operations
* **E2E Tests**: 100% of security paths, 80% of happy paths
* **Mutation Coverage**: 80% minimum with Pitest

**Quality Gate Criteria:**
Before marking any story complete:
* [ ] All P0 tests passing
* [ ] All P1 tests passing
* [ ] 80% of P2 tests passing
* [ ] No security vulnerabilities in test results
* [ ] Performance benchmarks met
* [ ] Test documentation complete

## Multi-Tenant RLS Validation

* **Negative Tests**: Attempt cross-tenant reads/writes at API and repository layers; expect denial.
* **SQL Policy Review**: Named RLS policies per table; enforced via migration checks.
* **SAST/Lint**: Static scanning of SQL fragments to ensure tenant predicates present.
* **Canary Queries**: Periodic synthetic checks to detect tenant leaks in production.

## Frontend Testing Strategy

* **Unit/Integration**: React Testing Library with Jest; avoid shallow rendering.
* **E2E**: Playwright against a running backend (Compose profile via Testcontainers where applicable).
* **Accessibility**: axe automated checks in CI; manual audits for critical flows.
* **Visual Regression**: baseline screenshots for critical pages.
* **CI**: Lint → Unit → Integration → E2E; failures block merge.

-----
