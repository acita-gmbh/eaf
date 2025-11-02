# Epic Technical Specification: Foundation & Project Infrastructure

Date: 2025-11-02
Author: Wall-E
Epic ID: 1
Status: Draft

---

## Overview

Epic 1 establishes the complete foundational infrastructure for EAF v1.0 by creating a production-ready Gradle 9.1.0 multi-module monorepo with Spring Modulith 1.4.4 boundary enforcement, convention plugins for consistent build configuration, a comprehensive Docker Compose development stack with one-command initialization, DDD base classes for domain modeling, and comprehensive CI/CD pipelines with Git hooks for quality enforcement. This epic delivers the architectural foundation that enables all subsequent epic development (Epics 2-10) with programmatic consistency enforcement from inception, eliminating 4-6 weeks of typical setup time.

## Objectives and Scope

**In Scope:**
- Gradle 9.1.0 monorepo with Kotlin DSL and multi-module structure (framework/, products/, shared/, apps/, tools/)
- Convention plugins in build-logic/ for consistent configuration (kotlin-common, spring-boot, quality-gates, testing)
- Version catalog (gradle/libs.versions.toml) with 28+ managed dependencies
- Spring Modulith 1.4.4 module boundary enforcement
- Docker Compose stack (PostgreSQL 16.10, Keycloak 26.4.2, Redis 7.2, Prometheus, Grafana 12.2)
- One-command initialization script (./scripts/init-dev.sh) with health checks and seed data
- DDD base classes (AggregateRoot, Entity, ValueObject, DomainEvent) in framework/core
- Common types (Money, Quantity, Identifier) and exception hierarchy
- CI/CD pipelines: Fast (<15min), Nightly (~2.5h), Security Review, Hook Validation
- Git hooks: Pre-commit (ktlint <5s), Pre-push (Detekt + tests <30s)
- Foundation documentation and project README

**Out of Scope:**
- Domain aggregates (Epic 2+)
- Security implementation (Epic 3)
- Multi-tenancy (Epic 4)
- Observability infrastructure (Epic 5)
- Workflow orchestration (Epic 6)
- Scaffolding CLI (Epic 7)
- Code quality audits (Epic 8)
- Documentation beyond foundation (Epic 9)
- Reference application (Epic 10)

## System Architecture Alignment

Epic 1 implements architecture.md Section 3 (Project Initialization) by reusing the validated prototype structure rather than standard starters. The foundation aligns with:

**Core Technology Stack (architecture.md Section 2):**
- Kotlin 2.2.21 (current stable), JVM 21 LTS
- Spring Boot 3.5.7 (current GA), Spring Modulith 1.4.4
- Gradle 9.1.0 with Kotlin DSL and Version Catalog pattern

**Testing & Quality Stack (architecture.md Section 4):**
- Kotest 6.0.4 (primary testing framework, JUnit explicitly forbidden)
- ktlint 1.7.1, Detekt 1.23.8, Konsist 0.17.3, Kover 0.9.3
- Pitest 1.19.0 (mutation testing), Jazzer 0.25.1 (fuzz testing - configured, executed in Epic 8+)
- Testcontainers 1.21.3 for integration tests

**Infrastructure Stack (architecture.md Section 2.3):**
- Docker Compose 2.40.3 for local development
- PostgreSQL 16.10, Keycloak 26.4.2, Redis 7.2
- Prometheus (Micrometer 1.15.5), Grafana 12.2

**Constraints:**
- Zero-Tolerance Policies: No wildcard imports, no generic exceptions (except infrastructure interceptors), Kotest-only, Version Catalog required
- Constitutional TDD: All production code must be test-first (Red-Green-Refactor enforced by Git hooks)
- 7-Layer Testing Defense: Static → Unit → Integration → Property → Fuzz → Concurrency → Mutation
- Quality Gates: Zero violations policy (ktlint, Detekt, Konsist must pass)

