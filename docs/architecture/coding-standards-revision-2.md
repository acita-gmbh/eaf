# Coding Standards (Revision 2)

**Enforcement:** These rules are **automated architectural tests** enforced by **Konsist** and `ktlint`/`Detekt` in the CI build (Story 1.2).

## Core Principles

1. **Ubiquitous Language:** All code must use the precise language of the business domain.
2. **Hexagonal Enforcement:** Code within a `domain` module must **never** depend on code from an `adapter` module.
3. **Immutability:** All Commands, Events, and Queries must be immutable Kotlin `data class` or `data object`.
4. **Static Analysis:** All commits will be required to pass `ktlint` and `detekt` checks.
5. **Use Modern Spring APIs:** Developers should prefer modern APIs like `RestClient` over legacy ones like `RestTemplate`.

## Critical Architectural Rules

1. **Functional Error Handling (Arrow):** Domain MUST return `Either<Error, Success>`.
2. **Read Model Querying (jOOQ):** Read projections MUST use jOOQ.
3. **No Generic Exceptions:** Always use specific exception types.
4. **No Wildcard Imports:** Every import must be explicit.
5. **Version Catalog Required:** All dependencies via Gradle Version Catalog.
6. **No Mocks:** Use Testcontainers for stateful deps, Nullable Design Pattern for stateless.
7. **No H2:** PostgreSQL Testcontainers only.
8. **TDD Required:** Constitutional TDD (RED-GREEN-Refactor).

## Kotlin Language Standards

* **Kotlin Version**: 2.0.10 (pinned for tool compatibility)
* **No Wildcard Imports**: Every import must be explicit
* **Jakarta over javax**: Always use jakarta.* imports
* **No Generic Exceptions**: Always use specific exception types

## Spring Modulith with Kotlin Configuration

Module boundaries require Kotlin-specific configuration:

* Use `@PackageInfo` classes instead of Java's `package-info.java`
* Each module needs `ModuleMetadata` class with `@ApplicationModule`
* Use short module names in `allowedDependencies` (e.g., "domain") not full package names
* Domain module uses `Type.OPEN` for MVP while implementing proper DTO boundaries

## Known Issue: Detekt-Kotest BehaviorSpec Indentation

**Problem**: Detekt 1.23.7's `Indentation` rule incorrectly reports indentation errors in Kotest BehaviorSpec tests due to the DSL's lambda-in-constructor pattern.

**Solution Implemented**:

* Add `@file:Suppress("detekt:Indentation")` to BehaviorSpec test files
* Use separate `detekt-test.yml` configuration for test sources
* Dedicated `detektTest` task configured for test-specific rules

## Arrow Functional Programming Integration

* **Version**: Arrow 1.2.4 (core, fx-coroutines, optics)
* **Error Handling**: Use `Either<DomainError, T>` for domain operations
* **"Check and Throw" Pattern**: Required for Spring `@Transactional` compatibility
  * Internal: Use Either for functional error handling
  * Boundary: Convert Either.Left to exception for Spring rollback
* **Repository Adapters**: Wrap Spring Data repositories with functional adapters returning Either
* **Validation**: Use `ValidatedNel` for accumulating validation errors

## Multi-Tenancy Patterns

* **TenantContext**: Stack-based implementation with proper cleanup in finally blocks
* **3-Layer Enforcement**: Request filter, service validation, database interceptor
* **Context Propagation**: Must use Micrometer Context Propagation for async boundaries
* **Database Isolation**: Every query must be tenant-aware with `tenant_id` in all indexes
* **Fail-Closed Design**: Any missing or invalid tenant context results in immediate request rejection
* **Audit Trail**: All tenant violations must be logged with security alerts

## Security Testing Patterns

* **Security-Lite Profile**: Use for fast JWT/security tests without database dependencies
  * Activate with `@ActiveProfiles("test", "security-lite")`
  * Provides local RSA key generation for real cryptographic validation
  * 65% faster test execution by eliminating external dependencies
  * Maintains security integrity without Keycloak containers
* **Real Cryptography**: Always use real JWT signatures, never mock security components
* **10-Layer JWT Validation**: All layers must be tested with attack scenarios
* **Algorithm Enforcement**: RS256-only whitelist, explicit rejection of "none" algorithm

## Nullable Design Pattern Standards

