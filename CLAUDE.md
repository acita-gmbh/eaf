# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the **Enterprise Application Framework (EAF) v1.0** - a modern, Kotlin-based enterprise-grade framework designed for multi-tenant applications. The project is in Phase 4 (Implementation) with comprehensive architectural documentation defining the technical approach.

**Implementation Status**: Phase 4 Ready (November 2025) - Proceed with implementation following comprehensive architectural patterns and quality standards defined in architecture.md, PRD.md, tech-spec.md, and 112 implementation-ready stories.

## Architecture At A Glance

- **Style**: Hexagonal + Spring Modulith (Kotlin/JVM 21)
- **Domain**: CQRS + Event Sourcing via Axon Framework 4.12.1
- **Data**: PostgreSQL 16.10 (event store + projections), jOOQ 3.20.8 for read projections
- **Security**: Keycloak 26.4.2 OIDC + 10-layer JWT validation, 3-layer tenant isolation
- **Multi-Tenancy**: Defense-in-depth with request filter, service validation, PostgreSQL RLS
- **Testing**: JUnit 6.0.1 + AssertJ 3.27.3 + Testcontainers, Nullable Design Pattern, Pitest 1.19.0 mutation testing
- **DevEx**: Gradle 9.1.0 monorepo, one-command onboarding, Picocli scaffolding CLI
- **Ops**: Docker Compose on-prem, multi-arch support (amd64, arm64, ppc64le optional)

See the full design in **docs/architecture.md** (159 KB, 89 architectural decisions documented).

## Documentation Structure

**Core Planning Documents:**
- [`docs/PRD.md`](docs/PRD.md) - Product Requirements (30 FRs, 3 NFRs, success criteria)
- [`docs/architecture.md`](docs/architecture.md) - Unified Decision Architecture (159 KB, Production-Ready)
- [`docs/tech-spec.md`](docs/tech-spec.md) - Technical Specification (FR-to-Epic mapping)
- [`docs/epics.md`](docs/epics.md) - Epic breakdown with story sequencing
- [`docs/stories/epic-*/story-*.md`](docs/sprint-artifacts/) - 112 implementation-ready stories with code examples

**Implementation Standards:**
- [`docs/architecture/coding-standards.md`](docs/architecture/coding-standards.md) - Detailed Kotlin/Spring coding standards
- [`docs/architecture/test-strategy.md`](docs/architecture/test-strategy.md) - Constitutional TDD and testing patterns

**Implementation Tracking:**
- [`docs/bmm-workflow-status.md`](docs/bmm-workflow-status.md) - Current workflow state
- [`docs/pre-epic-1-checklist.md`](docs/pre-epic-1-checklist.md) - Pre-implementation validation results

## Core Architecture Principles

- **Hexagonal Architecture with Spring Modulith** - Module boundaries programmatically verified with Konsist 0.17.3
- **CQRS/Event Sourcing with Axon Framework** - v4.12.1 current, v5 migration planned Q3-Q4 2026
- **Constitutional TDD** - Test-first mandatory, enforced by Git hooks and CI/CD
- **"No Mocks" Policy** - Testcontainers for stateful deps, Nullable Design Pattern for stateless
- **Quality-First Development** - Comprehensive quality gates with zero-violations policy

## Technology Stack

