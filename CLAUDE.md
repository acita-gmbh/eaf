# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the Enterprise Application Framework (EAF) v0.1 - a modern, Kotlin-based enterprise-grade framework designed for multi-tenant applications. The project is in its initial implementation phase with comprehensive architectural documentation defining the technical approach.

**Architecture Review Status**: GO (September 2025) - Proceed with implementation following comprehensive architectural patterns and quality standards.

## Architecture At A Glance

- **Style**: Hexagonal + Spring Modulith (Kotlin/JVM 21)
- **Domain**: CQRS + Event Sourcing via Axon Framework 4.9.4
- **Data**: PostgreSQL (event store + projections), jOOQ for read projections
- **Security**: Keycloak OIDC + 10-layer JWT validation, 3-layer tenant isolation
- **Multi-Tenancy**: Defense-in-depth with request filter, service validation, database RLS
- **Testing**: Kotest + Testcontainers, Nullable Design Pattern, Pitest mutation testing
- **DevEx**: Gradle monorepo, one-command onboarding, scaffolding CLI
- **Ops**: Docker Compose on-prem, multi-arch support (amd64, arm64, ppc64le)

See the full design in docs/architecture.md.

## Documentation Structure

**Sharded Architecture Documentation:**
- [`docs/architecture/`](docs/architecture/) - Individual architecture topics:
  - Tech Stack: `tech-stack.md`
  - Project Structure: `unified-project-structure.md`
  - Security: `security.md`
  - Testing Strategy: `test-strategy-and-standards-revision-3.md`
  - Coding Standards: `coding-standards-revision-2.md`
  - Development Workflow: `development-workflow.md`
  - Error Handling: `error-handling-strategy.md`
- [`docs/architecture.md`](docs/architecture.md) - Unified architecture document
- [`docs/prd/`](docs/prd/) - Product requirements documentation

**Rule of thumb**: Reference sharded files for detailed implementation, unified document for overview.

## Core Architecture Principles

- **Hexagonal Architecture with Spring Modulith** - Module boundaries programmatically verified with Konsist
- **CQRS/Event Sourcing with Axon Framework** - v4.9.4 current, v5 migration planned
- **Constitutional TDD** - Integration-first testing with hybrid nullable pattern approach
- **"No Mocks" Policy** - Testcontainers for stateful deps, Nullable Design Pattern for stateless
- **Quality-First Development** - Comprehensive quality gates with zero-violations policy

## Technology Stack

**For complete technology details, see:** [Tech Stack](docs/architecture/tech-stack.md)

### Core Technologies
- **Language**: Kotlin 2.0.10 (PINNED - critical constraint for tool compatibility)
- **Runtime**: JVM 21
- **Framework**: Spring Boot 3.3.5 (LOCKED for Spring Modulith 1.3.0 compatibility)
- **CQRS**: Axon Framework 4.9.4
- **Functional**: Arrow 1.2.4 for Either<Error,Success> domain error handling
- **Database**: PostgreSQL 16.1+ with mandatory optimizations (BRIN indexes, partitioning)
- **Build**: Gradle 9.1.0 monorepo with Version Catalogs (upgraded for Kotest 6.0.3 compatibility)
- **Testing**: Kotest 6.0.3 + Testcontainers integration-first approach

### Quality & Development Tools
- **Formatting**: ktlint 1.4.0 (enforced, zero violations)
- **Static Analysis**: Detekt 1.23.7 (enforced, zero violations)
- **Architecture Testing**: Konsist 0.17.3 (boundary verification)
- **Mutation Testing**: Pitest 1.19.0-rc.1 (80% minimum coverage, Gradle 9 compatible)
- **API Documentation**: Dokka 1.9.10

## Critical Kotlin Standards

**For complete coding standards, see:** [Coding Standards](docs/architecture/coding-standards-revision-2.md)

### MUST Follow
- **NO wildcard imports** - Every import must be explicit
- **NO generic exceptions** - Always use specific exception types
- **Use Kotest, NEVER JUnit** - All tests must use Kotest framework
- **CRITICAL**: Never mix JUnit and Kotest annotations - JUnit `@Disabled` has no effect on Kotest tests
- **Integration tests preferred** - Use Testcontainers for real dependencies
- **Version Catalog Required** - All versions in `gradle/libs.versions.toml`
- **Use Arrow for error handling** - Either<Error,Success> in domain, "check and throw" at boundaries
- **Spring Modulith compliance** - ModuleMetadata classes with @ApplicationModule