The Nullable Design Pattern provides fast, reliable testing infrastructure for business logic while maintaining integration-first philosophy and preserving behavioral semantics.

### Factory Pattern Requirements

**Naming Convention**: All nullable implementations must follow the `createNull()` factory pattern:

```kotlin
// ✅ CORRECT - Factory pattern for nullable implementations
object AuditSinkFactory {
    fun createNull(config: NullableAuditConfig = NullableAuditConfig()): AuditSink =
        NullableAuditSink(config)
}

// ✅ CORRECT - Extension function pattern
fun AuditSink.Companion.createNull(): AuditSink = NullableAuditSink()
```

**Configuration Pattern**: Use sealed class configuration with conditional Spring beans:

```kotlin
// ✅ CORRECT - Conditional bean creation
@ConditionalOnProperty("eaf.testing.nullable.audit", havingValue = "true")
@ConditionalOnProperty("security.enabled", havingValue = "true", matchIfMissing = true)
class NullableAuditConfig : AuditSink
```

### Implementation Requirements

1. **Interface Compliance**: Nullable implementations MUST implement the same interfaces as production components
2. **State-Based Validation**: Provide inspection methods for test validation (e.g., `getRecordedEvents()`, `getState()`)
3. **Tenant Isolation**: All nullable implementations must preserve tenant context validation
4. **Error Simulation**: Support configurable failure modes for negative testing scenarios
5. **Performance Characteristics**: Target 70-80% performance improvement over integration tests

### Quality Gate Requirements

**MANDATORY for all nullable implementations**:

1. **Contract Tests**: Every nullable component must have contract tests proving behavioral parity with production implementation
2. **Tenant Isolation Verification**: Test cross-tenant access prevention in nullable context
3. **Performance Validation**: Measure and document performance improvements using baseline scripts
4. **Interface Compliance**: Automated verification that nullable and production classes implement identical interfaces

### Anti-Patterns (PROHIBITED)

**❌ Domain Logic Stubbing**: Never stub or mock business logic - only infrastructure adapters:

```kotlin
// ❌ WRONG - Mocking domain logic
val mockWidgetService = mockk<WidgetService> {
    every { validateWidget(any()) } returns Unit
}

// ✅ CORRECT - Nullable infrastructure, real domain logic
val nullableAuditSink = AuditSink.createNull()
val realWidgetService = WidgetService(nullableAuditSink)
```

**❌ Behavioral Divergence**: Nullable implementations must never change business logic behavior:

```kotlin
// ❌ WRONG - Changing validation behavior
class NullableValidator : Validator {
    override fun validate(input: String) = ValidationResult.Success // Always passes!
}

// ✅ CORRECT - Same validation logic, different storage
class NullableValidator : Validator {
    override fun validate(input: String) =
        if (input.isBlank()) ValidationResult.Error("Required")
        else ValidationResult.Success
}
```

**❌ Test Profile Mixing**: Never mix nullable and production beans in same test context:

```kotlin
// ❌ WRONG - Mixed infrastructure
@TestPropertySource(properties = [
    "eaf.testing.nullable.audit=true",
    "eaf.testing.nullable.eventstore=false"  // Mixing nullable and real
])

// ✅ CORRECT - Consistent nullable infrastructure
@TestPropertySource(properties = [
    "eaf.testing.nullable.audit=true",
    "eaf.testing.nullable.eventstore=true"
])
```

### Contract Testing Requirements

Every nullable component must include contract tests that verify identical behavior:

```kotlin
// ✅ REQUIRED - Contract test structure
abstract class AuditSinkContractTest : FunSpec() {
    abstract fun createAuditSink(): AuditSink

    init {
        test("should record audit events with correct tenant isolation") {
            val auditSink = createAuditSink()
            // Test identical behavior expectations
        }
    }
}

class NullableAuditSinkContractTestRunner : AuditSinkContractTest() {
    override fun createAuditSink() = AuditSink.createNull()
}

class HttpAuditSinkContractTestRunner : AuditSinkContractTest() {
    override fun createAuditSink() = HttpAuditSink(mockWebClient)
}
```

### Performance Regression Detection

**CI Integration Requirements**:
1. Baseline measurement before nullable implementation
2. Performance validation after conversion (target: 70-80% improvement)
3. Automated regression detection if performance degrades >10%
4. Documentation of performance gains for architectural decision records

-----