**For complete technology details and version verification, see:** [Architecture Section 2](docs/architecture.md#2-version-verification-log)

### Core Technologies (All Verified 2025-10-30/31, Re-Verified 2025-11-01)
- **Language**: Kotlin 2.2.21 (verified current stable)
- **Runtime**: JVM 21 LTS
- **Framework**: Spring Boot 3.5.7 (verified current GA)
- **Architecture Enforcement**: Spring Modulith 1.4.4 (verified current stable)
- **CQRS**: Axon Framework 4.12.1 (verified stable)
- **Functional**: Arrow 2.1.2 for Either<Error,Success> domain error handling
- **Database**: PostgreSQL 16.10 with mandatory optimizations (BRIN indexes, partitioning)
- **Build**: Gradle 9.1.0 monorepo with Version Catalogs

### Quality & Development Tools
- **Formatting**: ktlint 1.7.1 (enforced, zero violations)
- **Static Analysis**: Detekt 1.23.8 (enforced, zero violations)
- **Architecture Testing**: Konsist 0.17.3 (boundary verification)
- **Mutation Testing**: Pitest 1.19.0 (60-70% minimum coverage target)
- **Coverage**: Kover 0.9.3 (85%+ line coverage target)
- **API Documentation**: Springdoc OpenAPI 2.6.0

## Critical Kotlin Standards

**For complete coding standards, see:** [Coding Standards](docs/architecture/coding-standards.md)

### MUST Follow (Zero-Tolerance Policies)

1. **NO wildcard imports** - Every import must be explicit
2. **NO generic exceptions** - Always use specific exception types (except infrastructure interceptors - see coding standards)
3. **Use JUnit 6 + AssertJ** - All tests must use JUnit 6.0.1 with AssertJ 3.27.3 for assertions
4. **Integration tests preferred** - Use Testcontainers for real dependencies
5. **Version Catalog Required** - All versions in `gradle/libs.versions.toml`
6. **Use Arrow for error handling** - Either<Error,Success> in domain, "check and throw" at boundaries
7. **Spring Modulith compliance** - ModuleMetadata classes with @ApplicationModule

### Advanced Patterns
- **Nullable Design Pattern**: Fast infrastructure substitutes with factory pattern (`createNull()`)
- **Multi-Tenancy**: 3-layer enforcement with context propagation
- **Security Testing**: Security-lite profile for fast JWT tests
- **Infrastructure Interceptor Exception Pattern**: Legitimate generic catch for observability (must re-throw immediately)

## Project Structure

```
eaf-v1/
├── framework/                   # Core framework modules (libraries only)
│   ├── core/                   # DDD base classes, domain primitives
│   ├── security/               # 10-layer JWT validation, tenant isolation
│   ├── cqrs/                   # CQRS/ES with Axon
│   ├── observability/          # Metrics, logging, tracing
│   ├── workflow/               # Flowable BPMN integration
│   ├── persistence/            # jOOQ adapters, projections
│   └── web/                    # REST controllers, global advice
├── products/                   # Deployable Spring Boot applications
│   └── widget-demo/            # Reference implementation (Epic 10)
├── shared/                     # Shared code
│   ├── shared-api/             # Axon commands, events, queries
│   ├── shared-types/           # Common types
│   └── testing/                # Test utilities, nullable implementations
├── apps/                       # Frontend applications
│   └── admin/                  # shadcn-admin-kit operator portal
├── build-logic/                # Gradle convention plugins
├── config/                     # Configuration (detekt, konsist)
├── scripts/                    # Development scripts (init-dev.sh, etc.)
├── tools/                      # Developer tools
│   └── eaf-cli/                # Scaffolding CLI (Epic 7)
└── docs/                       # Documentation
    ├── architecture.md          # Decision Architecture (159 KB)
    ├── PRD.md                   # Product Requirements
    ├── tech-spec.md             # Technical Specification
    ├── epics.md                 # Epic breakdown
    └── stories/                 # 112 implementation-ready stories
```

**Architectural Principle**: Framework modules contain ONLY infrastructure (publishable libraries). Domain aggregates, handlers, and business logic belong in product modules.

## Testing Requirements

**For comprehensive testing strategy, see:** [Test Strategy](docs/architecture/test-strategy.md)

### Framework & Philosophy
- **Primary**: JUnit 6.0.1 with AssertJ 3.27.3 - Modern, Kotlin-friendly testing framework
- **Core Strategy**: Constitutional TDD - test-first mandatory
- **7-Layer Defense**: Static → Unit → Integration → Property → Fuzz → Concurrency → Mutation
- **Nullable Pattern**: 100-1000x performance improvement for business logic testing
- **Coverage**: 85% line coverage, 60-70% mutation coverage minimum
- **Testing Pyramid**: 40-50% unit (nullable), 30-40% integration (Testcontainers), 10-20% E2E

### Critical Testing Rules
- **Use Testcontainers for stateful dependencies** - PostgreSQL, Redis, Keycloak (H2 explicitly forbidden)
- **Use Nullable Design Pattern for stateless dependencies** - Infrastructure adapters
- **@SpringBootTest Pattern**: Use `@Autowired` field injection + `@BeforeEach` setup (standard JUnit 6 pattern)
- **Plugin Order**: `id("eaf.testing")` BEFORE `id("eaf.spring-boot")` in product modules
- **Async Testing**: Use polling/await patterns for asynchronous tests (e.g., `Awaitility` library)
- **AssertJ Assertions**: Use fluent AssertJ API for all assertions - clear, readable, Kotlin-friendly

### Nullable Design Pattern
- **Factory Pattern**: `createNull()` convention for all nullable implementations
- **Purpose**: Fast domain testing with real business logic, stubbed infrastructure
- **Contract Testing**: Mandatory behavioral parity validation
- **Performance**: Target 100-1000x improvement over integration test baseline

## Quality Tools & Enforcement

- **Static Analysis**: ktlint 1.7.1, Detekt 1.23.8 with zero violations policy
- **Architecture Compliance**: Konsist 0.17.3 rules for module boundaries and coding standards
- **Security Scanning**: OWASP Dependency Check, fuzz testing (Jazzer 0.25.1)
- **Test Coverage**: Kover 0.9.3 for 85%+ line coverage, Pitest 1.19.0 for 60-70% mutation coverage
- **CI/CD**: Fast (<15min), Nightly (~2.5h with property/fuzz/concurrency/mutation tests)

## Multi-Tenancy

**For detailed patterns, see:** [Coding Standards - Multi-Tenancy](docs/architecture/coding-standards.md#multi-tenancy-patterns)

- **3-Layer Enforcement**: Request filter → Service validation → PostgreSQL RLS
- **Context Propagation**: Axon interceptors for async event processors
- **Fail-Closed Design**: Missing/invalid tenant context results in immediate rejection
- **Stack-based TenantContext**: ThreadLocal with automatic cleanup

### TenantContext API (Stories 4.1 & 4.2)

```kotlin
// Fail-closed (throws exception if missing) - Use in command handlers
val tenantId: String = TenantContext().getCurrentTenantId()

// Nullable (returns null if missing) - Use for defensive checks
val tenantId: String? = TenantContext().current()

// Stack operations (handled by Layer 1 filter)
tenantContext.setCurrentTenantId("tenant-a")
tenantContext.clearCurrentTenant()
```

### Command Handler Tenant Validation (Story 4.2)

```kotlin
@CommandHandler
constructor(command: CreateWidgetCommand) {
    val currentTenant = TenantContext().getCurrentTenantId()
    require(command.tenantId == currentTenant) {
        "Access denied: tenant context mismatch" // Generic message (CWE-209 protection)
    }
    apply(WidgetCreatedEvent(...))
}
```

## Security Implementation

**For comprehensive security details, see:** [Architecture - Security Decisions](docs/architecture.md#security-decisions)

### 10-Layer JWT Validation System
1. **Format Validation** - JWT structure verification
2. **Signature Validation** - RS256 cryptographic verification
3. **Algorithm Validation** - Prevent algorithm confusion attacks
4. **Claim Schema Validation** - Required claims enforcement (sub, iss, aud, exp, iat, tenant_id, roles)
5. **Time-based Validation** - exp/iat/nbf with 30s clock skew tolerance
6. **Issuer/Audience Validation** - Trust boundary enforcement
7. **Token Revocation Check** - Redis blacklist verification
8. **Role Validation** - Role whitelist and privilege escalation detection
9. **User Validation** - User existence and active status (optional, configurable)
10. **Injection Detection** - SQL injection, XSS, JNDI attack patterns

### 3-Layer Tenant Isolation
- **Layer 1**: TenantContextFilter extracts tenant_id from JWT → ThreadLocal
- **Layer 2**: Axon TenantValidationInterceptor validates command tenantId matches context
- **Layer 3**: PostgreSQL RLS policies enforce database-level isolation

## Development Workflow

### One-Command Onboarding (Story 1.6)
```bash
# Clone EAF repository (Story 1.1 creates this from prototype)
git clone <eaf-v1-repo>
cd eaf-v1

# Initialize development stack (Story 1.6)
./scripts/init-dev.sh
```

### Daily Development Commands
```bash
# Infrastructure services
./scripts/init-dev.sh                   # Start PostgreSQL, Keycloak, Redis, Prometheus, Grafana
./scripts/stop-dev.sh                   # Stop all services

# Backend development
./gradlew :products:widget-demo:bootRun # Start application

# Frontend development (Epic 9+)
npm run dev --prefix apps/admin         # Start shadcn-admin-kit portal

# Quality checks
./gradlew clean build                   # Full quality check (ktlint, Detekt, Konsist, tests)
./gradlew test                          # Run unit tests
./gradlew integrationTest               # Run integration tests
./gradlew check                         # All tests and quality gates
```

### Scaffolding CLI (Epic 7)
```bash
# Generate CQRS aggregate
eaf scaffold aggregate <Domain> --module <module-name>

# Generate API resource
eaf scaffold api-resource <Domain> --path /api/widgets

# Generate projection
eaf scaffold projection <Domain> --query-model WidgetSummary

# Generate shadcn-admin-kit UI resource
eaf scaffold ra-resource <Domain> --fields id,name,status
```

## Error Handling Strategy

**Arrow-Fold-Throw-ProblemDetails Pattern (Architecture Decision #4):**
1. **Domain**: Returns `Either.Left(DomainError)` for business rule violations
2. **Controller**: Folds Either, converts to Spring exceptions
3. **Global Advice**: Formats as RFC 7807 ProblemDetail with traceId and tenantId
4. **Frontend**: Parses application/problem+json for error display

**Features:**
- Comprehensive error catalog by category
- Context enrichment (traceId, tenantId)
- Structured logging and metrics
- Frontend integration patterns

## Version Management

### Critical Version Constraints (All Verified 2025-11-01)
**For complete compatibility matrix, see:** [Architecture Section 2](docs/architecture.md#2-version-verification-log)

- **Kotlin 2.2.21** - Current stable (released 2025-10-23)
- **Spring Boot 3.5.7** - Current GA (released 2025-10-23)
- **Spring Modulith 1.4.4** - Current stable (released 2025-10-27)
- **Axon Framework 4.12.1** - Production stable (released 2025-01-06, maintained until Axon 5.x migration Q3-Q4 2026)
- **Gradle 9.1.0** - Current stable
- **JUnit 6.0.1** - Current stable (released 2025-09-30)
- **AssertJ 3.27.3** - Current stable assertion library

### Version Catalog Enforcement
**MANDATORY**: All dependency versions MUST be centralized in `gradle/libs.versions.toml`.

```kotlin
// ✅ CORRECT - Use version catalog references
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spring.boot)
}
dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.bundles.axon.framework)
}

// ❌ WRONG - Never use explicit versions
plugins {
    kotlin("jvm") version "2.2.21"  // FORBIDDEN - Use version catalog
}
```

## Critical Kotlin Standards

**For complete coding standards, see:** [Coding Standards](docs/architecture/coding-standards.md)

### Zero-Tolerance Policies (MANDATORY)

These requirements are **MANDATORY** and violations will cause build failures:

1. **NO wildcard imports** - Every import must be explicit
2. **NO generic exceptions** - Always use specific exception types *except* in infrastructure interceptors (see Infrastructure Interceptor Exception Pattern)
3. **JUnit 6 + AssertJ REQUIRED** - All tests must use JUnit 6.0.1 with AssertJ 3.27.3
4. **Version Catalog REQUIRED** - All versions in `gradle/libs.versions.toml`
5. **Zero violations** - ktlint, Detekt, and Konsist must pass without warnings

**Exception to Policy #2**: Infrastructure interceptors (metrics, logging, tracing) may catch generic `Exception` **only when**:
- Purpose is pure observability (record metrics/logs then re-throw)
- Exception is immediately re-thrown unchanged
- Pattern is documented with `@Suppress("TooGenericExceptionCaught")` and justification comment

### Import Management (ktlint Enforced)

```kotlin
// ✅ CORRECT - Explicit imports
import com.axians.eaf.framework.core.domain.AggregateRoot
import com.axians.eaf.framework.security.TenantContext
import org.springframework.stereotype.Service
import arrow.core.Either
import arrow.core.left
import arrow.core.right

// ❌ FORBIDDEN - Wildcard imports
import com.axians.eaf.framework.core.domain.*
import org.springframework.stereotype.*
import arrow.core.*
```

**Enforcement Configuration (.editorconfig):**
```
[*.kt]
ij_kotlin_name_count_to_use_star_import = 2147483647
ij_kotlin_name_count_to_use_star_import_for_members = 2147483647
```

### Exception Handling

```kotlin
// ✅ CORRECT - Domain-specific exceptions with Arrow Either
class WidgetService {
    fun createWidget(command: CreateWidgetCommand): Either<DomainError, Widget> = either {
        // Domain validation
        ensure(command.name.isNotBlank()) {
            DomainError.ValidationError(
                field = "name",
                constraint = "required",
                invalidValue = command.name
            )
        }

        Widget.create(command).bind()
    }
}

// ❌ FORBIDDEN - Generic exceptions
fun badExample() {
    throw Exception("Something went wrong")           // Generic exception
    throw RuntimeException("Error occurred")         // Generic runtime exception
    throw IllegalArgumentException("Bad input")      // Too generic for domain logic
}
```

### Infrastructure Interceptor Exception Pattern (LEGITIMATE)

```kotlin
// ✅ CORRECT - Infrastructure interceptor pattern (observability only)
@Component
class CommandMetricsInterceptor(
    private val meterRegistry: MeterRegistry
) : MessageHandlerInterceptor<CommandMessage<*>> {
    override fun handle(
        unitOfWork: UnitOfWork<out CommandMessage<*>>,
        interceptorChain: InterceptorChain
    ): Any {
        val start = Instant.now()
        return try {
            val result = interceptorChain.proceed()
            recordSuccess(start)
            result
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception  // LEGITIMATE: Observability only, immediate re-throw
        ) {
            recordFailure(start)
            throw ex  // CRITICAL: Exception propagates unchanged
        }
    }
}
```

**Requirements for legitimate generic catch:**
1. ✅ Purpose is observability/telemetry only
2. ✅ No exception handling logic - just instrumentation
3. ✅ Exception is immediately re-thrown unchanged
4. ✅ Pattern is clearly documented with `@Suppress` annotation and comment

## Testing Requirements

**For comprehensive testing strategy, see:** [Test Strategy](docs/architecture/test-strategy.md)

### Testing Philosophy - Constitutional TDD

1. **Test-First Development**: All production code must be preceded by failing tests (Red-Green-Refactor cycle)
2. **Integration-First Approach**: Integration tests for critical business flows
3. **Nullable Pattern**: Fast infrastructure substitutes for business logic
4. **Real Dependencies**: Testcontainers for stateful services (PostgreSQL, Keycloak, Redis)
5. **Zero-Mocks Policy**: Never mock business logic, only infrastructure

### JUnit 6 Critical Features & Breaking Changes (Released 2025-09-30)

**CRITICAL for Kotlin + @Nested Classes:**
- **Native Suspend Support**: Test and lifecycle methods can use `suspend fun` directly (no runBlocking wrapper needed)
- **@TestInstance(PER_CLASS) REQUIRED**: MUST add `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` to test classes using `suspend fun` with `@Nested` inner classes
- **Deterministic @Nested Ordering**: @Nested classes now have consistent discovery order (non-alphabetical but stable)
- **@TestMethodOrder Inheritance**: Ordering annotations inherited by @Nested classes recursively

**Breaking Changes from JUnit 5:**
- **Java 17 Baseline**: Minimum Java version raised from 8 to 17 (we use Java 21 ✅)
- **Kotlin 2.2 Baseline**: Minimum Kotlin version raised to 2.2 (we use 2.2.21 ✅)
- **Unified Versioning**: Platform + Jupiter share same version (6.0.1) - **CRITICAL: Must force Platform version to avoid conflicts**
- **CSV Parsing**: Migrated to FastCSV (stricter validation, auto line-separator detection)
- **Kotlin assertTimeout Contract**: Changed from EXACTLY_ONCE to AT_MOST_ONCE (may cause compilation errors)
- **Removed Modules**: junit-platform-runner, junit-platform-jfr removed

**CRITICAL - Force Platform Version:**
Transitive dependencies may bring older Platform versions (e.g., 1.12.2) causing `NoClassDefFoundError`. Always force Platform 6.0.1:

```kotlin
// In module build.gradle.kts (especially products/*)
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.junit.platform") {
            useVersion("6.0.1")
            because("JUnit 6 unified versioning - Platform + Jupiter must match")
        }
    }
}
```

Verify with: `./gradlew dependencies --configuration testRuntimeClasspath | grep junit-platform`

**Example - Suspend Functions with @Nested:**
```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)  // REQUIRED for suspend + @Nested
class MyIntegrationTest {
    @Test
    suspend fun `can use suspend directly`() {
        val result = suspendingService.fetchData()
        assertThat(result).isNotNull()
    }

    @Nested
    inner class `Async Scenarios` {
        @Test
        suspend fun `nested test with suspend`() {
            // JUnit 6 handles coroutine context automatically
        }
    }
}
```

**Resources:**
- Official Release Notes: https://docs.junit.org/6.0.0/release-notes/
- Kotlin Suspend Guide: https://patodev.pl/posts/junit6-suspend/

### 7-Layer Testing Defense (Architecture Mandate)

1. **Static Analysis**: ktlint, Detekt, Konsist (instant feedback)
2. **Unit Tests**: Business logic with Nullable Pattern (<10s execution)
3. **Integration Tests**: Testcontainers with real dependencies (<3min)
4. **Property-Based Tests**: JUnit 6 compatible property testing for invariants (nightly)
5. **Fuzz Testing**: Jazzer 0.25.1 for security vulnerabilities (nightly)
6. **Concurrency Tests**: LitmusKt for race conditions (Epic 8, nightly)
7. **Mutation Testing**: Pitest 1.19.0 for test effectiveness (nightly, 60-70% target)

### Spring Boot Integration Test Pattern (MANDATORY - Story 4.6)

**Use @Autowired field injection with JUnit 6**:

```kotlin
@SpringBootTest
@ActiveProfiles("test")
class WidgetIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `should create widget via REST API`() {
        // mockMvc available here
        mockMvc.perform(post("/api/widgets")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Test Widget"}"""))
            .andExpect(status().isCreated())
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // Start Testcontainers
            PostgresTestContainer.start()
            KeycloakTestContainer.start()

            registry.add("spring.datasource.url") { PostgresTestContainer.jdbcUrl }
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") {
                KeycloakTestContainer.issuerUri
            }
        }
    }
}
```

**Test Lifecycle Hooks**:

```kotlin
@BeforeEach
fun beforeEach() {
    // Runs before each test
}

@AfterEach
fun afterEach() {
    // Runs after each test
}

@BeforeAll
@JvmStatic
fun beforeAll() {
    // Runs once before all tests (must be in companion object)
}

@AfterAll
@JvmStatic
fun afterAll() {
    // Runs once after all tests (must be in companion object)
}
```

**CRITICAL**: Plugin order matters for product modules. In build.gradle.kts:
```kotlin
plugins {
    id("eaf.testing")     // FIRST - Testing setup (JUnit 6 + AssertJ)
    id("eaf.spring-boot") // SECOND - Spring Boot
}
```

## Spring Modulith Configuration

- **Kotlin Pattern**: Use `@ApplicationModule` classes instead of Java's `package-info.java`
- **ModuleMetadata**: Each module requires `@ApplicationModule` configuration
- **Dependency Rules**: Products depend on framework; framework never depends on products
- **Architectural Testing**: Konsist 0.17.3 verification of module boundaries

```kotlin
@ApplicationModule(
    displayName = "EAF Security Module",
    allowedDependencies = ["core", "shared.api", "shared.testing"]
)
class SecurityModule
```

## PostgreSQL Performance Requirements

**Mandatory Optimizations for Event Store (Architecture Decision #1):**
- **BRIN Indexes**: Time-based indexing for event streams
- **Time-based Partitioning**: Monthly partitions on domain_event_entry table
- **Snapshot Strategy**: Every 100 events (configurable)
- **Connection Pooling**: HikariCP with optimal settings
- **Performance Targets**: API p95 <200ms, event lag <10s, processor lag <10s

## Critical Implementation Anti-Patterns

### Testing Anti-Patterns (PROHIBITED)
- ❌ **Domain Logic Mocking**: Never mock business logic - only infrastructure
- ❌ **H2 Usage**: PostgreSQL Testcontainers only (H2 explicitly forbidden)
- ❌ **Security Mocking**: Real cryptography required, never mock JWT validation
- ❌ **Wildcard Assertions**: Use specific AssertJ assertions, not generic `isTrue()`/`isFalse()` when better options exist
- ❌ **Test Interdependence**: Tests must be independent and order-agnostic

### Code Anti-Patterns (PROHIBITED)
- ❌ **Wildcard Imports**: Every import must be explicit (ktlint enforced)
- ❌ **Generic Exceptions**: Always use specific exception types (except infrastructure interceptors)
- ❌ **Version Hardcoding**: Version Catalog mandatory
- ❌ **Behavioral Divergence**: Nullable implementations must preserve business logic

## Commit Standards

- **JIRA Integration**: Include issue numbers when applicable: `[DPCMSG-1234] Description`
- **Conventional Commits**: Use prefixes: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`
- **Atomic Commits**: Single focused change per commit
- **Quality Gates**: All commits must pass ktlint, Detekt, Konsist before merge
- **Git Hooks**: Pre-commit hooks enforced (Story 1.10) - **NEVER use --no-verify**

## Performance Monitoring KPIs

Monitor these continuously for system health:
- **Command Latency**: p95 <200ms threshold (Architecture Decision #10)
- **Event Processor Lag**: <10 seconds target
- **Test Execution**: Full suite <15min, unit tests <30s
- **Build Time**: Full build <3min, incremental <30s
- **Nullable Pattern Performance**: Target 100-1000x improvement over integration baseline

## Implementation Phase Guidance

**Current Phase:** Phase 4 - Implementation
**Current Epic:** Epic 1 - Foundation & Project Infrastructure (11 stories)
**Current Story:** Story 1.1 - Initialize Repository and Root Build System
**Next Command:** `/bmad:bmm:workflows:dev-story`

**Critical References:**
- Implementation Readiness Report: `docs/implementation-readiness-report-2025-11-01.md`
- Pre-Epic-1 Checklist: `docs/pre-epic-1-checklist.md` (all 4 actions complete)
- Workflow Status: `docs/bmm-workflow-status.md`

**Story Execution Pattern:**
1. Read story file: `docs/stories/epic-X/story-X.Y-<name>.md`
2. Review acceptance criteria, prerequisites, technical notes
3. Follow implementation checklist
4. Validate test evidence
5. Confirm Definition of Done
6. Update workflow status

## Where to Find More Information

### Quick Reference by Topic

**Planning & Requirements:**
- [Product Requirements (PRD)](docs/PRD.md) - 30 FRs, 3 NFRs, success criteria
- [Technical Specification](docs/tech-spec.md) - FR-to-Epic mapping
- [Epic Breakdown](docs/epics.md) - 10 epics with story sequencing
- [Implementation Stories](docs/sprint-artifacts/) - 112 implementation-ready stories

**Architecture & Design:**
- [Architecture Document](docs/architecture.md) - 89 decisions, version verification, complete structure
- [Coding Standards](docs/architecture/coding-standards.md) - Detailed Kotlin/Spring standards
- [Test Strategy](docs/architecture/test-strategy.md) - Constitutional TDD, 7-layer defense

**Implementation Support:**
- [Pre-Epic-1 Checklist](docs/pre-epic-1-checklist.md) - Pre-implementation validation results
- [Readiness Assessment](docs/implementation-readiness-report-2025-11-01.md) - Solutioning gate check (99% confidence)
- [Workflow Status](docs/bmm-workflow-status.md) - Current progress tracking

## Jira Integration

Commits may include Jira issue numbers in square brackets (e.g., "[DPCMSG-1234] feat: add widget aggregate"). Use the Jira MCP to lookup additional context when needed.

**CRITICAL**: Never run git commits with `--no-verify` - Git hooks are mandatory quality gates (Story 1.10).