### Advanced Patterns
- **Nullable Design Pattern**: Fast infrastructure substitutes with factory pattern (`createNull()`)
- **Multi-Tenancy**: 3-layer enforcement with context propagation
- **Security Testing**: Security-lite profile for fast JWT tests (65% faster execution)

## Project Structure

```
eaf-monorepo/
├── framework/                   # Core framework modules (libraries only)
│   ├── core/                   # Core domain patterns
│   ├── security/               # 10-layer JWT validation, tenant isolation
│   ├── cqrs/                   # CQRS/ES with Axon
│   ├── observability/          # Metrics, logging, tracing
│   ├── workflow/               # Flowable BPMN integration
│   ├── persistence/            # jOOQ adapters, projections
│   └── web/                    # REST controllers, global advice
├── products/                   # Deployable Spring Boot applications
│   └── licensing-server/       # Epic 8 - First product implementation
├── shared/                     # Shared code
│   ├── shared-api/             # Axon commands, events, queries
│   ├── shared-types/           # TypeScript interfaces
│   └── testing/                # Test utilities, nullable implementations
├── apps/                       # Frontend applications
│   └── admin/                  # React-Admin portal
├── build-logic/                # Gradle convention plugins
├── config/                     # Configuration (detekt, konsist)
├── scripts/                    # Development scripts
└── docs/                       # Documentation
```

## Testing Requirements

**For comprehensive testing strategy, see:** [Test Strategy](docs/architecture/test-strategy-and-standards-revision-3.md)

### Framework & Philosophy
- **Primary**: Kotest (FunSpec, BehaviorSpec) - **JUnit forbidden**
- **Core Strategy**: Hybrid approach - 40-50% fast logic tests (nullable pattern), 30-40% critical integration, 10-20% E2E
- **Nullable Pattern**: 61.6% performance improvement for business logic testing
- **Security-Lite Profile**: 65% faster security tests without external dependencies
- **Coverage**: 85% line coverage, 80% mutation coverage minimum
- **Anti-Patterns**: Comprehensive list of prohibited approaches

### Critical Testing Rules
- **NEVER mix JUnit and Kotest annotations** - JUnit `@Disabled` is completely ignored by Kotest
- **Use Testcontainers for stateful dependencies** - PostgreSQL, Redis, Keycloak
- **Use Nullable Design Pattern for stateless dependencies** - Infrastructure adapters
- **H2 explicitly forbidden** - PostgreSQL Testcontainers only
- **Container timing**: Use companion object init blocks with explicit start() calls

### Nullable Design Pattern
- **Factory Pattern**: `createNull()` convention for all nullable implementations
- **Purpose**: Fast domain testing with real business logic, stubbed infrastructure
- **Contract Testing**: Mandatory behavioral parity validation
- **Performance**: Target 50-70% improvement over integration test baseline

## Quality Tools & Enforcement

**For complete tooling details, see:** [Tech Stack](docs/architecture/tech-stack.md)

- **Static Analysis**: ktlint, detekt with zero violations policy
- **Architecture Compliance**: Konsist rules for module boundaries and coding standards
- **Security Scanning**: OWASP Dependency Check, SAST rules
- **Test Coverage**: 85% line coverage, 80% mutation coverage
- **Performance Regression**: Container timing baselines, nullable pattern validation

## Multi-Tenancy

**For detailed patterns, see:** [Coding Standards - Multi-Tenancy](docs/architecture/coding-standards-revision-2.md#multi-tenancy-patterns)

- **3-Layer Enforcement**: Request filter → Service validation → Database interceptor
- **Context Propagation**: Micrometer Context Propagation for async boundaries
- **Database Isolation**: PostgreSQL RLS with tenant_id in all indexes
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
        "Access denied: tenant context mismatch" // Generic (CWE-209 protection)
    }
    apply(WidgetCreatedEvent(...))
}