## Detailed Design

### Services and Modules

| Module | Responsibility | Inputs | Outputs | Owner |
|--------|---------------|--------|---------|-------|
| **build-logic/** | Gradle convention plugins for consistent build configuration | Version catalog, module structure | Applied plugins (kotlin-common, spring-boot, quality-gates, testing) | Build System |
| **framework/core** | DDD base classes, domain primitives, exception hierarchy | None (foundation) | AggregateRoot, Entity, ValueObject, DomainEvent, Money, Quantity, Identifier | Framework Core |
| **framework/security** | Security module placeholder (implemented Epic 3) | None | Module structure | Framework Security |
| **framework/multi-tenancy** | Multi-tenancy module placeholder (implemented Epic 4) | None | Module structure | Framework Multi-Tenancy |
| **framework/cqrs** | CQRS module placeholder (implemented Epic 2) | None | Module structure | Framework CQRS |
| **framework/persistence** | Persistence module placeholder (implemented Epic 2) | None | Module structure | Framework Persistence |
| **framework/observability** | Observability module placeholder (implemented Epic 5) | None | Module structure | Framework Observability |
| **framework/workflow** | Workflow module placeholder (implemented Epic 6) | None | Module structure | Framework Workflow |
| **framework/web** | Web module placeholder (implemented Epic 2) | None | Module structure | Framework Web |
| **shared/shared-api** | Shared API contracts placeholder | None | Module structure | Shared API |
| **shared/shared-types** | Shared types placeholder | None | Module structure | Shared Types |
| **shared/testing** | Test utilities placeholder | None | Module structure | Shared Testing |
| **products/widget-demo** | Reference implementation placeholder (Epic 2+) | None | Module structure | Widget Demo |
| **apps/admin** | shadcn-admin-kit portal placeholder (Epic 7+) | None | Module structure | Admin Portal |
| **tools/eaf-cli** | Scaffolding CLI placeholder (Epic 7) | None | Module structure | EAF CLI |
| **docker/** | Docker Compose stack configurations | None | docker-compose.yml, service configs | Infrastructure |
| **scripts/** | Development automation scripts | None | init-dev.sh, health-check.sh, seed-data.sh, install-git-hooks.sh | DevOps |
| **.github/workflows/** | CI/CD pipeline definitions | Code changes, commits | Test results, build artifacts | CI/CD |

### Data Models and Contracts

**DDD Base Classes (framework/core/domain/):**

```kotlin
// AggregateRoot.kt
abstract class AggregateRoot<T : Identifier>(
    val id: T
) {
    private val domainEvents = mutableListOf<DomainEvent>()

    protected fun registerEvent(event: DomainEvent) {
        domainEvents.add(event)
    }

    fun clearEvents() = domainEvents.clear()
}

// Entity.kt
abstract class Entity<T : Identifier>(
    open val id: T
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Entity<*>) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

// ValueObject.kt
interface ValueObject  // Marker interface for immutable value objects

// DomainEvent.kt
interface DomainEvent  // Marker interface for domain events
```

**Common Types (framework/core/common/types/):**

```kotlin
// Money.kt
data class Money(
    val amount: BigDecimal,
    val currency: Currency = Currency.getInstance("EUR")
) : ValueObject {
    init {
        require(amount.scale() <= 2) { "Money amount must have max 2 decimal places" }
    }
}

// Quantity.kt
data class Quantity(
    val value: BigDecimal,
    val unit: String
) : ValueObject {
    init {
        require(value >= BigDecimal.ZERO) { "Quantity must be non-negative" }
    }
}

// Identifier.kt
interface Identifier {
    val value: String
}
```

**Exception Hierarchy (framework/core/common/exceptions/):**

```kotlin
// EafException.kt
open class EafException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

// ValidationException.kt
class ValidationException(
    val field: String,
    val constraint: String,
    val invalidValue: Any?
) : EafException("Validation failed for field '$field': $constraint")

// TenantIsolationException.kt
class TenantIsolationException(
    message: String = "Tenant isolation violation"
) : EafException(message)

// AggregateNotFoundException.kt
class AggregateNotFoundException(
    val aggregateType: String,
    val aggregateId: String
) : EafException("$aggregateType with id $aggregateId not found")
```

**Version Catalog Schema (gradle/libs.versions.toml):**

```toml
[versions]
kotlin = "2.2.21"
spring-boot = "3.5.7"
spring-modulith = "1.4.4"
axon = "4.12.1"
postgresql = "16.10"
# ... 28+ managed versions

[libraries]
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter", version.ref = "spring-boot" }
# ... library definitions

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
# ... plugin definitions
```

### APIs and Interfaces

**Convention Plugin Interfaces (build-logic/src/main/kotlin/):**

```kotlin
// eaf.kotlin-common.gradle.kts
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    implementation(platform(libs.kotlin.bom))
}

// eaf.spring-boot.gradle.kts
plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.modulith.starter.core)
}

// eaf.quality-gates.gradle.kts
plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

ktlint {
    version.set(libs.versions.ktlint)
    ignoreFailures.set(false)  // Zero violations policy
}

detekt {
    ignoreFailures = false  // Zero violations policy
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

// eaf.testing.gradle.kts
plugins {
    id("io.kotest")
}

dependencies {
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()  // Kotest runs on JUnit Platform
}
```

**Script Interfaces (scripts/):**

```bash
# init-dev.sh
# Entry point: ./scripts/init-dev.sh
# Exit codes:
#   0 - Success
#   1 - Docker not available
#   2 - Services failed to start
#   3 - Health checks failed

# health-check.sh
# Entry point: ./scripts/health-check.sh
# Validates: PostgreSQL, Keycloak, Redis, Prometheus, Grafana
# Exit codes:
#   0 - All services healthy
#   1 - One or more services unhealthy

# seed-data.sh
# Entry point: ./scripts/seed-data.sh
# Loads: Keycloak users, sample tenants, test data
# Exit codes:
#   0 - Data seeded successfully
#   1 - Seeding failed

# install-git-hooks.sh
# Entry point: ./scripts/install-git-hooks.sh
# Installs: pre-commit (ktlint), pre-push (Detekt + tests)
# Exit codes:
#   0 - Hooks installed
#   1 - Installation failed
```

**CI/CD Pipeline Interfaces (.github/workflows/):**

- **ci.yml**: Triggered on [push to main, pull_request], runs in <15min, outputs: test results, build artifacts
- **nightly.yml**: Triggered on [schedule: "0 2 * * *"], runs ~2.5h, outputs: property test results, fuzz findings, mutation scores
- **security-review.yml**: Triggered on [push to main, schedule: weekly], runs OWASP dependency check, outputs: vulnerability report
- **validate-hooks.yml**: Triggered on [push], validates Git hooks match CI requirements, outputs: validation status

### Workflows and Sequencing

**Development Environment Setup Sequence:**

```
Developer Onboarding Flow:
1. Clone Repository → 2. Run init-dev.sh → 3. Wait for Services → 4. Verify Health Checks → 5. Run ./gradlew build → 6. Start Development

init-dev.sh Workflow:
1. Check Docker availability
2. Start Docker Compose (docker-compose up -d)
3. Wait for PostgreSQL (port 5432)
4. Wait for Keycloak (port 8080, realm endpoint check)
5. Wait for Redis (port 6379, PING command)
6. Wait for Prometheus (port 9090)
7. Wait for Grafana (port 3000)
8. Run Flyway migrations (PostgreSQL schema setup)
9. Execute seed-data.sh (Keycloak users, test data)
10. Install Git hooks (install-git-hooks.sh)
11. Download Gradle dependencies (./gradlew dependencies)
12. Report success/failure with clear error messages
13. Display service URLs and credentials
```

**Story Sequencing (11 Stories, Sequential):**

```
Story 1.1: Initialize Repository
   ↓ (Foundation required)
Story 1.2: Create Multi-Module Structure
   ↓ (Structure required)
Story 1.3: Implement Convention Plugins
   ↓ (Plugins required)
Story 1.4: Create Version Catalog
   ↓ (Versions required)
Story 1.5: Docker Compose Stack
   ↓ (Infrastructure required)
Story 1.6: One-Command Init Script
   ↓ (Environment required)
Story 1.7: DDD Base Classes
   ↓ (Domain foundation independent)
Story 1.8: Spring Modulith Enforcement
   ↓ (Modules + base classes required)
Story 1.9: CI/CD Pipeline
   ↓ (Build + tests required)
Story 1.10: Git Hooks
   ↓ (Quality gates required)
Story 1.11: Foundation Documentation
   ↓ (All foundation complete)
Done: Foundation Ready for Epic 2
```

**CI/CD Pipeline Sequencing:**

```
PR Workflow (ci.yml):
1. Checkout code
2. Setup JDK 21
3. Gradle cache restore
4. ./gradlew ktlintCheck (formatting)
5. ./gradlew detektCheck (static analysis)
6. ./gradlew test (unit tests)
7. ./gradlew integrationTest (Testcontainers)
8. ./gradlew check (Konsist architecture validation)
9. Publish test results
10. Upload build artifacts
11. Gradle cache save
Target: <15 minutes

Nightly Workflow (nightly.yml):
1-8. Same as PR workflow
9. ./gradlew propertyTest (Kotest property tests)
10. ./gradlew fuzzTest (Jazzer targets × 5min)
11. ./gradlew pitest (mutation testing)
12. ./gradlew koverHtmlReport (coverage report)
13. Upload comprehensive test reports
Target: ~2.5 hours
```

## Non-Functional Requirements

### Performance

**Build Performance Targets (FR011):**
- Full test suite execution: <15 minutes (CI/CD pipeline)
- Unit tests only: <30 seconds
- Full build (clean build): <3 minutes
- Incremental build: <30 seconds

**Development Feedback Loop:**
- ktlint check (pre-commit hook): <5 seconds
- Detekt + unit tests (pre-push hook): <30 seconds
- Local build verification: <3 minutes

**Infrastructure Startup Performance:**
- Docker Compose stack startup: <2 minutes
- init-dev.sh complete execution: <5 minutes (including migrations, seed data, dependencies)
- Health checks complete: <30 seconds

**Quality Gate Performance:**
- ktlint format check: <5 seconds
- Detekt static analysis: <10 seconds
- Konsist architecture validation: <5 seconds
- Kover coverage report generation: <10 seconds

### Security

**Build System Security (NFR002):**
- Version Catalog enforcement: All versions centralized, no hardcoded dependencies
- OWASP Dependency Check integration: security-review.yml workflow (weekly)
- Zero critical vulnerabilities in production dependencies
- Gradle dependency verification (checksums)

**Git Hooks Security:**
- Pre-commit hook validation: Prevents commits without formatting checks
- Pre-push hook validation: Prevents pushes without tests passing
- Hook bypass logging: --no-verify usage tracked and discouraged

**CI/CD Security:**
- Secrets management: GitHub Secrets for credentials, no hardcoded secrets
- Dependency scanning: OWASP Dependency Check in security-review.yml
- Build artifact integrity: Gradle checksums, signed artifacts (future)

**Code Quality Security:**
- Detekt security rules: Enabled for security anti-patterns
- Konsist boundary enforcement: Prevents unauthorized module access
- Zero violations policy: All quality gates must pass (no exceptions)

**Documentation Security:**
- Customer due diligence support: Security documentation for NFR002 compliance
- Audit-ready posture: Comprehensive documentation for ISO 27001/NIS2 (Epic 3+)

### Reliability/Availability

**Build System Reliability:**
- Gradle daemon stability: Automatic recovery on failures
- Dependency resolution: Retry logic with exponential backoff
- Build cache: Gradle remote cache for CI/CD consistency
- Parallel execution: Safe parallel test execution (Kotest)

**Development Stack Reliability:**
- Docker Compose health checks: Automatic service restart on failures
- PostgreSQL connection pooling: HikariCP with optimal settings (Epic 2)
- Service dependencies: Ordered startup (PostgreSQL → Keycloak → Redis)
- Graceful degradation: Services can start independently

**CI/CD Reliability:**
- Workflow retry logic: Automatic retry on transient failures
- Test stability: Flaky test detection and quarantine (Epic 8)
- Artifact upload: Retry on network failures
- Cache reliability: Fallback to fresh build if cache corrupt

**Quality Gate Reliability:**
- Deterministic results: All quality tools produce consistent results
- No false positives: Quality rules tuned to minimize noise
- Clear failure diagnostics: Actionable error messages
- Automatic fixes: ktlint can auto-format (./gradlew ktlintFormat)

### Observability

**Build Observability:**
- Gradle build scans: Performance insights and failure diagnostics
- Test reports: HTML reports with detailed test execution data
- Quality gate reports: ktlint, Detekt, Konsist, Kover reports in CI/CD artifacts
- Build duration tracking: Gradle task execution times

**CI/CD Observability:**
- Workflow execution logs: GitHub Actions logs with timestamps
- Test result artifacts: JUnit XML reports for CI/CD dashboards
- Coverage reports: Kover HTML and XML reports
- Mutation test reports: Pitest HTML reports (nightly)

**Development Stack Observability (Foundation for Epic 5):**
- Prometheus: Configured and running (metrics collection in Epic 5+)
- Grafana: Configured and running (dashboards in Epic 5+)
- Service health endpoints: PostgreSQL, Keycloak, Redis health checks
- Docker Compose logs: Centralized logging via docker-compose logs

**Quality Metrics:**
- Code coverage trending: Kover reports tracked over time
- Violation tracking: ktlint, Detekt violation counts
- Test execution trends: Test duration and stability metrics
- Build performance metrics: Build time, cache hit rates

## Dependencies and Integrations

**Core Dependencies (gradle/libs.versions.toml):**

```toml
[versions]
kotlin = "2.2.21"
spring-boot = "3.5.7"
spring-modulith = "1.4.4"
axon = "4.12.1"
postgresql = "16.10"
jooq = "3.20.8"
keycloak = "26.4.2"
flowable = "7.2.0"
gradle = "9.1.0"

# Testing & Quality
kotest = "6.0.4"
testcontainers = "1.21.3"
jazzer = "0.25.1"
pitest = "1.19.0"
ktlint = "1.7.1"
detekt = "1.23.8"
konsist = "0.17.3"
kover = "0.9.3"

# Infrastructure
redis = "7.2"
prometheus-micrometer = "1.15.5"
opentelemetry = "1.55.0"
opentelemetry-instrumentation = "2.20.1"
logback = "1.5.19"
grafana = "12.2"

# Developer Tools
picocli = "4.7.7"
mustache = "0.9.14"
springdoc-openapi = "2.6.0"
dokka = "2.1.0"

# Build Tools
docker-compose = "2.40.3"
```

**External Service Dependencies:**

| Service | Version | Purpose | Configuration | Health Check |
|---------|---------|---------|---------------|--------------|
| **PostgreSQL** | 16.10 | Event store, projections | docker/postgres/postgresql.conf | Port 5432, SELECT 1 query |
| **Keycloak** | 26.4.2 | Identity provider (OIDC) | docker/keycloak/realm-export.json | Port 8080, /realms/eaf endpoint |
| **Redis** | 7.2 | Cache, session store | docker/redis/redis.conf | Port 6379, PING command |
| **Prometheus** | Latest | Metrics collection | monitoring/prometheus.yml | Port 9090, /-/healthy endpoint |
| **Grafana** | 12.2 | Dashboards (optional) | monitoring/grafana/datasources.yml | Port 3000, /api/health endpoint |

**Build Tool Dependencies:**

- **Gradle Wrapper**: 9.1.0 (included in repository, gradlew/gradlew.bat)
- **JDK**: 21 LTS (required on developer machine, Gradle Toolchain downloads if missing)
- **Docker**: 20.10+ or Podman 4.0+ (for Docker Compose stack)
- **Git**: 2.30+ (for version control and Git hooks)

**Development Dependencies:**

- **IDE**: IntelliJ IDEA 2024.2+ recommended (Kotlin support, Spring Boot support)
- **Shell**: bash/zsh (for init-dev.sh and scripts, Git Bash on Windows)

**Integration Points:**

1. **Gradle ↔ Version Catalog**: All plugin and dependency versions resolved from libs.versions.toml
2. **Gradle ↔ Convention Plugins**: build-logic/ composite build applies plugins to all modules
3. **Convention Plugins ↔ Quality Tools**: Plugins configure ktlint, Detekt, Konsist, Kotest
4. **CI/CD ↔ GitHub Actions**: Workflows triggered on push/PR/schedule events
5. **Git Hooks ↔ Quality Gates**: Pre-commit/pre-push hooks run quality checks locally
6. **Docker Compose ↔ Development Services**: Single stack manages all infrastructure services
7. **init-dev.sh ↔ All Services**: Script orchestrates startup and health checks

## Acceptance Criteria (Authoritative)

**Epic 1 - Foundation & Project Infrastructure:**

1. **AC1-1**: Gradle wrapper 9.1.0 configured and functional (gradlew, gradlew.bat)
2. **AC1-2**: Root build.gradle.kts with Kotlin plugin and multi-module configuration
3. **AC1-3**: Multi-module structure created with all required directories (framework/, products/, shared/, apps/, tools/, docker/, scripts/)
4. **AC1-4**: build-logic/ composite build with 4+ convention plugins (kotlin-common, spring-boot, quality-gates, testing)
5. **AC1-5**: gradle/libs.versions.toml with 28+ managed dependencies, all versions verified current
6. **AC1-6**: All framework modules compile with empty src/ directories
7. **AC1-7**: Docker Compose stack starts successfully with all 5 services (PostgreSQL, Keycloak, Redis, Prometheus, Grafana)
8. **AC1-8**: init-dev.sh completes in <5 minutes with health checks passing
9. **AC1-9**: DDD base classes implemented (AggregateRoot, Entity, ValueObject, DomainEvent)
10. **AC1-10**: Common types implemented (Money, Quantity, Identifier)
11. **AC1-11**: Exception hierarchy implemented (EafException, ValidationException, TenantIsolationException, AggregateNotFoundException)
12. **AC1-12**: Spring Modulith 1.4.4 configured with module boundary enforcement
13. **AC1-13**: Konsist 0.17.3 architecture tests verify module boundaries
14. **AC1-14**: CI/CD pipelines functional: ci.yml (<15min), nightly.yml (~2.5h), security-review.yml, validate-hooks.yml
15. **AC1-15**: Git hooks installed and functional: pre-commit (<5s), pre-push (<30s)
16. **AC1-16**: README.md with project overview, quick start, architecture overview
17. **AC1-17**: ./gradlew build executes successfully with zero violations (ktlint, Detekt, Konsist)
18. **AC1-18**: All quality gates configurable and pass: ktlint, Detekt, Konsist, Kover
19. **AC1-19**: Foundation documentation complete (README, CONTRIBUTING, getting-started)
20. **AC1-20**: Version catalog enforced: no hardcoded versions in any build.gradle.kts file

## Traceability Mapping

| AC # | Spec Section | Component(s) | Test Idea |
|------|-------------|--------------|-----------|
| AC1-1 | APIs & Interfaces | gradlew, gradlew.bat | Execute `./gradlew --version`, verify 9.1.0 |
| AC1-2 | Data Models | build.gradle.kts | Parse build file, verify Kotlin plugin present |
| AC1-3 | Services & Modules | Root directory structure | Check all directories exist via filesystem assertions |
| AC1-4 | Services & Modules | build-logic/ | Test convention plugin application on test module |
| AC1-5 | Dependencies | gradle/libs.versions.toml | Parse TOML, count entries, verify version format |
| AC1-6 | Services & Modules | framework/* modules | Run `./gradlew :framework:core:build`, verify SUCCESS |
| AC1-7 | Dependencies | docker-compose.yml | Run `docker-compose up -d`, verify 5 containers running |
| AC1-8 | Workflows & Sequencing | scripts/init-dev.sh | Execute script, measure time, verify exit code 0 |
| AC1-9 | Data Models | framework/core/domain/ | Unit test: Instantiate AggregateRoot, verify behavior |
| AC1-10 | Data Models | framework/core/common/types/ | Unit test: Create Money with validation |
| AC1-11 | Data Models | framework/core/common/exceptions/ | Unit test: Throw ValidationException, verify message |
| AC1-12 | System Arch Alignment | Spring Modulith config | Integration test: @SpringBootTest loads context |
| AC1-13 | System Arch Alignment | Konsist tests | Run `./gradlew konsistTest`, verify module rules pass |
| AC1-14 | Workflows & Sequencing | .github/workflows/ | Trigger CI workflow, verify all steps complete |
| AC1-15 | APIs & Interfaces | .git-hooks/ | Test hook: Commit with ktlint error, verify rejection |
| AC1-16 | N/A (Documentation) | README.md | Manual review: Verify sections present |
| AC1-17 | NFR: Performance | Root build | Run `./gradlew build`, verify zero violations |
| AC1-18 | NFR: Performance | Convention plugins | Run each quality gate independently, verify pass |
| AC1-19 | N/A (Documentation) | docs/getting-started/ | Manual review: Follow getting-started guide |
| AC1-20 | Dependencies | All build.gradle.kts | Grep for hardcoded versions, verify zero matches |

## Risks, Assumptions, Open Questions

**Risks:**

1. **RISK-1.1**: Docker/Podman compatibility issues on developer machines → **Mitigation**: Test on multiple platforms (macOS, Linux, Windows), provide fallback instructions
2. **RISK-1.2**: Gradle dependency resolution failures due to network issues → **Mitigation**: Configure Gradle retry logic, provide offline mode documentation
3. **RISK-1.3**: CI/CD pipeline exceeds 15-minute target → **Mitigation**: Optimize test execution, use Gradle cache effectively, monitor execution times
4. **RISK-1.4**: Git hooks too slow, developers bypass with --no-verify → **Mitigation**: Optimize pre-commit hook (<5s target), track bypass usage, educate team
5. **RISK-1.5**: Convention plugins too rigid, prevent valid use cases → **Mitigation**: Provide escape hatches, document override patterns, gather feedback
6. **RISK-1.6**: Multi-architecture support complexity (amd64/arm64/ppc64le) → **Mitigation**: Start with amd64/arm64, defer ppc64le to Epic 3 (Keycloak custom build)

**Assumptions:**

1. **ASSUME-1.1**: Developers have Docker Desktop or Podman installed (prerequisite)
2. **ASSUME-1.2**: JDK 21 LTS is acceptable (not latest JDK 23) for long-term support
3. **ASSUME-1.3**: GitHub Actions is available for CI/CD (project uses GitHub)
4. **ASSUME-1.4**: All dependency versions verified on 2025-10-30/31 remain current through Q1 2026
5. **ASSUME-1.5**: Prototype structure is production-ready and can be reused directly
6. **ASSUME-1.6**: Kotest-only testing strategy is accepted (JUnit explicitly forbidden)
7. **ASSUME-1.7**: Zero violations policy is acceptable (no exceptions for quality gates)

**Open Questions:**

1. **QUESTION-1.1**: Should we support Podman Compose in addition to Docker Compose? → **Answer Needed By**: Story 1.5
2. **QUESTION-1.2**: What is the preferred Git hook installation method (manual vs automatic via init-dev.sh)? → **Answer Needed By**: Story 1.6
3. **QUESTION-1.3**: Should foundation documentation include video tutorials or text-only? → **Answer Needed By**: Story 1.11
4. **QUESTION-1.4**: Are Gradle build scans (with data upload) acceptable for performance monitoring? → **Answer Needed By**: Story 1.9

## Test Strategy Summary

**Constitutional TDD (Mandatory):**
- All production code must be written test-first following Red-Green-Refactor cycle
- Enforced by Git hooks (pre-commit, pre-push) and CI/CD pipelines
- Code review requirement: Evidence of test-first development

**7-Layer Testing Defense:**

1. **Static Analysis** (Instant feedback):
   - ktlint 1.7.1: Code formatting verification
   - Detekt 1.23.8: Static code analysis, security rules
   - Konsist 0.17.3: Architecture boundary validation
   - Target: Zero violations

2. **Unit Tests** (Fast feedback <30s):
   - Framework: Kotest 6.0.4 (FunSpec, BehaviorSpec)
   - Coverage: Kover 0.9.3 (85%+ target)
   - Scope: DDD base classes, common types, exception hierarchy
   - Pattern: Nullable Pattern for fast execution (Epic 2+)

3. **Integration Tests** (Moderate feedback <3min):
   - Framework: Testcontainers 1.21.3
   - Scope: Convention plugin application, Docker Compose stack, build system
   - Real dependencies: PostgreSQL, Keycloak (Epic 3+), Redis (Epic 3+)
   - Coverage: 40-50% of test suite

4. **Property-Based Tests** (Nightly):
   - Framework: Kotest Property
   - Scope: Value object invariants (Money, Quantity validation)
   - Execution: Constructive generation (100x faster than filter-based)

5. **Fuzz Testing** (Nightly):
   - Framework: Jazzer 0.25.1
   - Scope: Configuration ready (executed in Epic 8+)
   - Targets: Build script parsing, configuration validation

6. **Concurrency Tests** (Nightly, Epic 8):
   - Framework: LitmusKt (JetBrains Research)
   - Scope: ThreadLocal usage (Epic 4+), build system thread safety
   - Deferred to Epic 8 for full implementation

7. **Mutation Testing** (Nightly):
   - Framework: Pitest 1.19.0
   - Target: 60-70% mutation score (realistic for deprecated Kotlin plugin)
   - Scope: Critical business logic (Epic 2+)

**Test Execution Strategy:**

- **Local Development**: Unit tests + ktlint + Detekt (<1 minute)
- **Pre-Commit Hook**: ktlint only (<5s)
- **Pre-Push Hook**: Detekt + unit tests (<30s)
- **CI/CD Fast (PR)**: All layers 1-3 (<15 minutes)
- **CI/CD Nightly**: All 7 layers (~2.5 hours)

**Coverage Targets:**

- Line Coverage (Kover): 85%+ for Epic 1 foundation code
- Mutation Coverage (Pitest): 60-70% (Epic 8+)
- Integration Coverage: 100% of critical paths (init-dev.sh, build system, convention plugins)

**Test Documentation:**

- Test naming convention: BDD-style (should, when, given)
- Test organization: Feature-based grouping
- Test evidence: CI/CD artifacts (HTML reports, XML for dashboards)