@CommandHandler
fun handle(command: UpdateWidgetCommand): Either<WidgetError, Unit> {
    val currentTenant = TenantContext().getCurrentTenantId()
    // Validate command AND aggregate tenant match
    when {
        command.tenantId != currentTenant -> return TenantIsolationViolation(...).left()
        this.tenantId != currentTenant -> return TenantIsolationViolation(...).left()
    }
    apply(WidgetUpdatedEvent(...))
}
```

## Security Implementation

**For comprehensive security details, see:** [Security](docs/architecture/security.md)

### 10-Layer JWT Validation System
1. **Format Validation** - JWT structure verification
2. **Signature Validation** - Real cryptographic verification (RS256)
3. **Algorithm Validation** - Prevent algorithm confusion attacks
4. **Claim Schema Validation** - Required claims enforcement
5. **Time-based Validation** - exp/iat/nbf with clock skew tolerance
6. **Issuer/Audience Validation** - Trust boundary enforcement
7. **Token Revocation Check** - Redis blacklist verification
8. **Role Validation** - Role whitelist and privilege escalation detection
9. **User Validation** - User existence and active status
10. **Injection Detection** - SQL injection, XSS, JNDI attack patterns

### 3-Layer Tenant Isolation
- **Layer 1**: Request filter extracts tenant from JWT
- **Layer 2**: Service boundary validation with AOP
- **Layer 3**: Database interceptor with automatic tenant filtering

### Emergency Security Recovery
- 5-phase recovery process (0-120 hours)
- Automated security validation suite (43+ tests)
- ASVS compliance restoration within 5 days

## Development Workflow

**For complete workflow details, see:** [Development Workflow](docs/architecture/development-workflow.md)

### One-Command Onboarding
```bash
git clone <repository>
cd eaf-monorepo
./scripts/init-dev.sh
```

### Daily Development Commands
```bash
# Infrastructure services
./scripts/init-dev.sh

# Backend development
./gradlew :products:licensing-server:bootRun

# Frontend development
npm run dev --prefix apps/admin

# Quality checks
./gradlew clean build                    # Full quality check
./gradlew test -P fastTests=true        # Fast feedback cycle
./gradlew verifyAllModules              # Architecture compliance
```

### Scaffolding CLI
```bash
# Generate module structure
eaf scaffold module <boundedContext>:<module>

# Generate CQRS aggregate
eaf scaffold aggregate <Domain> --events <Created,Updated>

# Generate API resource
eaf scaffold api-resource <Domain> --path /api/widgets

# Generate React-Admin resource
eaf scaffold ra-resource <Domain> --fields id,name,status
```

## Error Handling Strategy

**For complete error handling details, see:** [Error Handling](docs/architecture/error-handling-strategy.md)

**Arrow-Fold-Throw-ProblemDetails Pattern:**
1. **Domain**: Returns `Either.Left(DomainError)`
2. **Controller**: Folds Either, converts to HttpException
3. **Global Advice**: Formats as RFC 7807 ProblemDetail
4. **Frontend**: Parses problem+json for error display

**Features:**
- Comprehensive error catalog by category
- Context enrichment (traceId, tenantId)
- Structured logging and metrics
- Frontend integration patterns

## API Strategy

- **REST/OpenAPI** for React-Admin frontend
- **CQRS Pattern** with Axon for core logic
- **RFC 7807 Problem Details** for error responses
- **GraphQL Gateway** (Post-MVP) for flexible querying

## Version Management

### Critical Version Constraints
**For complete compatibility matrix, see:** [Tech Stack](docs/architecture/tech-stack.md)

- **Kotlin 2.0.10 (PINNED)** - Critical constraint for tool compatibility (ktlint 1.4.0, detekt 1.23.7)
- **Spring Boot 3.3.5 (LOCKED)** - Required for Spring Modulith 1.3.0 compatibility
- **Axon Framework 4.9.4** - Current stable, v5 migration planned before production
- **Gradle 9.1.0** - Required for Kotest 6.0.3 (embeds Kotlin 2.2.0 needed by Kotest)
- **Kotest 6.0.3** - Testing framework with hybrid runner approach (native + JUnit Platform)

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
    kotlin("jvm") version "2.0.10"  // FORBIDDEN
}
```

## Spring Modulith Configuration

**For detailed configuration, see:** [Project Structure](docs/architecture/unified-project-structure.md)

- **Kotlin Pattern**: Use `@PackageInfo` classes instead of Java's `package-info.java`
- **ModuleMetadata**: Each module requires `@ApplicationModule` configuration
- **Dependency Rules**: Products depend on framework; framework never depends on products
- **Architectural Testing**: Konsist verification of module boundaries

```kotlin
@ApplicationModule(
    displayName = "EAF Security Module",
    allowedDependencies = ["core", "shared.api", "shared.testing"]
)
class SecurityModule
```

## PostgreSQL Performance Requirements

**Mandatory Optimizations for Event Store:**
- **BRIN Indexes**: Time-based indexing for event streams
- **Time-based Partitioning**: Monthly/quarterly partitions
- **Autovacuum Tuning**: Optimized for high-write workloads
- **Connection Pooling**: PgBouncer or equivalent
- **KPIs**: p95 latency <200ms, processor lag <30s, conflict rate <1%

## Critical Implementation Anti-Patterns

### Testing Anti-Patterns (PROHIBITED)
- ❌ **JUnit/Kotest Mixing**: JUnit annotations completely ignored by Kotest
- ❌ **Domain Logic Mocking**: Never mock business logic - only infrastructure
- ❌ **H2 Usage**: PostgreSQL Testcontainers only
- ❌ **Security Mocking**: Real cryptography required, never mock security

### Code Anti-Patterns (PROHIBITED)
- ❌ **Wildcard Imports**: Every import must be explicit
- ❌ **Generic Exceptions**: Always use specific exception types
- ❌ **Version Hardcoding**: Version Catalog mandatory
- ❌ **Behavioral Divergence**: Nullable implementations must preserve business logic

## Commit Standards

- Include JIRA issue numbers: `[DPCMSG-1234] Description`
- Use conventional commit format when applicable
- Atomic commits focused on single changes
- All commits must pass quality gates before merge

## Performance Monitoring KPIs

Monitor these continuously for system health:
- **Command Latency**: p95 <200ms threshold
- **Event Processor Lag**: <30 seconds acceptable
- **Concurrency Conflicts**: <1% conflict rate
- **Test Execution**: Target 65% improvement with optimizations
- **Build Time**: <10 seconds incremental, <2 minutes full

## Migration Notes (2025-01)

### Gradle 9.1.0 and Kotest 6.0.3 Migration

**Key Changes**:
1. **Gradle 8.14 → 9.1.0**: Upgraded to resolve Kotlin version mismatch
   - Gradle 9.1.0 embeds Kotlin 2.2.0 (required by Kotest 6.0.3)
   - Fixes `NoSuchMethodError: kotlin.time.Clock` issues

2. **Kotest Plugin Hybrid Approach**:
   - Main tests: Native `jvmKotest` task (automatic)
   - Custom source sets: `Test` tasks with `useJUnitPlatform()` (manual)
   - Requires `kotest-runner-junit5-jvm` for custom source sets only

3. **Dependency Fixes**:
   - `kotest-extensions-pitest`: GroupId changed from `io.kotest.extensions` to `io.kotest`
   - Pitest plugin: Updated to 1.19.0-rc.1 for Gradle 9 compatibility

**Migration Commands**:
```bash
# Update Gradle wrapper
./gradlew wrapper --gradle-version 9.1.0

# Run tests with new setup
./gradlew jvmKotest          # Main tests (native runner)
./gradlew integrationTest    # Integration tests (JUnit Platform)
./gradlew konsistTest        # Architecture tests (JUnit Platform)
./gradlew check              # All tests and quality gates
```

## Where to Find More Information

### Quick Reference by Topic

**Architecture & Design:**
- [High Level Architecture](docs/architecture/high-level-architecture.md)
- [Components](docs/architecture/components.md)
- [Project Structure](docs/architecture/unified-project-structure.md)
- [Tech Stack](docs/architecture/tech-stack.md)

**Development & Quality:**
- [Development Workflow](docs/architecture/development-workflow.md)
- [Coding Standards](docs/architecture/coding-standards-revision-2.md)
- [Test Strategy](docs/architecture/test-strategy-and-standards-revision-3.md)
- [Error Handling](docs/architecture/error-handling-strategy.md)

**Security & Operations:**
- [Security](docs/architecture/security.md)
- [Monitoring & Observability](docs/architecture/monitoring-and-observability.md)
- [Deployment Architecture](docs/architecture/deployment-architecture-revision-2.md)
- [Operational Playbooks](docs/architecture/operational-playbooks.md)

**Data & Integration:**
- [Data Models](docs/architecture/data-models.md)
- [Database Schema](docs/architecture/database-schema.md)
- [API Specification](docs/architecture/api-specification-revision-2.md)
- [External APIs](docs/architecture/external-apis.md)

## Jira Integration

Commits may include Jira issue numbers in square brackets (e.g., "[DPCMSG-1234] server: fixed xyz"). Use the Jira MCP to lookup additional context when needed.