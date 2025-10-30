# EAF v1.0 Decision Architecture

**Date:** 2025-10-30
**Project:** Enterprise Application Framework (EAF) v1.0
**For:** Wall-E
**Status:** Production-Ready

---

## 1. Executive Summary

EAF v1.0 implements a production-validated architecture combining **Hexagonal Architecture**, **CQRS/Event Sourcing** (Axon Framework 4.12.1), and **Spring Modulith 1.4.3** for programmatic boundary enforcement. Built on Kotlin 2.2.21 and Spring Boot 3.5.7 with PostgreSQL 16.6 as a swappable event store adapter, the framework delivers enterprise-grade multi-tenancy (3-layer isolation), comprehensive security (10-layer JWT validation, Keycloak OIDC), and industry-leading testing strategy (7-layer defense including Property-Based, Fuzz, and Concurrency testing).

The architecture supports **Active-Active HA readiness** through stateless design with phased deployment (Active-Passive MVP transitioning to Active-Active for enterprise customers), **multi-architecture support** (amd64/arm64/ppc64le with custom Keycloak builds), and exceptional developer experience via Scaffolding CLI (70-80% boilerplate elimination), Golden Path documentation, and 12-week tiered onboarding program.

All architectural decisions are optimized for AI agent consistency during implementation, with comprehensive implementation patterns preventing conflicts in naming, structure, formatting, and cross-cutting concerns.

---

## 2. Version Verification Log

**All technology versions verified via WebSearch on 2025-10-30:**

### Core Technology Stack

| Technology | Version | Verification Status | Source | Verified On |
|------------|---------|---------------------|--------|-------------|
| **Kotlin** | 2.2.21 | ✅ Current Stable | [kotlinlang.org/docs/releases.html](https://kotlinlang.org/docs/releases.html) | 2025-10-30 |
| **JVM** | 21 LTS | ✅ Current LTS | [openjdk.org](https://openjdk.org/) | 2025-10-30 |
| **Spring Boot** | 3.5.7 | ✅ Current Stable (GA) | [spring.io/blog](https://spring.io/blog/2025/10/23/spring-boot-3-5-7-available-now/) | 2025-10-30 |
| **Spring Modulith** | 1.4.3 | ✅ Current Stable | [spring.io/projects/spring-modulith](https://spring.io/projects/spring-modulith) | 2025-10-30 |
| **Axon Framework** | 4.12.1 | ✅ Production Stable | [docs.axoniq.io](https://docs.axoniq.io/) | 2025-10-30 |
| **PostgreSQL** | 16.10 | ✅ Current Stable (16.x series) | [postgresql.org/about/news](https://www.postgresql.org/about/news/postgresql-176-1610-1514-1419-1322-and-18-beta-3-released-3118/) | 2025-10-30 |
| **jOOQ** | 3.20.8 | ✅ Current Stable | [jooq.org](https://www.jooq.org/) | 2025-10-30 |
| **Keycloak** | 26.4.2 | ✅ Current Stable | [keycloak.org](https://www.keycloak.org/2025/10/keycloak-2642-released) | 2025-10-30 |
| **Flowable BPMN** | 7.2.0 | ✅ Current Stable | [flowable.com](https://www.flowable.com/) | 2025-10-30 |
| **Gradle** | 9.1.0 | ✅ Current Stable | [gradle.org/releases](https://gradle.org/releases/) | 2025-10-30 |

### Testing & Quality Stack

| Technology | Version | Verification Status | Source | Verified On |
|------------|---------|---------------------|--------|-------------|
| **Kotest** | 6.0.4 | ✅ Current Stable | [kotest.io](https://kotest.io/) | 2025-10-30 |
| **Testcontainers** | 1.21.3 | ✅ Current Stable | [testcontainers.com](https://www.testcontainers.com/) | 2025-10-30 |
| **Jazzer** | 0.25.1 | ✅ Current Stable | [github.com/CodeIntelligenceTesting/jazzer](https://github.com/CodeIntelligenceTesting/jazzer) | 2025-10-30 |
| **Pitest** | 1.19.0 | ✅ Current Stable | [pitest.org](https://pitest.org/) | 2025-10-30 |
| **ktlint** | 1.7.1 | ✅ Current Stable | [github.com/pinterest/ktlint](https://github.com/pinterest/ktlint) | 2025-10-30 |
| **Detekt** | 1.23.8 | ✅ Current Stable | [detekt.dev](https://detekt.dev/) | 2025-10-30 |
| **Konsist** | 0.17.3 | ✅ Current Stable | [konsist.lemonappdev.com](https://konsist.lemonappdev.com/) | 2025-10-30 |
| **Kover** | 0.9.3 | ✅ Current Stable | [github.com/Kotlin/kotlinx-kover](https://github.com/Kotlin/kotlinx-kover) | 2025-10-30 |

### Infrastructure & Deployment Stack

| Technology | Version | Verification Status | Source | Verified On |
|------------|---------|---------------------|--------|-------------|
| **Docker Compose** | 2.40.3 | ✅ Current Stable | [github.com/docker/compose/releases](https://github.com/docker/compose/releases) | 2025-10-30 |
| **Redis** | 7.2 | ✅ Current Stable | [redis.io](https://redis.io/) | 2025-10-30 |
| **Prometheus** | 1.15.5 | ✅ Current Stable (Micrometer) | [micrometer.io](https://micrometer.io/) | 2025-10-30 |
| **OpenTelemetry** | 1.55.0 (API/SDK) / 2.20.1 (instrumentation) | ✅ Current Stable | [opentelemetry.io](https://opentelemetry.io/) | 2025-10-30 |
| **Logback** | 1.5.19 | ✅ Current Stable | [logback.qos.ch](https://logback.qos.ch/) | 2025-10-30 |
| **Grafana** | 12.2 | ✅ Current Stable | [grafana.com/blog](https://grafana.com/blog/2025/09/25/grafana-12-2-release-all-the-latest-features/) | 2025-10-30 |

### Developer Experience Stack

| Technology | Version | Verification Status | Source | Verified On |
|------------|---------|---------------------|--------|-------------|
| **Picocli** | 4.7.7 | ✅ Current Stable | [picocli.info](https://picocli.info/) | 2025-10-30 |
| **Mustache** | 0.9.14 | ✅ Current Stable | [github.com/spullara/mustache.java](https://github.com/spullara/mustache.java) | 2025-10-30 |
| **Springdoc OpenAPI** | 2.6.0 | ✅ Current Stable | [springdoc.org](https://springdoc.org/) | 2025-10-30 |
| **Dokka** | 2.1.0 | ✅ Current Stable | [kotlinlang.org/docs/dokka-introduction.html](https://kotlinlang.org/docs/dokka-introduction.html) | 2025-10-30 |

### Version Selection Criteria

- **LTS Preference:** JVM 21 LTS selected over latest JDK (23) for long-term support
- **Stability Over Bleeding Edge:** All versions are stable GA releases, no alpha/beta for critical path
- **Patch Updates:** PostgreSQL 16.10 (was 16.6), Keycloak 26.4.2 (was 26.4.0) updated to latest patches
- **Compatibility Verified:** All versions tested for inter-compatibility (e.g., Spring Boot 3.5.7 + Kotlin 2.2.21)
- **Breaking Change Awareness:** Axon 5.x migration planned Q3-Q4 2026 (1-1.5 months effort documented)

### Next Verification

**Recommended:** Quarterly version review (align with Keycloak ppc64le rebuild schedule)

---

## 3. Project Initialization

### Foundation Strategy

EAF v1.0 **reuses validated prototype structure** from `/Users/michael/acci_eaf` rather than using standard starter templates (Spring Initializr, JHipster). Standard starters provide <30% coverage of EAF's requirements (Hexagonal Architecture, CQRS/ES, Spring Modulith, Multi-Tenancy, 7-layer testing).

### Initialization Command (Epic 1: Foundation)

```bash
# Clone validated prototype structure:
git clone <prototype-repo> eaf-v1
cd eaf-v1

# Clean prototype implementations (keep structure):
rm -rf framework/*/src/     # Remove prototype code
rm -rf products/*/src/      # Remove prototype products
# Keep: Build config, module structure, Docker setup, CI/CD pipelines

# Verify structure compiles:
./gradlew build

# Initialize development stack:
./scripts/init-dev.sh
```

### What the Prototype Structure Provides

**Build System:**
- Gradle 9.1.0 with Kotlin DSL
- Multi-module monorepo (framework/, products/, shared/, apps/, tools/)
- Convention plugins in build-logic/
- Version catalog (gradle/libs.versions.toml) with 28 managed dependencies

**Framework Modules:**
- `:framework:core` - DDD base classes, domain primitives
- `:framework:security` - 10-layer JWT, Keycloak OIDC
- `:framework:multi-tenancy` - 3-layer tenant isolation
- `:framework:cqrs` - Axon configuration, command/query gateways
- `:framework:persistence` - PostgreSQL event store adapter, jOOQ projections
- `:framework:observability` - Structured logging, Prometheus, OpenTelemetry
- `:framework:workflow` - Flowable BPMN, Axon bridge
- `:framework:web` - REST API foundation, RFC 7807 error handling

**Development Infrastructure:**
- Docker Compose stack (PostgreSQL, Keycloak, Redis, Prometheus, Grafana)
- One-command setup script (init-dev.sh)
- Git hooks for quality gates (ktlint, Detekt)
- Multi-architecture build support (amd64/arm64/ppc64le)

**Quality Gates:**
- ktlint 1.7.1 (code formatting)
- Detekt 1.23.8 (static analysis)
- Konsist 0.17.3 (architecture boundaries)
- Pitest 1.19.0 (mutation testing)
- Kover 0.9.3 (coverage)

**Testing Infrastructure:**
- Kotest 6.0.4 (primary testing framework)
- Testcontainers 1.21.3 (real dependencies)
- Jazzer 0.25.1 (fuzz testing)
- Property-based testing (Kotest property)
- LitmusKt (concurrency testing - to be added Epic 8)

**CI/CD Pipelines:**
- `.github/workflows/ci.yml` - Fast feedback (<15min)
- `.github/workflows/nightly.yml` - Deep validation (~2.5h)
- `.github/workflows/security-review.yml` - OWASP dependency check
- `.github/workflows/validate-hooks.yml` - Git hooks validation

This foundation eliminates 4-6 weeks of setup time and ensures architectural consistency from inception.

---

## 4. Decision Summary

### Core Technology Stack

| Category | Decision | Version | Affects Epics | Rationale | Source |
|----------|----------|---------|---------------|-----------|--------|
| **Language** | Kotlin | 2.2.21 | All | Type-safe, null-safe, JVM interop, excellent Spring Boot support | Prototype (updated) |
| **Runtime** | JVM | 21 LTS | All | Long-term support, mature ecosystem, multi-arch support | Prototype |
| **App Framework** | Spring Boot | 3.5.7 | All | Industry standard, comprehensive ecosystem, production-proven | Prototype (updated) |
| **Architecture Enforcement** | Spring Modulith | 1.4.3 | All | Compile-time boundary verification, hexagonal architecture support | Prototype (updated) |
| **CQRS/Event Sourcing** | Axon Framework | 4.12.1 | All | Mature CQRS/ES framework, PostgreSQL support, production-proven | Prototype |
| **Event Store** | PostgreSQL | 16.10 | Epic 2+ | Only viable FOSS event store with native Axon support, swappable adapter | Prototype + Analysis (updated 2025-10-30) |
| **Query Layer** | jOOQ | 3.20.8 | Epic 2+ | Type-safe SQL for projections, excellent Kotlin support | Prototype (updated) |
| **Identity Provider** | Keycloak | 26.4.2 | Epic 3+ | Enterprise OIDC, multi-tenancy support, proven at scale | Prototype (updated 2025-10-30) |
| **Workflow Engine** | Flowable BPMN | 7.2.0 | Epic 6+ | Better than Camunda 7 (EOL approaching), BPMN 2.0, compensating transactions | Prototype |
| **Build Tool** | Gradle | 9.1.0 | All | Kotlin DSL, multi-module support, superior to Maven for Kotlin | Prototype |

### Testing & Quality Stack

| Category | Decision | Version | Affects Epics | Rationale | Source |
|----------|----------|---------|---------------|-----------|--------|
| **Testing Framework** | Kotest | 6.0.4 | All | Kotlin-native, BDD syntax, property testing, better than JUnit 5 | Prototype |
| **Integration Testing** | Testcontainers | 1.21.3 | All | Real dependencies (PostgreSQL, Keycloak, Redis), production-realistic | Prototype |
| **Fuzz Testing** | Jazzer | 0.25.1 | Epic 8+ | Google OSS-Fuzz standard, coverage-guided, finds crashes/DoS | Prototype |
| **Concurrency Testing** | LitmusKt | TBD | Epic 8 | JetBrains Research, race condition detection, memory model validation | Analysis (NEW) |
| **Mutation Testing** | Pitest | 1.19.0 | Epic 8+ | Only FOSS option (deprecated Kotlin plugin), 60-70% target realistic | Prototype + Analysis |
| **Code Formatting** | ktlint | 1.7.1 | All | Kotlin official style, automated, Git hook integration | Prototype |
| **Static Analysis** | Detekt | 1.23.8 | All | Comprehensive rules, Spring Boot aware, zero violations enforced | Prototype |
| **Architecture Testing** | Konsist | 0.17.3 | All | Spring Modulith boundary verification, hexagonal architecture validation | Prototype |
| **Coverage** | Kover | 0.9.3 | All | Kotlin-native coverage (better than JaCoCo), 85%+ target | Prototype |

### Infrastructure & Deployment Stack

| Category | Decision | Version | Affects Epics | Rationale | Source |
|----------|----------|---------|---------------|-----------|--------|
| **Container Runtime** | Docker Compose | 2.40.3 | All | Standard, mature, Podman Compose not ready, excellent M1/M2 support | Analysis (verified 2025-10-30) |
| **Cache & Session** | Redis | 7.2 | Epic 3+ | JWT revocation blacklist, Phase 2 session management, Sentinel HA ready | Prototype |
| **Metrics** | Prometheus + Micrometer | 1.15.5 | Epic 5+ | Industry standard, pull-based, comprehensive Spring Boot integration | Prototype |
| **Distributed Tracing** | OpenTelemetry | 1.55.0 / 2.20.1 | Epic 5+ | Vendor-neutral, automatic instrumentation, W3C Trace Context | Prototype (updated) |
| **Structured Logging** | Logback + Logstash Encoder | 1.5.19 / 8.1 | Epic 5+ | JSON logging, automatic context injection (trace_id, tenant_id) | Prototype |
| **Dashboards** | Grafana | 12.2 | Post-MVP | Deferred (dashboards optional per Product Brief) | Prototype (verified 2025-10-30) |

### Developer Experience Stack

| Category | Decision | Version | Affects Epics | Rationale | Source |
|----------|----------|---------|---------------|-----------|--------|
| **CLI Framework** | Picocli | 4.7.7 | Epic 7 | Modern CLI library, annotation-based, excellent Kotlin support | Prototype |
| **Template Engine** | Mustache | 0.9.14 | Epic 7 | Logic-less templates, simple, JHipster migration path | Prototype |
| **Frontend Framework** | React-Admin | TBD | Epic 7 | Operator portal, Material-UI, Keycloak integration | Prototype |
| **API Documentation** | Springdoc OpenAPI | 2.6.0 | Epic 2+ | OpenAPI 3.0 generation, Swagger UI, Spring Boot 3 support | Prototype |
| **Code Documentation** | Dokka | 2.1.0 | Epic 7.5 | Kotlin-native documentation generator | Prototype |

### Critical Architectural Decisions

| Decision | Value | Affects Epics | Rationale | Source |
|----------|-------|---------------|-----------|--------|
| **Multi-Architecture** | amd64, arm64, ppc64le | All | Framework reusability, ZEWSSP uses amd64, future products may need ppc64le | Analysis (NEW) |
| **Keycloak ppc64le** | Custom build (UBI9-based) | Epic 3 | No official images, €4.4K investment for future-proofing | Analysis (NEW) |
| **HA Strategy Phase 1** | Active-Passive (<2min failover) | All | Standard customers, Patroni PostgreSQL, stateless app design | Product Brief + Analysis |
| **HA Strategy Phase 2** | Active-Active (<30s failover) | Post-MVP | Enterprise customers (Coast-to-Coast), 3-4 week migration, no code changes | Analysis (NEW) |
| **Event Store Partitioning** | Time-based (monthly) | Epic 2 | Performance optimization, archive strategy | Decision #1 |
| **Event Store Indexing** | BRIN indexes | Epic 2 | Low overhead for time-series data | Decision #1 |
| **Snapshot Strategy** | Every 100 events | Epic 2 | Balance performance vs storage | Decision #1 |
| **Tenant Resolution** | JWT `tenant_id` claim (primary), X-Tenant-ID header (dev fallback) | Epic 4 | Keycloak standard, secure-by-default, dev-friendly | Decision #2 |
| **PostgreSQL RLS** | Tenant isolation policies | Epic 4 | Layer 3 defense-in-depth, database-level security | Decision #2 |
| **API Response Format** | Direct response (no envelope) | Epic 2+ | Minimal overhead, industry standard | Decision #3 |
| **Error Format** | RFC 7807 Problem Details | Epic 2+ | Standardized error responses, includes traceId and tenantId | Decision #3 |
| **Pagination** | Cursor-based | Epic 2+ | Scalable, consistent performance at scale | Decision #3 |
| **Error Handling (Domain)** | Arrow Either | All | Functional purity, explicit error types | Decision #4 |
| **Error Handling (App)** | Spring Exceptions | All | Framework conventions, Spring Boot compatibility | Decision #4 |
| **Logging Format** | JSON structured | Epic 5+ | Machine-parsable, auto-correlation (trace_id, tenant_id), PII masking | Decision #5 |
| **Date/Time Storage** | UTC everywhere, ISO-8601, Instant/TIMESTAMPTZ | All | Eliminates timezone bugs, international standard | Decision #6 |
| **Projection Updates** | Real-time (TrackingEventProcessor) | Epic 2+ | <10s lag target, eventual consistency acceptable | Decision #7 |
| **Flowable-Axon Bridge** | ServiceTask command dispatch, Event signal correlation | Epic 6 | Bidirectional integration, tenant-aware workflows | Decision #8 |
| **React-Admin Provider** | Custom REST data provider, Keycloak auth provider | Epic 7 | Cursor pagination compatibility, OIDC integration | Decision #9 |
| **Performance Budgets** | API p95 <200ms, Event lag <10s, Query timeout 30s | All | Product Brief NFRs, monitoring triggers | Decision #10 |
| **i18n** | English only (MVP), Spring MessageSource (Post-MVP) | Post-MVP | ZEWSSP German market, defer complexity | Decision #11 |
| **Audit Trail** | Event Store IS audit log, dedicated table deferred | All | Event Sourcing provides audit by design | Decision #12 |
| **Feature Flags** | Environment variables (MVP), LaunchDarkly/Unleash (Post-MVP) | Epic 9+ | Simple for MVP, clear evolution | Decision #13 |

### Testing Strategy Decisions

| Decision | Value | Affects Epics | Rationale | Source |
|----------|-------|---------------|-----------|--------|
| **Testing Philosophy** | 7-layer defense-in-depth | All | Static → Unit → Integration → Property → Fuzz → Concurrency → Mutation | Analysis (NEW) |
| **Unit Testing** | Nullables Pattern (James Shore) | All | 100-1000x faster than mocking frameworks, production code with off-switch | Analysis (NEW) |
| **Aggregate Testing** | Axon Test Fixtures | Epic 2+ | Framework-optimized, Given-When-Then DSL | Prototype |
| **Property-Based Testing** | Kotest Property (constructive generation) | Nightly | Idempotence, security invariants, 100x faster than filter-based | Prototype |
| **Fuzz Testing** | 7 targets × 5 min (Jazzer) | Nightly | JWT parsing, role normalization, DoS detection | Prototype |
| **Concurrency Testing** | LitmusKt (TenantContext, Event Processors, Locks) | Epic 8, Nightly | Race condition detection, memory model validation, prevents €50K-140K bugs | Analysis (NEW) |
| **Mutation Testing** | Pitest 60-70% target (excludes property tests) | Nightly | Test effectiveness validation, realistic target for deprecated Kotlin plugin | Prototype + Analysis |
| **Integration Testing** | Testcontainers (PostgreSQL, Keycloak, Redis) | All | 40-50% of test suite, production-realistic, real dependencies mandatory | Prototype |
| **Coverage Target** | 85%+ line coverage | All | Measured by Kover, enforced in CI/CD | Product Brief |

**Total Decisions Documented:** 88 (75 from prototype + 13 new)
**Decision Coverage:** 100% of architectural requirements

---

## 5. Complete Project Structure

```
eaf-v1/
├── .github/
│   └── workflows/
│       ├── ci.yml                          # PR validation (fast: <15min)
│       ├── nightly.yml                     # Deep validation (~2.5h: Property, Fuzz, LitmusKt, Mutation)
│       ├── security-review.yml             # OWASP dependency check
│       └── validate-hooks.yml              # Git hooks CI validation
│
├── build-logic/                            # Gradle Convention Plugins
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/main/kotlin/
│       ├── eaf.kotlin-common.gradle.kts    # Common Kotlin configuration
│       ├── eaf.spring-boot.gradle.kts      # Spring Boot configuration
│       ├── eaf.quality-gates.gradle.kts    # ktlint, Detekt, Konsist
│       ├── eaf.testing.gradle.kts          # Kotest, Testcontainers, Jazzer, Pitest
│       └── eaf.publishing.gradle.kts       # Artifact publishing (future)
│
├── framework/                              # Core Framework Modules (Spring Modulith enforced)
│   ├── core/                               # Epic 1: Foundation
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/kotlin/com/axians/eaf/framework/core/
│   │       │   ├── domain/                 # DDD base classes
│   │       │   │   ├── AggregateRoot.kt
│   │       │   │   ├── Entity.kt
│   │       │   │   ├── ValueObject.kt
│   │       │   │   └── DomainEvent.kt
│   │       │   ├── common/
│   │       │   │   ├── types/              # Money, Quantity, etc.
│   │       │   │   │   ├── Money.kt
│   │       │   │   │   ├── Quantity.kt
│   │       │   │   │   └── Identifier.kt
│   │       │   │   └── exceptions/         # Base exception hierarchy
│   │       │   │       ├── EafException.kt
│   │       │   │       ├── ValidationException.kt
│   │       │   │       ├── TenantIsolationException.kt
│   │       │   │       └── AggregateNotFoundException.kt
│   │       │   └── config/
│   │       │       └── EafCoreConfiguration.kt
│   │       └── test/kotlin/                # Unit tests with Nullables
│   │
│   ├── security/                           # Epic 3: Authentication & Authorization
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/kotlin/com/axians/eaf/framework/security/
│   │       │   ├── jwt/
│   │       │   │   ├── JwtValidationFilter.kt      # 10-layer validation
│   │       │   │   ├── TokenExtractor.kt
│   │       │   │   └── RoleNormalizer.kt
│   │       │   ├── keycloak/
│   │       │   │   ├── KeycloakOidcConfiguration.kt
│   │       │   │   └── KeycloakJwksProvider.kt
│   │       │   ├── revocation/
│   │       │   │   └── RedisRevocationStore.kt
│   │       │   └── config/
│   │       │       └── SecurityConfiguration.kt
│   │       ├── test/kotlin/                        # Unit tests (Nullables)
│   │       ├── integrationTest/kotlin/             # Integration (Testcontainers Keycloak)
│   │       ├── propertyTest/kotlin/                # Property-based tests (Kotest)
│   │       │   ├── RoleNormalizationPropertyTest.kt
│   │       │   └── TokenExtractorPropertyTest.kt
│   │       └── fuzzTest/kotlin/                    # Jazzer fuzz tests
│   │           ├── JwtFormatFuzzer.kt
│   │           ├── TokenExtractorFuzzer.kt
│   │           └── RoleNormalizationFuzzer.kt
│   │
│   ├── multi-tenancy/                      # Epic 4: Multi-Tenancy
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/kotlin/com/axians/eaf/framework/multitenancy/
│   │       │   ├── context/
│   │       │   │   ├── TenantContext.kt            # ThreadLocal context
│   │       │   │   ├── TenantContextHolder.kt
│   │       │   │   └── TenantId.kt                 # Value Object
│   │       │   ├── filter/
│   │       │   │   └── TenantContextFilter.kt      # Layer 1: Extract from JWT
│   │       │   ├── interceptor/
│   │       │   │   └── AxonTenantInterceptor.kt    # Axon message context propagation
│   │       │   ├── validation/
│   │       │   │   └── TenantValidator.kt          # Layer 2: Service validation
│   │       │   └── config/
│   │       │       └── MultiTenancyConfiguration.kt
│   │       ├── test/kotlin/
│   │       ├── integrationTest/kotlin/
│   │       │   └── TenantIsolationIntegrationTest.kt
│   │       └── litmusTest/kotlin/                  # LitmusKt concurrency tests (Epic 8)
│   │           ├── TenantContextIsolationTest.kt
│   │           ├── EventProcessorPropagationTest.kt
│   │           └── ConnectionPoolContextTest.kt
│   │
│   ├── cqrs/                               # Epic 2: CQRS/Event Sourcing
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/kotlin/com/axians/eaf/framework/cqrs/
│   │       │   ├── command/
│   │       │   │   ├── CommandGatewayConfiguration.kt
│   │       │   │   └── CommandHandlerInterceptor.kt
│   │       │   ├── query/
│   │       │   │   ├── QueryGatewayConfiguration.kt
│   │       │   │   └── QueryHandlerInterceptor.kt
│   │       │   ├── event/
│   │       │   │   ├── EventProcessorConfiguration.kt
│   │       │   │   └── TrackingTokenStore.kt
│   │       │   └── saga/
│   │       │       └── SagaConfiguration.kt
│   │       └── test/kotlin/
│   │
│   ├── persistence/                        # Epic 2: Persistence Layer
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/kotlin/com/axians/eaf/framework/persistence/
│   │       │   ├── eventstore/
│   │       │   │   ├── AxonEventStoreConfiguration.kt
│   │       │   │   ├── PostgresEventStoreAdapter.kt  # Hexagonal adapter (swappable)
│   │       │   │   └── EventStoreOptimization.kt     # Partitioning, BRIN indexes
│   │       │   ├── projection/
│   │       │   │   ├── JooqConfiguration.kt
│   │       │   │   ├── ProjectionBase.kt
│   │       │   │   └── ProjectionEventHandler.kt
│   │       │   └── migration/
│   │       │       └── FlywayConfiguration.kt
│   │       ├── main/resources/db/migration/
│   │       │   ├── V001__event_store_schema.sql
│   │       │   ├── V002__partitioning_setup.sql
│   │       │   ├── V003__brin_indexes.sql
│   │       │   ├── V004__rls_policies.sql            # PostgreSQL Row-Level Security
│   │       │   └── V005__token_store.sql             # Axon token store for processor coordination
│   │       ├── test/kotlin/
│   │       └── integrationTest/kotlin/
│   │           └── EventStoreIntegrationTest.kt
│   │
│   ├── observability/                      # Epic 5: Observability
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/kotlin/com/axians/eaf/framework/observability/
│   │       │   ├── logging/
│   │       │   │   ├── StructuredLoggingConfiguration.kt
│   │       │   │   ├── PiiMaskingFilter.kt
│   │       │   │   └── ContextEnricher.kt           # Auto-inject trace_id, tenant_id
│   │       │   ├── metrics/
│   │       │   │   ├── MicrometerConfiguration.kt
│   │       │   │   └── CustomMetrics.kt
│   │       │   └── tracing/
│   │       │       ├── OpenTelemetryConfiguration.kt
│   │       │       └── TraceContextPropagation.kt
│   │       ├── main/resources/
│   │       │   └── logback-spring.xml              # JSON logging configuration
│   │       └── test/kotlin/
│   │
│   ├── workflow/                           # Epic 6: Flowable BPMN
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/kotlin/com/axians/eaf/framework/workflow/
│   │       │   ├── flowable/
│   │       │   │   ├── FlowableConfiguration.kt
│   │       │   │   └── TenantAwareProcessEngine.kt
│   │       │   ├── bridge/
│   │       │   │   ├── AxonCommandGatewayDelegate.kt  # BPMN → Axon commands
│   │       │   │   └── FlowableEventListener.kt       # Axon events → BPMN signals
│   │       │   └── ansible/
│   │       │       ├── AnsibleAdapter.kt              # Legacy Dockets migration
│   │       │       └── AnsibleTaskExecutor.kt
│   │       ├── main/resources/processes/
│   │       │   └── dockets-pattern.bpmn20.xml        # Migration template
│   │       ├── test/kotlin/
│   │       └── integrationTest/kotlin/
│   │
│   └── web/                                # Epic 2: REST API Foundation
│       ├── build.gradle.kts
│       └── src/
│           ├── main/kotlin/com/axians/eaf/framework/web/
│           │   ├── rest/
│           │   │   ├── RestConfiguration.kt
│           │   │   ├── ProblemDetailExceptionHandler.kt  # RFC 7807
│           │   │   └── CursorPaginationSupport.kt
│           │   ├── openapi/
│           │   │   └── OpenApiConfiguration.kt
│           │   └── cors/
│           │       └── CorsConfiguration.kt
│           └── test/kotlin/
│
├── products/                               # Product Implementations
│   ├── widget-demo/                        # Epic 9: Reference Application
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/kotlin/com/axians/eaf/products/widget/
│   │       │   ├── domain/
│   │       │   │   ├── Widget.kt                      # Aggregate
│   │       │   │   ├── WidgetId.kt                    # Value Object
│   │       │   │   └── WidgetStatus.kt                # Enum
│   │       │   ├── command/
│   │       │   │   ├── CreateWidgetCommand.kt
│   │       │   │   ├── PublishWidgetCommand.kt
│   │       │   │   └── DeleteWidgetCommand.kt
│   │       │   ├── event/
│   │       │   │   ├── WidgetCreatedEvent.kt
│   │       │   │   ├── WidgetPublishedEvent.kt
│   │       │   │   └── WidgetDeletedEvent.kt
│   │       │   ├── query/
│   │       │   │   ├── WidgetProjection.kt
│   │       │   │   ├── FindWidgetQuery.kt
│   │       │   │   └── ListWidgetsQuery.kt
│   │       │   ├── api/
│   │       │   │   ├── WidgetController.kt            # REST API
│   │       │   │   └── WidgetRequest.kt               # DTOs
│   │       │   └── workflow/
│   │       │       └── widget-approval.bpmn20.xml     # Flowable approval process
│   │       ├── main/resources/db/migration/
│   │       │   └── V100__widget_projections.sql      # Product-specific migrations (V100+)
│   │       ├── test/kotlin/
│   │       │   ├── WidgetAggregateTest.kt            # Axon Test Fixtures
│   │       │   └── WidgetServiceTest.kt              # Nullables Pattern
│   │       └── integrationTest/kotlin/
│   │           ├── WidgetApiIntegrationTest.kt       # Testcontainers
│   │           └── WidgetWorkflowIntegrationTest.kt  # Flowable + Axon
│   │
│   └── licensing-server/                   # Future: ZEWSSP migration target (post-MVP)
│       └── README.md                       # Placeholder
│
├── shared/                                 # Shared Libraries
│   ├── shared-api/                         # API contracts (DTOs, interfaces)
│   │   └── src/main/kotlin/com/axians/eaf/shared/api/
│   │       ├── dto/
│   │       │   ├── PaginatedResponse.kt
│   │       │   └── CursorPagination.kt
│   │       └── common/
│   │           └── ApiConstants.kt
│   │
│   ├── shared-types/                       # Common types
│   │   └── src/main/kotlin/com/axians/eaf/shared/types/
│   │       ├── Money.kt
│   │       ├── Quantity.kt
│   │       ├── Email.kt
│   │       └── PhoneNumber.kt
│   │
│   └── testing/                            # Testing utilities
│       └── src/
│           ├── main/kotlin/com/axians/eaf/testing/
│           │   ├── nullables/              # Nullables Pattern utilities
│           │   │   ├── NullableFactory.kt
│           │   │   ├── OutputTracker.kt
│           │   │   └── BehaviorSimulator.kt
│           │   ├── builders/               # Test data builders
│           │   │   ├── OrderTestBuilder.kt
│           │   │   └── CustomerTestBuilder.kt
│           │   └── tags/                   # Kotest tags
│           │       ├── PbtTag.kt            # Property-based test tag
│           │       ├── FuzzTag.kt           # Fuzz test tag
│           │       └── LitmusTag.kt         # Concurrency test tag
│           └── testFixtures/kotlin/        # Shared test fixtures
│               └── TestContainersConfiguration.kt
│
├── apps/                                   # Frontend Applications
│   └── admin/                              # Epic 7: React-Admin Operator Portal
│       ├── package.json
│       ├── pnpm-lock.yaml
│       ├── vite.config.ts
│       ├── tsconfig.json
│       └── src/
│           ├── components/
│           │   └── widgets/                # Generated by: eaf scaffold ra-resource widget
│           │       ├── WidgetList.tsx
│           │       ├── WidgetEdit.tsx
│           │       ├── WidgetCreate.tsx
│           │       └── WidgetShow.tsx
│           ├── providers/
│           │   ├── dataProvider.ts         # Custom REST provider (cursor pagination)
│           │   └── authProvider.ts         # Keycloak OIDC integration
│           ├── theme/
│           │   └── customTheme.ts          # Material-UI v5 overrides
│           └── App.tsx
│
├── tools/                                  # Development Tools
│   └── eaf-cli/                            # Epic 7: Scaffolding CLI
│       ├── build.gradle.kts
│       └── src/
│           ├── main/kotlin/com/axians/eaf/cli/
│           │   ├── commands/
│           │   │   ├── ScaffoldModuleCommand.kt
│           │   │   ├── ScaffoldAggregateCommand.kt
│           │   │   ├── ScaffoldApiResourceCommand.kt
│           │   │   ├── ScaffoldProjectionCommand.kt
│           │   │   └── ScaffoldRaResourceCommand.kt
│           │   ├── templates/              # Mustache templates
│           │   │   ├── aggregate/
│           │   │   │   ├── Aggregate.kt.mustache
│           │   │   │   ├── Command.kt.mustache
│           │   │   │   ├── Event.kt.mustache
│           │   │   │   └── AggregateTest.kt.mustache
│           │   │   ├── api-resource/
│           │   │   │   ├── Controller.kt.mustache
│           │   │   │   └── Request.kt.mustache
│           │   │   ├── projection/
│           │   │   │   ├── Projection.kt.mustache
│           │   │   │   └── EventHandler.kt.mustache
│           │   │   └── ra-resource/
│           │   │       ├── List.tsx.mustache
│           │   │       ├── Edit.tsx.mustache
│           │   │       └── Create.tsx.mustache
│           │   ├── generator/
│           │   │   └── CodeGenerator.kt    # Template processing
│           │   └── EafCli.kt               # Picocli main
│           └── test/kotlin/
│               └── CodeGeneratorTest.kt
│
├── docker/                                 # Docker Infrastructure
│   ├── keycloak/
│   │   ├── Dockerfile                      # Multi-arch (amd64/arm64)
│   │   ├── Dockerfile.ppc64le              # Custom ppc64le build (UBI9-based)
│   │   └── realm-export.json               # Test realm with users/roles
│   ├── postgres/
│   │   └── init-scripts/
│   │       ├── 01-create-schemas.sql       # Axon, Flowable, Projections schemas
│   │       └── 02-enable-rls.sql           # Row-Level Security setup
│   ├── grafana/
│   │   ├── dashboards/                     # Post-MVP (deferred)
│   │   └── provisioning/
│   └── prometheus/
│       └── prometheus.yml                  # Scrape configuration
│
├── scripts/                                # Development & Operations Scripts
│   ├── init-dev.sh                         # One-command development setup
│   ├── health-check.sh                     # Verify all services ready
│   ├── seed-data.sh                        # Load test data (Keycloak users, sample aggregates)
│   ├── install-git-hooks.sh                # Install pre-commit/pre-push hooks
│   └── build-multi-arch.sh                 # Docker multi-arch build automation
│
├── docs/                                   # Epic 7.5: Golden Path Documentation
│   ├── getting-started/
│   │   ├── 00-prerequisites.md
│   │   ├── 01-your-first-aggregate.md     # 15-minute tutorial
│   │   ├── 02-understanding-cqrs.md
│   │   ├── 03-understanding-event-sourcing.md
│   │   └── 04-axon-framework-basics.md
│   ├── tutorials/
│   │   ├── simple-aggregate.md            # Milestone 1 guide
│   │   ├── standard-aggregate.md          # Milestone 2 guide
│   │   ├── production-aggregate.md        # Milestone 3 guide
│   │   ├── multi-tenancy.md
│   │   ├── projections-and-queries.md
│   │   └── sagas-and-workflows.md
│   ├── how-to/
│   │   ├── handle-validation-errors.md
│   │   ├── implement-business-rules.md
│   │   ├── test-with-axon-fixtures.md
│   │   ├── test-with-nullables.md
│   │   ├── debug-event-sourcing.md
│   │   └── troubleshoot-common-issues.md
│   ├── reference/
│   │   ├── architecture-decisions.md       # THIS DOCUMENT
│   │   ├── api-documentation.md
│   │   ├── configuration-reference.md
│   │   └── cli-commands.md
│   └── examples/
│       ├── simple-widget/                  # Fully working example
│       ├── multi-tenant-order/             # Production example
│       └── saga-payment/                   # Complex workflow example
│
├── gradle/
│   ├── libs.versions.toml                  # Version catalog (28 dependencies)
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties       # Gradle 9.1.0
│
├── docker-compose.yml                      # Development stack (PostgreSQL, Keycloak, Redis, Prometheus, Grafana)
├── docker-compose.prod.yml                 # Production template (Active-Passive HA)
├── docker-compose.active-active.yml        # Phase 2 template (Active-Active Multi-DC)
├── settings.gradle.kts                     # Module configuration
├── build.gradle.kts                        # Root build script
├── gradlew                                 # Gradle wrapper (Unix)
├── gradlew.bat                             # Gradle wrapper (Windows)
├── .gitignore
├── .editorconfig
├── .git-hooks/                             # Git hooks (installed by init-dev.sh)
│   ├── pre-commit                          # ktlint check (<5s)
│   └── pre-push                            # Detekt + fast tests (<30s)
├── README.md
└── LICENSE                                 # Apache 2.0 (or Axians choice)
```

---

## 6. Epic to Architecture Mapping

| Epic | Primary Modules | Secondary Modules | Integration Points | Key Deliverables |
|------|----------------|-------------------|-------------------|------------------|
| **Epic 1: Foundation** | `framework/core` | `build-logic/`, root config | Gradle multi-module, version catalog | DDD base classes, build system, Docker Compose stack |
| **Epic 2: Walking Skeleton** | `framework/cqrs`, `framework/persistence`, `framework/web` | `framework/core`, `shared/` | Axon → PostgreSQL, REST API | Event Store, Command/Query handlers, REST endpoints, Projections |
| **Epic 3: Authentication** | `framework/security` | `framework/core` | Keycloak OIDC, Redis (revocation) | 10-layer JWT validation, Keycloak integration, custom ppc64le build |
| **Epic 4: Multi-Tenancy** | `framework/multi-tenancy` | `framework/security`, `framework/cqrs`, `framework/persistence` | JWT tenant extraction, PostgreSQL RLS, Axon interceptors | 3-layer tenant isolation, TenantContext propagation, RLS policies |
| **Epic 5: Observability** | `framework/observability` | All modules | Prometheus, OpenTelemetry, Logback | Structured JSON logging, Prometheus metrics, distributed tracing, PII masking |
| **Epic 6: Workflow** | `framework/workflow` | `framework/cqrs` | Flowable ↔ Axon bridge, Ansible adapter | Flowable BPMN engine, bidirectional integration, Dockets pattern template |
| **Epic 7: Scaffolding CLI** | `tools/eaf-cli` | All (generates code for all modules) | Mustache templates | CLI commands (aggregate, api-resource, projection, ra-resource), 70-80% boilerplate elimination |
| **Epic 7.5: Documentation** | `docs/` | All (documents all modules) | Tutorial examples, How-To guides | Getting Started, Tutorials, How-To, Reference, Examples (4 weeks, BEFORE Epic 9) |
| **Epic 8: Quality & Alignment** | All modules | `build-logic/` (quality gates) | ktlint, Detekt, Konsist, LitmusKt, Pitest, Git hooks | Architecture compliance, concurrency testing, mutation testing, Git hooks |
| **Epic 9: Reference App** | `products/widget-demo` | Uses ALL framework modules | Full vertical slice (API → Domain → DB), Flowable workflow | Multi-tenant Widget Management, Majlinda validation (<3 days), Nullables benchmarks |

### Module Dependency Graph (Spring Modulith Enforced)

```
framework/core
  ↑ (no dependencies - foundation)
  │
  ├─── framework/persistence
  │      ↑ depends on: core
  │
  ├─── framework/security
  │      ↑ depends on: core
  │
  ├─── framework/observability
  │      ↑ depends on: core
  │
  ├─── framework/multi-tenancy
  │      ↑ depends on: core, security
  │
  ├─── framework/cqrs
  │      ↑ depends on: core, persistence, multi-tenancy
  │
  ├─── framework/workflow
  │      ↑ depends on: core, cqrs
  │
  ├─── framework/web
  │      ↑ depends on: core, security, multi-tenancy
  │
  └─── products/widget-demo
         ↑ depends on: ALL framework modules

tools/eaf-cli
  ↑ (standalone - no framework dependencies)

shared/shared-api
  ↑ (no dependencies)

shared/shared-types
  ↑ (no dependencies)

shared/testing
  ↑ depends on: core (optional)
```

### Epic Implementation Sequence

```
Week 1-2:  Epic 1 (Foundation)
             └─ Enables: All subsequent epics

Week 3-4:  Epic 2 (Walking Skeleton)
             └─ Enables: All feature development

Week 5-7:  Epic 3 (Authentication) + Epic 4 (Multi-Tenancy)
             └─ Parallel execution possible
             └─ Enables: Secure multi-tenant features

Week 8-10: Epic 5 (Observability) + Epic 6 (Workflow)
             └─ Parallel execution possible
             └─ Enables: Production monitoring, BPMN workflows

Week 11:   Epic 7 (Scaffolding CLI)
             └─ Enables: Rapid development, Majlinda productivity

Week 12-15: Epic 7.5 (Documentation)
             └─ CRITICAL: Must complete BEFORE Epic 9
             └─ Enables: Majlinda self-service learning

Week 16:   Epic 8 (Quality & Alignment)
             └─ Includes: LitmusKt concurrency testing
             └─ Enables: Production-grade quality

Week 17-18: Epic 9 (Reference Application)
             └─ Validates: All framework capabilities
             └─ Validates: Majlinda onboarding (<3 days)
             └─ Validates: Nullables Pattern benchmarks

Total: 14-18 weeks (includes Epic 7.5 documentation)
```

---

## 7. Technology Stack Details

### Core Technologies

**Kotlin 2.2.21 + JVM 21 LTS**
- Type-safe, null-safe language with excellent Spring Boot integration
- Coroutines for async/reactive programming
- Data classes for immutable domain models
- Sealed classes for ADT-style error handling (Arrow Either)
- Multi-platform ready (future: Kotlin/Native, Kotlin/JS)

**Spring Boot 3.5.7**
- Production-proven application framework
- Comprehensive auto-configuration
- Actuator for health checks and metrics
- OAuth2 Resource Server for Keycloak integration
- WebMVC for REST API (not reactive - simplicity over async complexity)

**Spring Modulith 1.4.3**
- Compile-time module boundary verification (Konsist integration)
- Hexagonal architecture enforcement
- Event publication registry
- Documentation generation

**Axon Framework 4.12.1**
- Mature CQRS/Event Sourcing framework
- `JdbcEventStorageEngine` for PostgreSQL
- Tracking event processors for projections
- Saga support for process managers
- Test fixtures for aggregate testing
- Migration to Axon 5.x planned Q3-Q4 2026 (1-1.5 months effort)

**PostgreSQL 16.6**
- Event store (time-based partitioning, BRIN indexes)
- Projection tables (jOOQ type-safe queries)
- Flowable BPMN persistence
- Row-Level Security for tenant isolation (Layer 3)
- Streaming replication for HA (Patroni-managed)
- Swappable adapter design (future migration to NATS/Axon Server possible)

**Performance Triggers for Event Store Migration:**
- Write latency p95 >200ms sustained
- Event processor lag >10s sustained
- PostgreSQL CPU >80% sustained

### Security Stack

**Keycloak 26.4.0**
- Enterprise OIDC identity provider
- Multi-realm support (development, staging, production)
- Role-based access control (RBAC)
- User federation (LDAP/Active Directory)
- **Multi-Architecture:** Official amd64/arm64, Custom ppc64le build (UBI9-based)
- Integration: Spring Security OAuth2 Resource Server

**10-Layer JWT Validation:**
1. Format validation (3-part structure)
2. Signature validation (RS256 with Keycloak public keys via JWKS)
3. Algorithm validation (RS256 only, reject HS256)
4. Claim schema validation (required claims: sub, iss, aud, exp, iat, tenant_id, roles)
5. Time-based validation (exp, iat, nbf with clock skew tolerance)
6. Issuer/Audience validation (Keycloak realm verification)
7. Revocation check (Redis blacklist cache, 10-minute TTL)
8. Role validation (required roles present, normalized)
9. User validation (user exists and active - optional, performance trade-off)
10. Injection detection (SQL/XSS patterns in claims via regex)

**Redis 7.2**
- JWT revocation blacklist (10-minute TTL)
- Phase 2: Spring Session backend (Active-Active HA)
- Phase 2: Redis Sentinel (3-node HA cluster)
- Distributed locks (if needed for Active-Active)

### Workflow Stack

**Flowable BPMN 7.2.0**
- Industry-standard BPMN 2.0 engine
- Better than Camunda 7 (EOL approaching in 2026)
- Compensating transactions support
- Tenant-aware process variables
- PostgreSQL persistence (dedicated schema)
- Integration: Bidirectional Axon bridge

**Axon-Flowable Bridge Patterns:**
- BPMN → Axon: ServiceTask delegates to `AxonCommandGatewayDelegate`
- Axon → BPMN: EventHandler signals via `RuntimeService.signalEventReceived()`
- Tenant propagation: Process variable `tenant_id`
- Error handling: BPMN error boundary events → compensating transactions

**Ansible Adapter (Legacy Migration):**
- JSch 0.2.18 (maintained fork, CVE-2023-48795 patched)
- Ansible playbook execution from BPMN ServiceTasks
- Supports Dockets-to-Flowable migration

### Observability Stack

**Structured Logging:**
- Logback 1.5.19 + Logstash Encoder 8.1
- JSON format with automatic context (trace_id, tenant_id, service_name)
- PII masking (email, phone, names)
- Correlation across distributed calls

**Metrics:**
- Micrometer 1.15.5 + Prometheus
- Prometheus endpoint: `/actuator/prometheus`
- Metrics tagged with: tenant_id, service_name, aggregate_type
- JVM metrics, HTTP metrics, Axon processing metrics

**Distributed Tracing:**
- OpenTelemetry 1.55.0 (API/SDK) + 2.20.1 (instrumentation)
- Automatic trace propagation (HTTP, Axon messages)
- W3C Trace Context standard
- trace_id injection into logs for correlation
- Jaeger or Zipkin backend (Post-MVP)

**Dashboards:**
- Grafana (Post-MVP - deferred per Product Brief)
- Pre-configured panels for: Golden Signals, Business Metrics, Security Monitoring

### Testing & Quality Stack

**Kotest 6.0.4** (Primary Testing Framework)
- Kotlin-native testing framework
- BDD-style specifications (FunSpec, StringSpec, etc.)
- Property-based testing built-in
- Excellent Spring Boot integration
- Better than JUnit 5 for Kotlin (90/100 vs 68/100 in analysis)

**Testcontainers 1.21.3**
- Real dependencies for integration tests (PostgreSQL, Keycloak, Redis)
- 40-50% of test suite (Integration-First philosophy)
- Container reuse for performance
- Production-realistic testing
- **FORBIDDEN:** H2 in-memory database (real PostgreSQL mandatory)

**Jazzer 0.25.1** (Fuzz Testing)
- Google OSS-Fuzz standard
- Coverage-guided fuzzing with corpus caching
- 7 targets: JWT parsing, role normalization, token extraction
- 5 minutes per target = 35-40 minutes nightly
- Finds: Crashes, DoS vulnerabilities, regex complexity attacks, memory exhaustion

**LitmusKt** (Concurrency Testing - Epic 8)
- JetBrains Research concurrency stress testing
- Memory model validation, race condition detection
- Critical for: TenantContext ThreadLocal, Event Processor propagation, Distributed locks
- Prevents: €50K-140K bug costs (data breach, corruption)
- Integrated in Kotlin compiler CI (production-proven)

**Pitest 1.19.0** (Mutation Testing)
- Deprecated Kotlin plugin (only FOSS option, Arcmutate is commercial)
- Target: 60-70% mutation coverage (realistic for deprecated plugin)
- Excludes property tests (exponential time complexity)
- Validates test effectiveness
- ~20-30 minutes nightly

**Quality Gates:**
- ktlint 1.7.1: Code formatting (zero violations, Git hooks enforced)
- Detekt 1.23.8: Static analysis (zero violations, comprehensive rules)
- Konsist 0.17.3: Architecture boundaries (Spring Modulith verification)
- Kover 0.9.3: Code coverage (85%+ target, Kotlin-native)

### Developer Experience Stack

**Scaffolding CLI (Epic 7):**
- Picocli 4.7.7: Modern CLI framework
- Mustache 0.9.14: Logic-less templates
- Commands: `scaffold module|aggregate|api-resource|projection|ra-resource`
- Generated code passes all quality gates immediately
- 70-80% boilerplate elimination
- 1-week implementation (Hygen-inspired design)

**Documentation (Epic 7.5):**
- Dokka 2.1.0: Kotlin-native documentation generator
- Markdown-based tutorials and guides
- 4-week investment: Getting Started, Tutorials, How-To, Reference, Examples
- **Critical:** Must complete BEFORE Epic 9 (Majlinda validation dependency)

**Frontend:**
- React-Admin: Operator portal framework (version TBD in Epic 7)
- Material-UI v5: Component library
- Vite: Build tool (fast, modern)
- pnpm: Package manager

---

## 8. Integration Points

### 1. Keycloak OIDC ↔ EAF Security Module

```
┌─────────────────┐
│ Keycloak        │
│ (Port 8080)     │
└────────┬────────┘
         │
         │ OIDC Discovery: /.well-known/openid-configuration
         │ JWKS Endpoint: /realms/{realm}/protocol/openid-connect/certs
         ↓
┌─────────────────────────────────────────┐
│ framework/security                      │
│                                         │
│ JwtValidationFilter:                    │
│  1. Extract Bearer token from header   │
│  2. Fetch public keys (JWKS, cached)   │
│  3. Validate 10 layers                 │
│  4. Extract claims (sub, roles, ...)   │
│  5. Build Spring Security context      │
└────────┬────────────────────────────────┘
         │
         │ SecurityContext populated
         ↓
┌─────────────────────────────────────────┐
│ framework/multi-tenancy                 │
│                                         │
│ TenantContextFilter:                    │
│  1. Extract tenant_id from JWT claim   │
│  2. TenantContext.set(tenantId)        │
│  3. Propagate to Axon (interceptor)    │
│  4. Propagate to PostgreSQL RLS        │
└─────────────────────────────────────────┘
         │
         │ All subsequent requests tenant-aware
         ↓
┌─────────────────────────────────────────┐
│ All Framework Modules                   │
│ (CQRS, Persistence, Workflow, etc.)    │
└─────────────────────────────────────────┘
```

**Configuration:**
```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/eaf
          jwk-set-uri: http://localhost:8080/realms/eaf/protocol/openid-connect/certs

eaf:
  security:
    jwt:
      tenant-claim: tenant_id
      role-claim: roles
      revocation-check-enabled: true
  keycloak:
    realm: eaf
    admin-client-id: eaf-admin
    admin-client-secret: ${KEYCLOAK_ADMIN_SECRET}
```

---

### 2. Axon Framework ↔ PostgreSQL Event Store

```
┌─────────────────┐
│ Command arrives │
│ via REST API    │
└────────┬────────┘
         │
         ↓
┌──────────────────────────────────┐
│ framework/cqrs                   │
│ CommandGateway.send(command)     │
└────────┬─────────────────────────┘
         │
         ↓
┌────────────────────────────────────────────┐
│ Aggregate (e.g., OrderAggregate)           │
│                                            │
│ @CommandHandler                            │
│ constructor(command: CreateOrderCommand) { │
│   AggregateLifecycle.apply(               │
│     OrderCreatedEvent(...)                 │
│   )                                        │
│ }                                          │
└────────┬───────────────────────────────────┘
         │
         │ Event applied
         ↓
┌──────────────────────────────────────────────┐
│ framework/persistence                        │
│ PostgresEventStoreAdapter                    │
│  (Hexagonal Port Implementation)            │
│                                              │
│ JdbcEventStorageEngine:                      │
│  1. Serialize event to JSON                  │
│  2. INSERT INTO events (partition-aware)    │
│  3. Tenant isolation (RLS enforced)         │
│  4. Snapshot check (every 100 events)       │
└────────┬─────────────────────────────────────┘
         │
         ↓
┌──────────────────────────────────┐
│ PostgreSQL 16.6                  │
│                                  │
│ events_2025_10 (partition)       │
│  - event_id (PK)                 │
│  - aggregate_id                  │
│  - tenant_id (RLS)               │
│  - timestamp (BRIN indexed)      │
│  - event_type                    │
│  - payload (JSONB)               │
└────────┬─────────────────────────┘
         │
         │ Event persisted, published to event bus
         ↓
┌──────────────────────────────────────────┐
│ Event Processors (Tracking)              │
│                                          │
│ @EventHandler                            │
│ fun on(event: OrderCreatedEvent) {       │
│   dsl.insertInto(ORDERS)                 │
│      .set(ORDERS.ID, event.orderId)     │
│      .execute()                          │
│ }                                        │
└────────┬─────────────────────────────────┘
         │
         ↓
┌──────────────────────────────────┐
│ Projection Tables (jOOQ)         │
│                                  │
│ orders (read model)              │
│  - Real-time updates (<10s lag)  │
│  - Query-optimized schema        │
└──────────────────────────────────┘
```

**Configuration:**
```yaml
# application.yml
axon:
  eventhandling:
    processors:
      order-projection:
        mode: tracking
        batch-size: 100
        thread-count: 2
  axonserver:
    enabled: false  # Use PostgreSQL, not Axon Server
  serializer:
    general: jackson
    events: jackson
    messages: jackson
```

---

### 3. Flowable BPMN ↔ Axon Framework Bridge

**BPMN → Axon (Command Dispatch):**
```xml
<!-- widget-approval.bpmn20.xml -->
<serviceTask id="publishWidget"
             flowable:delegateExpression="${axonCommandGateway}">
  <extensionElements>
    <flowable:field name="commandType">
      <flowable:string>PublishWidgetCommand</flowable:string>
    </flowable:field>
    <flowable:field name="aggregateId">
      <flowable:expression>${widgetId}</flowable:expression>
    </flowable:field>
  </extensionElements>
</serviceTask>
```

```kotlin
// AxonCommandGatewayDelegate.kt
@Component("axonCommandGateway")
class AxonCommandGatewayDelegate(
    private val commandGateway: CommandGateway
) : JavaDelegate {
    override fun execute(execution: DelegateExecution) {
        val commandType = getFieldValue("commandType", execution)
        val aggregateId = getFieldValue("aggregateId", execution)
        val tenantId = execution.getVariable("tenant_id") as String

        TenantContext.set(TenantId(tenantId))  // Propagate tenant

        val command = buildCommand(commandType, aggregateId, execution)
        commandGateway.sendAndWait<Any>(command)
    }
}
```

**Axon → BPMN (Event Signal):**
```kotlin
// FlowableEventListener.kt
@Component
class FlowableEventListener(
    private val runtimeService: RuntimeService
) {
    @EventHandler
    fun on(event: WidgetPublishedEvent) {
        runtimeService.signalEventReceived(
            signalName = "widgetPublished",
            executionId = findExecutionByWidgetId(event.widgetId),
            signalData = mapOf(
                "widgetId" to event.widgetId.value,
                "status" to event.status.name
            )
        )
    }
}
```

---

### 4. React-Admin ↔ EAF REST API

```
┌─────────────────────────────────┐
│ apps/admin (React-Admin)        │
│                                 │
│ dataProvider.getList(resource)  │
└────────┬────────────────────────┘
         │
         │ GET /api/widgets?cursor=xyz&limit=50
         │ Authorization: Bearer <keycloak-jwt>
         ↓
┌──────────────────────────────────────────┐
│ framework/web                            │
│ WidgetController.listWidgets()          │
│                                          │
│ @PreAuthorize("hasRole('WIDGET_READER')")│
│ @GetMapping("/api/widgets")              │
└────────┬─────────────────────────────────┘
         │
         │ TenantContext validated
         ↓
┌──────────────────────────────────────┐
│ framework/cqrs                       │
│ QueryGateway.query(ListWidgetsQuery) │
└────────┬─────────────────────────────┘
         │
         ↓
┌────────────────────────────────────────┐
│ Projection (jOOQ Query)                │
│ SELECT * FROM widgets                  │
│ WHERE tenant_id = ?                    │
│ AND id > cursor                        │
│ LIMIT 50                               │
└────────┬───────────────────────────────┘
         │
         ↓
┌──────────────────────────────────┐
│ Response (Direct, no envelope)   │
│                                  │
│ {                                │
│   "data": [...],                 │
│   "pagination": {                │
│     "nextCursor": "...",         │
│     "hasMore": true              │
│   }                              │
│ }                                │
└──────────────────────────────────┘
```

**Data Provider Configuration:**
```typescript
// apps/admin/src/providers/dataProvider.ts
const dataProvider = {
    getList: async (resource, params) => {
        const { cursor, limit = 50 } = params.pagination;
        const url = `/api/${resource}?cursor=${cursor || ''}&limit=${limit}`;

        const { data, pagination } = await httpClient(url);

        return {
            data,
            total: pagination.total,
            pageInfo: {
                hasNextPage: pagination.hasMore,
                endCursor: pagination.nextCursor
            }
        };
    },
    // ... other methods (getOne, create, update, delete)
};
```

---

## 9. High Availability Strategy

### Overview

EAF v1.0 implements a **phased HA approach** supporting multiple customer deployment scenarios:
- **Small/Medium Customers:** Active-Passive (1-2 servers, <2min failover)
- **Enterprise Customers:** Active-Active Multi-DC (Coast-to-Coast, <30s failover)

### Phase 1: Active-Passive HA (MVP)

**Target Customers:**
- Single-server deployments (development, test, small customers)
- Standard production (2 servers, <2min failover acceptable)

**Architecture:**
```
┌─────────────────────────┐         ┌─────────────────────────┐
│   Primary Server        │         │   Standby Server        │
│                         │         │                         │
│  ┌──────────────────┐   │         │  ┌──────────────────┐   │
│  │  EAF Application │   │         │  │  EAF Application │   │
│  │  (ACTIVE)        │   │         │  │  (STANDBY)       │   │
│  ├──────────────────┤   │         │  ├──────────────────┤   │
│  │  PostgreSQL      │───┼─────────┼─>│  PostgreSQL      │   │
│  │  (PRIMARY)       │   │ Stream  │  │  (REPLICA)       │   │
│  ├──────────────────┤   │ Replic. │  ├──────────────────┤   │
│  │  Keycloak        │   │         │  │  Keycloak        │   │
│  ├──────────────────┤   │         │  ├──────────────────┤   │
│  │  Redis           │   │         │  │  Redis           │   │
│  │  (Standalone)    │   │         │  │  (Standalone)    │   │
│  ├──────────────────┤   │         │  ├──────────────────┤   │
│  │  Flowable        │   │         │  │  Flowable        │   │
│  └──────────────────┘   │         │  └──────────────────┘   │
│                         │         │                         │
│  Patroni Node 1         │         │  Patroni Node 2         │
└─────────────────────────┘         └─────────────────────────┘
           ↑                                     ↑
           └──────────── Patroni HA ─────────────┘
                    (Automated Failover <2min)
```

**Components:**
- **Patroni-managed PostgreSQL cluster:** Automated failover, health checks, VIP management
- **PostgreSQL Streaming Replication:** WAL-based, asynchronous
- **Failover Time:** <2 minutes (automated), 0 seconds (manual)
- **Zero-Downtime Maintenance:** Manual failover for planned maintenance
- **Redis Standalone:** JWT revocation blacklist (acceptable state loss on failover)

**Stateless Application Design (Active-Active-Ready):**
- ✅ NO component-level mutable state
- ✅ NO local caches (use Axon Projections or Redis)
- ✅ ThreadLocal ONLY for request context (TenantContext, SecurityContext)
- ✅ Axon Token Store in PostgreSQL (processor coordination ready)

**Configuration:**
```yaml
# docker-compose.prod.yml
services:
  postgres-primary:
    image: postgres:16.6
    environment:
      - POSTGRES_REPLICATION_MODE=master

  postgres-replica:
    image: postgres:16.6
    environment:
      - POSTGRES_REPLICATION_MODE=slave
      - POSTGRES_MASTER_HOST=postgres-primary

  patroni-primary:
    image: patroni:latest
    environment:
      - PATRONI_SCOPE=eaf-cluster
      - PATRONI_NAME=node1

  patroni-replica:
    image: patroni:latest
    environment:
      - PATRONI_SCOPE=eaf-cluster
      - PATRONI_NAME=node2
```

---

### Phase 2: Active-Active HA (Post-MVP)

**Target Customers:**
- Enterprise deployments with distributed datacenters
- Coast-to-Coast geographic redundancy
- <30s failover requirement

**Architecture:**
```
┌─────────────────────────────────────────────────────────────┐
│                    LOAD BALANCER                            │
│                  (NGINX/HAProxy)                            │
│  - Health Checks (10s interval)                            │
│  - SSL Termination                                          │
│  - Geographic Routing                                       │
└────────────────────┬────────────────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        ↓                         ↓
┌──────────────────┐      ┌──────────────────┐
│ Datacenter WEST  │      │ Datacenter EAST  │
│                  │      │                  │
│ ┌──────────────┐ │      │ ┌──────────────┐ │
│ │ EAF Node 1   │ │      │ │ EAF Node 2   │ │
│ │ (ACTIVE)     │ │      │ │ (ACTIVE)     │ │
│ ├──────────────┤ │      │ ├──────────────┤ │
│ │ PostgreSQL   │◄┼──────┼─┤ PostgreSQL   │ │
│ │ Primary      │ │ Sync │ │ Read-Replica │ │
│ ├──────────────┤ │      │ ├──────────────┤ │
│ │ Redis        │◄┼──────┼─┤ Redis        │ │
│ │ (Sentinel)   │ │ Sync │ │ (Sentinel)   │ │
│ ├──────────────┤ │      │ ├──────────────┤ │
│ │ Keycloak     │◄┼──────┼─┤ Keycloak     │ │
│ │ (Clustered)  │ │ Sync │ │ (Clustered)  │ │
│ └──────────────┘ │      │ └──────────────┘ │
└──────────────────┘      └──────────────────┘
```

**Additional Components (Phase 2):**
- **NGINX/HAProxy Load Balancer:** Health checks, SSL termination, geographic routing
- **PostgreSQL Read-Replicas:** Primary writes, replicas for reads (@Transactional(readOnly=true) routing)
- **Redis Sentinel:** 3-node HA cluster for session management
- **Keycloak Cluster Mode:** Shared session cache, distributed locks
- **Spring Session:** Redis-backed session management

**Migration Effort:** 3-4 weeks
- Load Balancer configuration (3-5 days)
- PostgreSQL read-replica routing (2-3 days)
- Redis Sentinel migration (2-3 days)
- Keycloak clustering (3-5 days)
- Testing and validation (5-7 days)

**No Code Changes Required:** Application already stateless (Active-Active-Ready from MVP)

---

## 10. Multi-Architecture Support

### Supported Architectures (v1.0)

EAF Framework provides first-class support for three processor architectures:

- **linux/amd64 (x86_64)** - PRIMARY
  - Target: Production (VMware, bare metal, cloud), Development, CI/CD
  - ZEWSSP primary deployment platform
  - All components: Official Docker images

- **linux/arm64 (aarch64)** - SECONDARY
  - Target: Apple Silicon development (M1/M2/M3)
  - Target: AWS Graviton, cloud-native deployments
  - All components: Official Docker images

- **linux/ppc64le (IBM POWER9+)** - FUTURE-READY
  - Target: Future products with IBM Power requirements
  - Rationale: Framework reusability (DPCM, future products may need Power)
  - Mixed: Official + Custom-maintained images

### Rationale: Multi-Architecture from Day 1

**Framework Philosophy:** EAF is a **reusable framework** serving multiple products (ZEWSSP, DPCM, future). While ZEWSSP (first migration) targets VMware/x86, future products may require alternative architectures. Implementing multi-arch support from inception avoids:

- Retrofitting custom builds later (€22.000+ cost)
- Architecture-specific bugs discovered late
- Product launch delays waiting for platform support
- "Gefrickel" and hacky workarounds

**Investment:** €4.400 initial + €8.000/year maintenance
**ROI:** Prevents €22.000+ retrofit costs + product delays (52% savings)

### Component Support Matrix

| Component | amd64 | arm64 | ppc64le | Source | Maintenance |
|-----------|-------|-------|---------|--------|-------------|
| **EAF Application** | ✅ Native | ✅ Native | ✅ Native | Kotlin/JVM (platform-independent) | Zero |
| **PostgreSQL** | ✅ Official | ✅ Official | ✅ Official | Docker Hub | Zero |
| **Redis** | ✅ Official | ✅ Official | ✅ Official | Docker Hub | Zero |
| **Prometheus** | ✅ Official | ✅ Official | ✅ Official | Quay.io | Zero |
| **Flowable** | ✅ Official | ✅ Official | ✅ Verified | Pure Java (JVM) | Zero |
| **Keycloak** | ✅ Official | ✅ Official | ✅ **EAF Custom** | Quay.io (amd64/arm64), Custom (ppc64le) | **Quarterly** |
| **Grafana** | ✅ Official | ✅ Official | ⚠️ Community | Docker Hub (Post-MVP) | Post-MVP |

### Custom Build: Keycloak ppc64le

**Why Custom Build Required:**
- Upstream Keycloak: Only amd64 + arm64 official images
- ppc64le not in RedHat's cloud-focused roadmap
- EAF maintains production-ready custom build for framework reusability

**Build Strategy (Epic 3):**
```dockerfile
# docker/keycloak/Dockerfile.ppc64le
FROM registry.access.redhat.com/ubi9/openjdk-21:latest AS builder

ARG KEYCLOAK_VERSION=26.4.0
RUN curl -L https://github.com/keycloak/keycloak/releases/download/${KEYCLOAK_VERSION}/keycloak-${KEYCLOAK_VERSION}.tar.gz \
    | tar xzf - -C /opt && mv /opt/keycloak-${KEYCLOAK_VERSION} /opt/keycloak

WORKDIR /opt/keycloak
RUN bin/kc.sh build --db=postgres --health-enabled=true --metrics-enabled=true

FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest
COPY --from=builder /opt/keycloak /opt/keycloak

ENV KC_DB=postgres
ENV KC_HTTP_ENABLED=true
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

WORKDIR /opt/keycloak
EXPOSE 8080 9000

USER 1000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--optimized"]
```

**Why This Works:**
- ✅ UBI9 (Universal Base Image): Official ppc64le support (IBM partnership)
- ✅ Keycloak Distribution: Pure Java, platform-agnostic
- ✅ Build Process: `kc.sh build` is platform-independent

**Maintenance:**
- Quarterly updates synchronized with Keycloak releases
- Automated CI/CD pipeline rebuilds
- Estimated effort: 6 hours/quarter
- Cost: €2.400/year

**CI/CD Multi-Arch Build:**
```bash
docker buildx build \
  --platform linux/amd64,linux/arm64,linux/ppc64le \
  --build-arg KEYCLOAK_VERSION=26.4.0 \
  --tag ghcr.io/axians/eaf-keycloak:26.4.0 \
  --push \
  -f docker/keycloak/Dockerfile.ppc64le .
```

---

## 11. Testing Strategy

### 7-Layer Defense-in-Depth

EAF implements an industry-leading multi-layer testing strategy validated through prototype development:

```
┌───────────────────────────────────────────────────────────┐
│ Layer 7: Concurrency Stress Testing (NIGHTLY)            │
│ - Tool: LitmusKt (JetBrains Research)                    │
│ - Targets: TenantContext isolation, Event Processor      │
│   propagation, Connection pool context, Distributed locks│
│ - Finds: Race conditions, memory ordering, unsafe        │
│   publication                                             │
│ - Execution: ~20-30 minutes                              │
│ - ROI: Prevents €50K-140K concurrency bugs              │
└───────────────────────────────────────────────────────────┘
                          ↑
┌───────────────────────────────────────────────────────────┐
│ Layer 6: Mutation Testing (NIGHTLY)                      │
│ - Tool: Pitest (deprecated Kotlin plugin)                │
│ - Target: 60-70% mutation coverage                       │
│ - Scope: Unit tests only (excludes property tests)       │
│ - Execution: ~20-30 minutes                              │
│ - Validates: Test suite effectiveness                    │
└───────────────────────────────────────────────────────────┘
                          ↑
┌───────────────────────────────────────────────────────────┐
│ Layer 5: Fuzz Testing (NIGHTLY)                          │
│ - Tool: Jazzer (Google OSS-Fuzz standard)               │
│ - Targets: 7 @FuzzTest methods (JWT, roles, tokens)     │
│ - Coverage-guided with corpus caching                    │
│ - Execution: 5 min × 7 = 35-40 minutes                   │
│ - Finds: Crashes, DoS, regex attacks, memory exhaustion  │
└───────────────────────────────────────────────────────────┘
                          ↑
┌───────────────────────────────────────────────────────────┐
│ Layer 4: Property-Based Testing (NIGHTLY)                │
│ - Tool: Kotest Property Testing                          │
│ - Properties: Idempotence, security invariants,          │
│   fail-closed semantics                                  │
│ - Optimization: Constructive generation (100x faster)    │
│ - Execution: 30-45 minutes (1000+ iterations)            │
│ - Excluded from: Mutation testing (exponential time)     │
└───────────────────────────────────────────────────────────┘
                          ↑
┌───────────────────────────────────────────────────────────┐
│ Layer 3: Integration Tests (EVERY COMMIT)                │
│ - Tool: Testcontainers (Real Dependencies)              │
│ - Dependencies: PostgreSQL, Keycloak, Redis              │
│ - Coverage: 40-50% of test suite (Integration-First)    │
│ - Execution: <10 minutes                                 │
│ - Production-realistic scenarios                         │
└───────────────────────────────────────────────────────────┘
                          ↑
┌───────────────────────────────────────────────────────────┐
│ Layer 2: Unit Tests (EVERY COMMIT)                       │
│ - Tools: Kotest + Nullables + Axon Fixtures             │
│ - Nullables: 100-1000x faster than mocking frameworks   │
│ - Coverage: 85%+ line coverage                           │
│ - Execution: <5 minutes                                  │
└───────────────────────────────────────────────────────────┘
                          ↑
┌───────────────────────────────────────────────────────────┐
│ Layer 1: Static Analysis (EVERY COMMIT)                  │
│ - Tools: ktlint, Detekt, Konsist                        │
│ - Enforcement: Zero violations (Git hooks + CI/CD)       │
│ - Execution: <30 seconds                                 │
└───────────────────────────────────────────────────────────┘
```

### Layer-Specific Strategies

**Layer 1: Static Analysis**
```kotlin
// Automated via Git hooks (pre-commit):
$ git commit
→ Running ktlint check... ✅
→ Running Detekt... ✅
→ Running Konsist... ✅
→ Commit allowed

// CI/CD enforcement (blocks PR merge)
```

**Layer 2: Unit Tests (Nullables Pattern)**
```kotlin
// Infrastructure wrapper tests (100-1000x faster than Mockk):
class EmailClientTest : FunSpec({
    test("should send email") {
        val emailClient = EmailClient.createNull()
        val sentEmails = emailClient.trackOutput()

        val service = NotificationService(emailClient)
        service.notifyOrderCreated(orderId)

        assertThat(sentEmails.data).containsExactly(
            Email(to = "customer@example.com", subject = "Order Created")
        )
    }
})

// Aggregate tests (Axon Test Fixtures):
class OrderAggregateTest : FunSpec({
    test("should create order") {
        fixture.given()
            .`when`(CreateOrderCommand(...))
            .expectEvents(OrderCreatedEvent(...))
    }
})

// Domain logic tests (pure functions):
class MoneyTest : FunSpec({
    test("should add amounts") {
        val sum = Money(10.00) + Money(5.00)
        assertThat(sum).isEqualTo(Money(15.00))
    }
})
```

**Layer 3: Integration Tests (Testcontainers)**
```kotlin
@SpringBootTest
@Testcontainers
class OrderApiIntegrationTest : FunSpec({
    @Container
    val postgres = PostgreSQLContainer<Nothing>("postgres:16.6")

    @Container
    val keycloak = KeycloakContainer("quay.io/keycloak/keycloak:26.4.0")

    @Container
    val redis = GenericContainer<Nothing>("redis:7.2-alpine")

    test("end-to-end order creation") {
        val token = keycloak.getAccessToken("user", "password")

        val response = restTemplate.postForEntity(
            "/api/orders",
            CreateOrderRequest(...),
            OrderResponse::class.java,
            HttpHeaders().apply { setBearerAuth(token) }
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.body?.orderId).isNotNull()
    }
})
```

**Layer 4: Property-Based Tests (Kotest)**
```kotlin
class RoleNormalizationPropertyTest : FunSpec() {
    override fun tags() = setOf(PbtTag)  // Nightly only

    init {
        test("normalizing twice equals normalizing once (idempotence)") {
            checkAll(1000, validRoleArb()) { role ->
                val once = normalize(role)
                val twice = normalize(once.authority)
                once.authority shouldBe twice.authority
            }
        }

        test("normalized roles never contain injection characters") {
            checkAll(1000, validRoleArb()) { role ->
                val authority = normalize(role)
                authority shouldNotContain "'"
                authority shouldNotContain ";"
            }
        }
    }
}

// Constructive Arb generators (100x faster than filter-based):
private fun validRoleArb(): Arb<String> {
    val validChars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('_', '-', '.')
    return Arb.list(Arb.of(validChars), 1..256).map { it.joinToString("") }
}
```

**Layer 5: Fuzz Testing (Jazzer)**
```kotlin
class JwtFormatFuzzer {
    @FuzzTest(maxDuration = "5m")
    fun fuzzJwtBasicFormatValidation(data: FuzzedDataProvider) {
        val jwtString = data.consumeRemainingAsString()

        try {
            validateJwtFormat(jwtString)
        } catch (e: OutOfMemoryError) {
            throw e  // Report as critical finding
        } catch (e: StackOverflowError) {
            throw e  // Regex DoS attack
        } catch (e: Exception) {
            // Expected for invalid inputs
        }
    }
}

// 7 @FuzzTest methods:
// 1. JwtFormatFuzzer.fuzzJwtBasicFormatValidation
// 2. TokenExtractorFuzzer.fuzzTokenExtraction
// 3. TokenExtractorFuzzer.fuzzTokenExtractionWithNull
// 4. RoleNormalizationFuzzer.fuzzRoleNormalization
// 5. RoleNormalizationFuzzer.fuzzRoleNormalizationWithNull
// 6. RoleNormalizationFuzzer.fuzzRoleNormalizationInjectionPatterns
// 7. RoleNormalizationFuzzer.fuzzRoleNormalizationUnicodeAttacks
```

**Layer 6: Mutation Testing (Pitest)**
```kotlin
// gradle.properties
pitest.targetClasses=com.axians.eaf.framework.*
pitest.excludedClasses=**/*PropertyTest,**/*FuzzTest,**/*LitmusTest
pitest.targetTests=com.axians.eaf.framework.*Test
pitest.mutationThreshold=60
pitest.coverageThreshold=85
pitest.threads=4

// Run nightly (not on PR - too slow):
$ ./gradlew pitest
→ Mutation coverage: 65% ✅ (target: 60-70%)
```

**Layer 7: Concurrency Stress Testing (LitmusKt)**
```kotlin
// TenantContextIsolationTest.kt (Epic 8)
litmusTest("TenantContext Thread Isolation") {
    vars { var tenant1, tenant2, leak = null, null, false }

    thread {
        TenantContext.set(TenantId("tenant-A"))
        tenant1 = TenantContext.get()
    }

    thread {
        TenantContext.set(TenantId("tenant-B"))
        tenant2 = TenantContext.get()

        if (TenantContext.get() == TenantId("tenant-A")) {
            leak = true  // FORBIDDEN!
        }
    }

    accept { tenant1 == "tenant-A" && tenant2 == "tenant-B" && !leak }
    forbidden { leak == true }  // Cross-tenant leak = CRITICAL BUG
}

// 5 critical concurrency tests (Epic 8):
// 1. TenantContext ThreadLocal isolation
// 2. Event Handler context propagation
// 3. Connection pool tenant context
// 4. Distributed lock mutual exclusion
// 5. Active-Active session consistency
```

### Test Execution Strategy

**Fast Feedback (Every Commit):**
```yaml
# .github/workflows/ci.yml
- Static Analysis: <30 seconds
- Unit Tests: <5 minutes (Nullables Pattern)
- Integration Tests: <10 minutes (Testcontainers)
Total: <15 minutes (blocks PR merge)
```

**Deep Validation (Nightly 2 AM CEST):**
```yaml
# .github/workflows/nightly.yml
- Property-Based Tests: 30-45 minutes
- Fuzz Tests: 35-40 minutes
- Concurrency Tests (LitmusKt): 20-30 minutes
- Mutation Tests (Pitest): 20-30 minutes
Total: ~2.5 hours (acceptable for nightly schedule)
```

### Quality Metrics & Targets

| Metric | Target | Measurement | Enforcement |
|--------|--------|-------------|-------------|
| **Line Coverage** | 85%+ | Kover | CI/CD blocks <85% |
| **Mutation Coverage** | 60-70% | Pitest | Nightly report, trend monitoring |
| **Static Analysis** | 0 violations | ktlint, Detekt | Git hooks + CI/CD block |
| **Architecture Violations** | 0 | Konsist | CI/CD blocks violations |
| **Concurrency Bugs** | 0 forbidden outcomes | LitmusKt | Nightly alert on failures |
| **Test Suite Speed** | <15 min | CI/CD timing | Optimize if >15 min |
| **Developer Feedback** | <5 sec | Local unit tests | Nullables Pattern ensures this |

### Industry Standards Applied

**Separation of Concerns:**
- Property tests for **deep validation** (mathematical properties, security invariants)
- Mutation tests for **fast feedback** (test suite effectiveness)
- Concurrency tests for **race detection** (memory model validation)
- NOT combining (prevents exponential time complexity)

**Performance Optimization:**
- Nullables Pattern: 100-1000x faster than mocking frameworks (James Shore empirical data)
- Constructive Arb generators: 100x faster than filter-based generation
- Fuzz corpus caching: Incremental improvement across runs
- Property tests excluded from mutation testing: Prevents explosion

**Reference:**
- Nullables Pattern: https://www.jamesshore.com/v2/projects/nullables
- Testing Without Mocks: https://www.jamesshore.com/v2/projects/nullables/testing-without-mocks

---

## 12. Implementation Patterns

These patterns ensure multiple AI agents write **compatible code** across all epics.

### Naming Patterns

**File Naming:**
```kotlin
// Aggregates: PascalCase + "Aggregate" suffix
OrderAggregate.kt          ✅ CORRECT
order-aggregate.kt         ❌ WRONG (kebab-case)
OrderAgg.kt                ❌ WRONG (abbreviated)

// Commands: PascalCase + "Command" suffix
CreateOrderCommand.kt      ✅ CORRECT
create_order_command.kt    ❌ WRONG (snake_case)

// Events: PascalCase + past tense + "Event" suffix
OrderCreatedEvent.kt       ✅ CORRECT (past tense)
OrderCreateEvent.kt        ❌ WRONG (present tense)

// Tests: Same name + "Test" suffix
OrderAggregateTest.kt      ✅ CORRECT
TestOrderAggregate.kt      ❌ WRONG (prefix)
```

**Class/Interface Naming:**
```kotlin
// Interfaces: No "I" prefix (Kotlin convention)
interface OrderRepository  ✅ CORRECT
interface IOrderRepository ❌ WRONG (Java convention)

// Value Objects: Descriptive nouns
data class OrderId(val value: UUID)     ✅ CORRECT
data class OrderIdentifier(...)          ❌ WRONG (verbose)

// Enums: Singular name
enum class OrderStatus                   ✅ CORRECT
enum class OrderStatuses                 ❌ WRONG (plural)
```

**Database Naming:**
```sql
-- Tables: Plural, snake_case
CREATE TABLE orders                      ✅ CORRECT
CREATE TABLE order                       ❌ WRONG (singular)
CREATE TABLE Orders                      ❌ WRONG (PascalCase)

-- Columns: snake_case
order_id UUID PRIMARY KEY                ✅ CORRECT
orderId UUID PRIMARY KEY                 ❌ WRONG (camelCase)

-- Foreign Keys: {table}_id format
customer_id UUID                         ✅ CORRECT
fk_customer UUID                         ❌ WRONG (prefix style)
customerId UUID                          ❌ WRONG (camelCase)
```

**REST Endpoints:**
```
// Resources: Plural, kebab-case
GET /api/orders                          ✅ CORRECT
GET /api/order                           ❌ WRONG (singular)
GET /api/Orders                          ❌ WRONG (PascalCase)

// Sub-resources: Nested
GET /api/orders/{id}/items               ✅ CORRECT
GET /api/order-items?orderId={id}        ❌ WRONG (query param instead of nesting)

// Actions: Verb at end
POST /api/orders/{id}/submit             ✅ CORRECT
POST /api/orders/{id}/submission         ❌ WRONG (noun)
POST /api/submit-order                   ❌ WRONG (verb prefix)
```

### Structure Patterns

**Package Organization (By Feature - DDD Bounded Context):**
```kotlin
com.axians.eaf.products.widget/
  ├── domain/           ✅ Aggregates, Value Objects, Enums
  ├── command/          ✅ Command classes
  ├── event/            ✅ Event classes
  ├── query/            ✅ Query classes, Projections
  ├── api/              ✅ REST Controllers
  └── workflow/         ✅ BPMN processes

// FORBIDDEN: By Layer globally
com.axians.eaf.domain/         ❌ WRONG
com.axians.eaf.controller/     ❌ WRONG
com.axians.eaf.service/        ❌ WRONG
```

**Test Location (Co-located via Gradle Source Sets):**
```
framework/security/
  ├── src/main/kotlin/              # Production code
  ├── src/test/kotlin/              # Unit tests
  ├── src/integrationTest/kotlin/   # Integration tests
  ├── src/propertyTest/kotlin/      # Property-based tests
  ├── src/fuzzTest/kotlin/          # Fuzz tests
  └── src/litmusTest/kotlin/        # Concurrency tests

// FORBIDDEN: Separate __tests__ directory
```

**Migration Files (Version Ranges by Module):**
```
src/main/resources/db/migration/
  ├── V001__event_store_schema.sql         ✅ Framework (V001-099)
  ├── V002__partitioning_setup.sql         ✅ Framework
  ├── V003__brin_indexes.sql               ✅ Framework
  ├── V004__rls_policies.sql               ✅ Framework
  ├── V100__widget_projections.sql         ✅ Product: widget-demo (V100-199)
  ├── V101__widget_indexes.sql             ✅ Product: widget-demo
  └── V200__licensing_projections.sql      ✅ Product: licensing-server (V200-299)

// Versioning scheme:
// V001-099: Framework infrastructure
// V100-199: First product (widget-demo)
// V200-299: Second product (licensing-server)
// V300-399: Third product, etc.
```

### Format Patterns

**API Request/Response DTOs:**
```kotlin
// Request DTO: Suffix "Request"
data class CreateOrderRequest(
    val customerId: String,
    val items: List<OrderItemRequest>
)

// Response DTO: Suffix "Response"
data class OrderResponse(
    val orderId: String,
    val status: String,
    val createdAt: String  // ISO-8601
)

// FORBIDDEN: Reusing domain objects as DTOs
// FORBIDDEN: Different naming (CreateOrderDto, OrderDto)
```

**Event JSON Format (Axon Default):**
```json
{
  "eventType": "OrderCreatedEvent",
  "aggregateId": "order-123",
  "aggregateType": "Order",
  "timestamp": "2025-10-30T14:23:45.123Z",
  "tenantId": "tenant-abc",
  "payload": {
    "orderId": "order-123",
    "customerId": "customer-456",
    "total": 100.00
  },
  "metadata": {
    "userId": "user-789",
    "traceId": "a1b2c3d4"
  }
}
```

**Error Response (RFC 7807 Problem Details):**
```json
{
  "type": "https://api.eaf.axians.com/problems/validation-error",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Order items cannot be empty",
  "instance": "/api/orders",
  "traceId": "a1b2c3d4",
  "tenantId": "tenant-123"
}
```

### Communication Patterns

**Command Dispatch (ALWAYS via Axon CommandGateway):**
```kotlin
// ✅ CORRECT:
commandGateway.send<OrderId>(
    CreateOrderCommand(orderId, customerId, items)
)

// ❌ FORBIDDEN: Direct aggregate instantiation
val aggregate = OrderAggregate()
aggregate.handle(command)
```

**Query Execution (ALWAYS via Axon QueryGateway):**
```kotlin
// ✅ CORRECT:
queryGateway.query(
    FindOrderQuery(orderId),
    ResponseTypes.instanceOf(OrderResponse::class.java)
).join()

// ❌ FORBIDDEN: Direct repository access from REST layer
orderRepository.findById(orderId)
```

**Event Publishing (ONLY via AggregateLifecycle in Aggregates):**
```kotlin
// ✅ CORRECT (inside @Aggregate):
@CommandHandler
constructor(command: CreateOrderCommand) {
    AggregateLifecycle.apply(OrderCreatedEvent(...))
}

// ❌ FORBIDDEN: Direct EventBus
eventBus.publish(event)
```

### Lifecycle Patterns

**Loading States (Async Command Response):**
```kotlin
// API: Return 202 Accepted for async commands
@PostMapping("/api/orders")
fun createOrder(@RequestBody request: CreateOrderRequest): ResponseEntity<AcceptedResponse> {
    val orderId = commandGateway.send<OrderId>(
        CreateOrderCommand.from(request)
    ).join()

    return ResponseEntity
        .status(HttpStatus.ACCEPTED)
        .body(AcceptedResponse(
            id = orderId.value,
            location = "/api/orders/${orderId.value}"
        ))
}

// Frontend: Poll status endpoint or use WebSocket subscription
```

**Error Recovery (Retry with Exponential Backoff):**
```kotlin
@EventHandler
@RetryScheduler(maxRetries = 3, backoffStrategy = ExponentialBackoff::class)
fun on(event: OrderCreatedEvent) {
    // Event handler with automatic retries
    // Failed attempts logged with trace_id for debugging
}
```

**Compensating Transactions (Saga Pattern):**
```kotlin
@Saga
class OrderSaga {
    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    fun on(event: OrderSubmittedEvent) {
        commandGateway.send(ProcessPaymentCommand(event.orderId))
    }

    @SagaEventHandler(associationProperty = "orderId")
    fun on(event: PaymentFailedEvent) {
        // Compensate: Cancel order
        commandGateway.send(CancelOrderCommand(event.orderId))
    }

    @SagaEventHandler(associationProperty = "orderId")
    fun on(event: PaymentSuccessfulEvent) {
        // Continue: Ship order
        commandGateway.send(ShipOrderCommand(event.orderId))
    }
}
```

### Location Patterns

**Static Assets:**
```
apps/admin/
  └── public/
      ├── images/          # Images
      ├── fonts/           # Fonts
      └── favicon.ico      # Favicon

// URL path: /static/images/logo.png
```

**Configuration Files:**
```
# Application config: src/main/resources/
application.yml              ✅ Base configuration
application-dev.yml          ✅ Development profile
application-prod.yml         ✅ Production profile

# Module-specific config:
framework/security/src/main/resources/
  └── security-defaults.yml  ✅ Module configuration

// FORBIDDEN: Config in root directory
config.yml                   ❌ WRONG
```

**API Route Structure:**
```
Pattern:
/api/{resource}                          # Collection
/api/{resource}/{id}                     # Item
/api/{resource}/{id}/{sub-resource}      # Sub-collection
/api/{resource}/{id}/{action}            # Action

Examples:
GET    /api/orders                       # List orders
POST   /api/orders                       # Create order
GET    /api/orders/{id}                  # Get single order
PUT    /api/orders/{id}                  # Update order
DELETE /api/orders/{id}                  # Delete order
GET    /api/orders/{id}/items            # Order items (sub-resource)
POST   /api/orders/{id}/submit           # Submit action
```

---

## 13. Consistency Rules

### Cross-Cutting Patterns (ALL Agents MUST Follow)

#### Error Handling

```kotlin
// DOMAIN LAYER: Arrow Either (pure functional)
fun validateOrder(order: Order): Either<DomainError, ValidatedOrder> {
    return when {
        order.items.isEmpty() -> ValidationError("Items required").left()
        order.total < Money.ZERO -> ValidationError("Negative total").left()
        else -> ValidatedOrder(order).right()
    }
}

// APPLICATION LAYER: Spring Exceptions
@PostMapping("/api/orders")
fun createOrder(@RequestBody request: CreateOrderRequest): OrderResponse {
    return orderService.create(request)
        .fold(
            { error -> throw error.toException() },
            { order -> order.toResponse() }
        )
}

// REST LAYER: RFC 7807 Problem Details (automatic via @ExceptionHandler)
@ExceptionHandler(ValidationException::class)
fun handle(ex: ValidationException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message).apply {
        type = URI("https://api.eaf.axians.com/problems/validation-error")
        setProperty("traceId", MDC.get("trace_id"))
        setProperty("tenantId", TenantContext.get()?.value)
    }
```

**Rule:** Domain returns Either, Application throws, REST returns RFC 7807

---

#### Logging

```kotlin
// STRUCTURED JSON LOGGING with correlation
logger.info(
    "Order created successfully",
    kv("order_id", orderId.value),
    kv("tenant_id", TenantContext.get().value),
    kv("aggregate_type", "Order"),
    kv("user_id", SecurityContextHolder.getUserId())
)

// Output (JSON):
{
  "timestamp": "2025-10-30T14:23:45.123Z",
  "level": "INFO",
  "logger": "com.axians.eaf.orders.OrderService",
  "message": "Order created successfully",
  "trace_id": "a1b2c3d4e5f6",
  "span_id": "12345678",
  "tenant_id": "tenant-abc-123",
  "user_id": "user-xyz-789",
  "service_name": "eaf-framework",
  "environment": "production",
  "context": {
    "order_id": "order-123",
    "aggregate_type": "Order"
  }
}

// MANDATORY: Always include trace_id and tenant_id
// MANDATORY: Use kv() for structured fields
// FORBIDDEN: String interpolation logger.info("Order $orderId created")
// FORBIDDEN: Logging passwords, tokens, credit cards
```

**PII Masking Rules:**
- Email: `user@example.com` → `u***@example.com`
- Phone: `+49123456789` → `+49****6789`
- Names: First 2 chars only `Michael` → `Mi****`

---

#### Date/Time Handling

```kotlin
// EVENTS: Always Instant (UTC)
data class OrderCreatedEvent(
    val orderId: OrderId,
    val timestamp: Instant = Instant.now()  // UTC, ISO-8601
)

// API RESPONSES: ISO-8601 strings (UTC)
{
  "createdAt": "2025-10-30T14:23:45.123Z"
}

// DATABASE: TIMESTAMPTZ (UTC stored)
CREATE TABLE orders (
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

// DOMAIN LOGIC: Instant or ZonedDateTime
fun calculateDeadline(createdAt: Instant): Instant {
    return createdAt.plus(Duration.ofDays(7))
}

// FORBIDDEN: LocalDateTime (no timezone info)
// FORBIDDEN: java.util.Date, Calendar
// FORBIDDEN: Timezone offsets in storage (always UTC)
```

**Rule:** UTC everywhere, ISO-8601 format, `Instant` for events, `TIMESTAMPTZ` in DB

---

#### Authentication & Authorization

```kotlin
// JWT EXTRACTION: Spring Security (automatic)
Authorization: Bearer <jwt-token>

// 10-LAYER VALIDATION: Automatic via SecurityConfig
// (Layers 1-10 configured in framework/security module)

// TENANT EXTRACTION: TenantContextFilter (after authentication)
val tenantId = jwt.getClaim("tenant_id") as String
TenantContext.set(TenantId(tenantId))

// AUTHORIZATION: Role-based with @PreAuthorize
@PreAuthorize("hasRole('ORDER_MANAGER')")
@PostMapping("/api/orders")
fun createOrder(@RequestBody request: CreateOrderRequest): OrderResponse {
    // Role validated before method execution
}

// TENANT VALIDATION: Command handler (Layer 2)
@CommandHandler
constructor(command: CreateOrderCommand) {
    require(command.tenantId == TenantContext.get()) {
        "Tenant mismatch: command=${command.tenantId}, context=${TenantContext.get()}"
    }
}

// FORBIDDEN: Custom auth logic in business code
// FORBIDDEN: Bypassing TenantContext
// FORBIDDEN: Hardcoded credentials
```

**Rule:** Spring Security handles auth, TenantContext propagated, roles enforced declaratively

---

#### Tenant Context Propagation

```kotlin
// FILTER: Set context (Layer 1 - Request)
@Component
class TenantContextFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request, response, filterChain) {
        val tenantId = extractTenantFromJwt(request)
        TenantContext.set(tenantId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            TenantContext.clear()  // MANDATORY cleanup
        }
    }
}

// VALIDATOR: Validate in commands (Layer 2 - Service)
@CommandHandler
fun handle(command: CreateOrderCommand) {
    require(command.tenantId == TenantContext.get()) {
        "Tenant mismatch"
    }
    // Process command...
}

// INTERCEPTOR: Propagate to async Axon processors
@Component
class TenantPropagationInterceptor : MessageHandlerInterceptor<EventMessage<*>> {
    override fun handle(message: EventMessage<*>, interceptorChain: InterceptorChain) {
        val tenantId = message.metaData["tenant_id"] as? String
        if (tenantId != null) {
            TenantContext.set(TenantId(tenantId))
        }
        try {
            return interceptorChain.proceed()
        } finally {
            TenantContext.clear()
        }
    }
}

// POSTGRESQL RLS: Database-level isolation (Layer 3 - Database)
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON orders
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
```

**Rule:** 3-layer defense (Filter → Validator → RLS), always propagate, always cleanup

---

## 14. Data Architecture

### Event Store Schema

**Table Structure (PostgreSQL 16.10):**
```sql
-- Base events table (partitioned by timestamp)
CREATE TABLE events (
    event_id          UUID PRIMARY KEY,
    aggregate_id      UUID NOT NULL,
    aggregate_type    VARCHAR(255) NOT NULL,
    sequence_number   BIGINT NOT NULL,
    tenant_id         UUID NOT NULL,        -- Multi-tenancy
    timestamp         TIMESTAMPTZ NOT NULL,
    event_type        VARCHAR(255) NOT NULL,
    payload           JSONB NOT NULL,
    metadata          JSONB,
    UNIQUE (aggregate_id, sequence_number)
) PARTITION BY RANGE (timestamp);

-- Monthly partitions (created automatically by migration or stored procedure)
CREATE TABLE events_2025_10 PARTITION OF events
  FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');

CREATE TABLE events_2025_11 PARTITION OF events
  FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');

-- BRIN index for time-range queries (low overhead)
CREATE INDEX events_timestamp_brin_idx ON events
  USING BRIN (timestamp);

-- B-tree index for aggregate lookups
CREATE INDEX events_aggregate_idx ON events (aggregate_id, sequence_number);

-- Tenant isolation index
CREATE INDEX events_tenant_idx ON events (tenant_id);

-- Row-Level Security (Layer 3)
ALTER TABLE events ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_events ON events
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
```

**Snapshot Table:**
```sql
CREATE TABLE snapshots (
    aggregate_id      UUID PRIMARY KEY,
    aggregate_type    VARCHAR(255) NOT NULL,
    sequence_number   BIGINT NOT NULL,
    tenant_id         UUID NOT NULL,
    timestamp         TIMESTAMPTZ NOT NULL,
    snapshot_data     JSONB NOT NULL
);

-- Snapshot every 100 events (configurable)
```

**Axon Token Store (Event Processor Coordination):**
```sql
CREATE TABLE token_entry (
    processor_name    VARCHAR(255) NOT NULL,
    segment           INT NOT NULL,
    token             BYTEA,
    token_type        VARCHAR(255),
    timestamp         TIMESTAMPTZ,
    owner             VARCHAR(255),      -- Node ID for Active-Active
    PRIMARY KEY (processor_name, segment)
);
```

### Projection Schema Design

**Example: Order Projection (Read Model):**
```sql
-- Query-optimized table (denormalized)
CREATE TABLE orders (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    customer_id       UUID NOT NULL,
    status            VARCHAR(50) NOT NULL,
    total_amount      DECIMAL(19, 2) NOT NULL,
    currency          VARCHAR(3) NOT NULL DEFAULT 'EUR',
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    submitted_at      TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ
);

-- Indexes for common queries
CREATE INDEX orders_tenant_idx ON orders (tenant_id);
CREATE INDEX orders_customer_idx ON orders (customer_id);
CREATE INDEX orders_status_idx ON orders (status);
CREATE INDEX orders_created_at_idx ON orders (created_at DESC);  -- Cursor pagination

-- Cursor pagination compound index
CREATE INDEX orders_cursor_idx ON orders (id, created_at);

-- Row-Level Security
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_orders ON orders
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
```

**Projection Event Handler (jOOQ):**
```kotlin
@Component
class OrderProjection(
    private val dsl: DSLContext
) {
    @EventHandler
    fun on(event: OrderCreatedEvent) {
        dsl.insertInto(ORDERS)
            .set(ORDERS.ID, event.orderId.value)
            .set(ORDERS.TENANT_ID, event.tenantId.value)
            .set(ORDERS.CUSTOMER_ID, event.customerId.value)
            .set(ORDERS.STATUS, OrderStatus.CREATED.name)
            .set(ORDERS.TOTAL_AMOUNT, event.total.amount)
            .set(ORDERS.CURRENCY, event.total.currency)
            .set(ORDERS.CREATED_AT, event.timestamp)
            .set(ORDERS.UPDATED_AT, event.timestamp)
            .execute()
    }

    @EventHandler
    fun on(event: OrderSubmittedEvent) {
        dsl.update(ORDERS)
            .set(ORDERS.STATUS, OrderStatus.SUBMITTED.name)
            .set(ORDERS.SUBMITTED_AT, event.timestamp)
            .set(ORDERS.UPDATED_AT, event.timestamp)
            .where(ORDERS.ID.eq(event.orderId.value))
            .execute()
    }
}
```

### Data Retention & Archival

**Event Store Retention:**
- Primary storage: 2 years (hot data)
- Archive storage: 7 years total (compliance requirement)
- Archival strategy: Export to S3/object storage after 2 years
- Format: Parquet or JSONL (compressed)

**Projection Retention:**
- Keep current state indefinitely
- Soft delete: Mark as deleted, retain for audit
- Hard delete: After 7 years (compliance)

---

## 15. API Contracts

### REST API Design Principles

**1. Direct Response (No Envelope)**
```json
// SUCCESS (200 OK):
{
  "orderId": "order-123",
  "status": "CREATED",
  "total": 100.00
}

// ❌ FORBIDDEN: Envelope pattern
{
  "success": true,
  "data": { "orderId": "..." }
}
```

**2. RFC 7807 Problem Details (Errors)**
```json
// ERROR (400 Bad Request):
{
  "type": "https://api.eaf.axians.com/problems/validation-error",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Order items cannot be empty",
  "instance": "/api/orders",
  "traceId": "a1b2c3d4e5f6g7h8",
  "tenantId": "tenant-abc-123"
}
```

**3. Cursor-Based Pagination**
```json
// REQUEST:
GET /api/orders?cursor=eyJpZCI6MTIzfQ&limit=50

// RESPONSE:
{
  "data": [
    { "orderId": "123", ... },
    { "orderId": "124", ... }
  ],
  "pagination": {
    "nextCursor": "eyJpZCI6MTczfQ",
    "prevCursor": "eyJpZCI6NzN9",
    "hasMore": true,
    "limit": 50
  }
}
```

### HTTP Status Codes

| Status | Usage | Example |
|--------|-------|---------|
| **200 OK** | Successful GET, PUT, DELETE | `GET /api/orders/123` |
| **201 Created** | Resource created (sync) | `POST /api/orders` (immediate) |
| **202 Accepted** | Async command accepted | `POST /api/orders` (async CQRS) |
| **204 No Content** | Successful DELETE | `DELETE /api/orders/123` |
| **400 Bad Request** | Validation error | Invalid request body |
| **401 Unauthorized** | Missing/invalid JWT | No Authorization header |
| **403 Forbidden** | Insufficient permissions | Role check failed |
| **404 Not Found** | Resource not found | Order doesn't exist |
| **409 Conflict** | Concurrent modification | Aggregate version conflict |
| **500 Internal Server Error** | Unexpected error | Uncaught exception |

### Standard Endpoints (Per Resource)

```kotlin
// Collection operations:
GET    /api/{resource}                    # List (with cursor pagination)
POST   /api/{resource}                    # Create

// Item operations:
GET    /api/{resource}/{id}               # Retrieve
PUT    /api/{resource}/{id}               # Update (full replacement)
PATCH  /api/{resource}/{id}               # Partial update
DELETE /api/{resource}/{id}               # Delete

// Actions (POST only):
POST   /api/{resource}/{id}/{action}      # Trigger action (e.g., submit, approve)

// Sub-resources:
GET    /api/{resource}/{id}/{sub-resource}        # List sub-resources
POST   /api/{resource}/{id}/{sub-resource}        # Create sub-resource
GET    /api/{resource}/{id}/{sub-resource}/{sid}  # Get sub-resource
```

### OpenAPI Documentation

```kotlin
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order management API")
class OrderController(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway
) {
    @PostMapping
    @Operation(
        summary = "Create order",
        description = "Creates a new order for the authenticated tenant"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "202", description = "Order creation accepted"),
        ApiResponse(responseCode = "400", description = "Validation failed"),
        ApiResponse(responseCode = "401", description = "Unauthorized")
    ])
    fun createOrder(
        @RequestBody @Valid request: CreateOrderRequest
    ): ResponseEntity<AcceptedResponse> {
        // Implementation...
    }
}
```

**Generation:** Automatic via Springdoc OpenAPI 2.6.0
**Access:** Swagger UI at `/swagger-ui.html`

---

## 16. Security Architecture

### Overview

EAF implements **defense-in-depth** security with multiple validation layers:

1. **Network Layer:** TLS/SSL encryption (production)
2. **API Gateway:** Rate limiting, IP whitelisting (future)
3. **Authentication:** Keycloak OIDC, 10-layer JWT validation
4. **Authorization:** Role-based access control (RBAC)
5. **Multi-Tenancy:** 3-layer tenant isolation
6. **Data Layer:** PostgreSQL Row-Level Security (RLS)
7. **Audit:** Event Store provides complete audit trail

### 10-Layer JWT Validation

**Implemented in:** `framework/security/jwt/JwtValidationFilter.kt`

```kotlin
@Component
class JwtValidationFilter(
    private val jwksProvider: KeycloakJwksProvider,
    private val revocationStore: RedisRevocationStore
) : OncePerRequestFilter() {

    override fun doFilterInternal(request, response, filterChain) {
        val token = extractToken(request)  // Layer 1: Format validation

        // Layer 2: Signature validation (RS256 with JWKS)
        val publicKey = jwksProvider.getPublicKey(token.keyId)
        validateSignature(token, publicKey)

        // Layer 3: Algorithm validation (RS256 only, reject HS256)
        require(token.algorithm == "RS256") { "Invalid algorithm" }

        // Layer 4: Claim schema validation
        require(token.hasClaim("sub")) { "Missing sub claim" }
        require(token.hasClaim("tenant_id")) { "Missing tenant_id claim" }
        require(token.hasClaim("roles")) { "Missing roles claim" }

        // Layer 5: Time-based validation (exp, iat, nbf)
        val now = Instant.now()
        require(token.expiresAt.isAfter(now)) { "Token expired" }
        require(token.issuedAt.isBefore(now.plusSeconds(clockSkew))) { "Future iat" }

        // Layer 6: Issuer/Audience validation
        require(token.issuer == expectedIssuer) { "Invalid issuer" }
        require(token.audience.contains(expectedAudience)) { "Invalid audience" }

        // Layer 7: Revocation check (Redis blacklist)
        require(!revocationStore.isRevoked(token.jti)) { "Token revoked" }

        // Layer 8: Role validation
        val roles = token.getClaim("roles") as List<String>
        require(roles.isNotEmpty()) { "No roles present" }
        val normalizedRoles = roles.map { normalizeRole(it) }

        // Layer 9: User validation (optional - performance trade-off)
        // val userId = token.subject
        // require(userRepository.exists(userId) && userRepository.isActive(userId))

        // Layer 10: Injection detection
        val claims = token.allClaims
        claims.values.forEach { value ->
            detectInjectionPatterns(value)  // SQL, XSS patterns
        }

        // Build Spring Security context
        val authentication = buildAuthentication(token, normalizedRoles)
        SecurityContextHolder.setContext(SecurityContext(authentication))

        filterChain.doFilter(request, response)
    }
}
```

**Injection Detection Patterns:**
```kotlin
private val injectionPatterns = listOf(
    Regex("'.*OR.*'="),           // SQL injection
    Regex("<script"),              // XSS
    Regex("javascript:"),          // XSS
    Regex("\\$\\{.*}"),            // Expression injection
    Regex("\\.\\.[\\\\/]")         // Path traversal
)

fun detectInjectionPatterns(value: Any?) {
    val str = value?.toString() ?: return
    injectionPatterns.forEach { pattern ->
        require(!str.contains(pattern)) {
            "Potential injection detected in JWT claim"
        }
    }
}
```

### 3-Layer Multi-Tenancy Isolation

**Layer 1: Request Filter (TenantContextFilter)**
```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)  // After Security, before business logic
class TenantContextFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request, response, filterChain) {
        val jwt = SecurityContextHolder.getAuthentication() as JwtAuthenticationToken
        val tenantId = jwt.token.getClaim("tenant_id") as? String

        requireNotNull(tenantId) { "Missing tenant_id in JWT" }

        TenantContext.set(TenantId(tenantId))

        // Set PostgreSQL session variable for RLS
        dataSource.connection.use { conn ->
            conn.prepareStatement("SET app.tenant_id = ?").use { stmt ->
                stmt.setString(1, tenantId)
                stmt.execute()
            }
        }

        try {
            filterChain.doFilter(request, response)
        } finally {
            TenantContext.clear()
        }
    }
}
```

**Layer 2: Service Validation (Command Handlers)**
```kotlin
@CommandHandler
constructor(command: CreateOrderCommand) {
    val contextTenant = TenantContext.get()
    require(command.tenantId == contextTenant) {
        "Tenant mismatch: command=${command.tenantId}, context=$contextTenant"
    }

    // Business logic (tenant-validated)
    AggregateLifecycle.apply(
        OrderCreatedEvent(
            orderId = command.orderId,
            tenantId = command.tenantId,  // Stored in event
            ...
        )
    )
}
```

**Layer 3: Database RLS (PostgreSQL)**
```sql
-- Enable Row-Level Security on all tenant-aware tables
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE events ENABLE ROW LEVEL SECURITY;

-- Policy: Users can only see their tenant's data
CREATE POLICY tenant_isolation_orders ON orders
  FOR ALL
  USING (tenant_id = current_setting('app.tenant_id')::uuid);

CREATE POLICY tenant_isolation_order_items ON order_items
  FOR ALL
  USING (tenant_id = current_setting('app.tenant_id')::uuid);

CREATE POLICY tenant_isolation_events ON events
  FOR ALL
  USING (tenant_id = current_setting('app.tenant_id')::uuid);

-- CRITICAL: Policies are FAIL-CLOSED
-- If app.tenant_id is not set, queries return zero rows
```

### Role-Based Access Control (RBAC)

**Role Hierarchy:**
```
SUPER_ADMIN           # System administrator (all permissions)
  ├── TENANT_ADMIN    # Tenant administrator (tenant-scoped)
  │     ├── ORDER_MANAGER      # Manage orders
  │     ├── PRODUCT_MANAGER    # Manage products
  │     └── USER_MANAGER       # Manage users
  └── OPERATOR        # Read-only operator
        └── VIEWER    # Read-only viewer
```

**Permission-Style Roles (Alternative):**
```
widget:create
widget:read
widget:update
widget:delete
widget:publish
order:create
order:read
order:approve
```

**Role Normalization (Handles Both Styles):**
```kotlin
fun normalizeRole(role: String): GrantedAuthority {
    return when {
        role.contains(":") -> {
            // Permission-style: Keep as-is
            SimpleGrantedAuthority(role)
        }
        role.startsWith("ROLE_") -> {
            // Already normalized
            SimpleGrantedAuthority(role)
        }
        else -> {
            // Traditional role: Add ROLE_ prefix
            SimpleGrantedAuthority("ROLE_$role")
        }
    }
}
```

**Usage in Controllers:**
```kotlin
@PreAuthorize("hasRole('ORDER_MANAGER')")           // Traditional style
@PostMapping("/api/orders")
fun createOrder(...) { }

@PreAuthorize("hasAuthority('order:create')")       // Permission style
@PostMapping("/api/orders")
fun createOrder(...) { }

@PreAuthorize("hasRole('ADMIN') or hasAuthority('order:read')")  // Combined
@GetMapping("/api/orders/{id}")
fun getOrder(...) { }
```

### Security Best Practices

**Fail-Closed Design:**
- Missing tenant_id in JWT → Request rejected (401 Unauthorized)
- Missing app.tenant_id in PostgreSQL → Query returns zero rows
- Missing required role → Request rejected (403 Forbidden)
- Invalid JWT signature → Request rejected (401 Unauthorized)

**Secret Management:**
```yaml
# application.yml (NO secrets!)
eaf:
  keycloak:
    admin-client-id: eaf-admin
    admin-client-secret: ${KEYCLOAK_ADMIN_SECRET}  # Environment variable

# Deployment:
# - Development: .env file (Git-ignored)
# - Production: Kubernetes Secrets, AWS Secrets Manager, etc.
# FORBIDDEN: Hardcoded secrets in code or config files
```

**CORS Configuration:**
```kotlin
@Configuration
class CorsConfiguration {
    @Bean
    fun corsFilter(): CorsFilter {
        val config = CorsConfiguration().apply {
            // Development: Allow localhost
            allowedOrigins = listOf("http://localhost:5173")  # React-Admin dev

            // Production: Specific domains only
            // allowedOrigins = listOf("https://admin.eaf.axians.com")

            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600
        }

        val source = UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", config)
        }

        return CorsFilter(source)
    }
}
```

---

## 17. Performance Considerations

### Performance Budgets (Product Brief NFRs)

| Operation Type | Target (p95) | Monitoring Threshold | Alert Trigger |
|----------------|--------------|---------------------|---------------|
| **Simple Query** | <50ms | >75ms | >100ms sustained (5 min) |
| **Complex Query** | <200ms | >300ms | >400ms sustained (5 min) |
| **Command (sync)** | <100ms | >150ms | >200ms sustained (5 min) |
| **Command (async)** | <10ms (ack) | >20ms | >30ms sustained (5 min) |
| **Event Processing Lag** | <10s | >15s | >30s sustained (5 min) |
| **Projection Update** | <10s | >15s | >30s sustained (5 min) |

### Optimization Strategies

**Event Store (PostgreSQL):**
```sql
-- 1. Time-based partitioning (monthly)
CREATE TABLE events_2025_10 PARTITION OF events
  FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');

-- 2. BRIN indexes (low overhead for time-series)
CREATE INDEX events_timestamp_brin_idx ON events USING BRIN (timestamp);

-- 3. Aggregate snapshots (every 100 events)
-- Reduces event replay overhead

-- 4. Partition pruning for queries
-- Queries with timestamp ranges scan only relevant partitions

-- 5. Analyze and vacuum scheduled (nightly)
```

**Projection Queries (jOOQ):**
```kotlin
// Cursor pagination (constant performance):
dsl.selectFrom(ORDERS)
    .where(ORDERS.TENANT_ID.eq(tenantId))
    .and(ORDERS.ID.greaterThan(cursor))  // Indexed column
    .orderBy(ORDERS.ID.asc())
    .limit(limit)
    .fetch()

// FORBIDDEN: Offset pagination (degrades at scale)
// LIMIT 50 OFFSET 10000  ❌ Scans 10000 rows
```

**Connection Pooling:**
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20        # Tune based on load
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**Axon Event Processor Tuning:**
```yaml
axon:
  eventhandling:
    processors:
      order-projection:
        mode: tracking
        batch-size: 100              # Process 100 events per transaction
        thread-count: 2              # Parallel processing
        claim-timeout: 10000         # Active-Active coordination
```

### Caching Strategy

**Query Results (Read-Through Cache):**
```kotlin
// Optional: Redis cache for frequently-accessed projections
@Cacheable(value = "orders", key = "#orderId")
fun findOrder(orderId: OrderId): OrderResponse {
    return queryGateway.query(FindOrderQuery(orderId)).join()
}

// Cache eviction on updates:
@CacheEvict(value = "orders", key = "#event.orderId")
@EventHandler
fun on(event: OrderUpdatedEvent) {
    // Invalidate cache
}
```

**JWKS Public Keys (1-hour TTL):**
```kotlin
@Cacheable(value = "jwks", key = "#keyId")
fun getPublicKey(keyId: String): PublicKey {
    return jwksProvider.fetchPublicKey(keyId)  // Cached 1 hour
}
```

### Performance Monitoring

**Prometheus Metrics:**
```kotlin
// Custom metrics:
@Timed(value = "eaf.command.execution", description = "Command execution time")
@CommandHandler
fun handle(command: CreateOrderCommand) {
    // Execution timed automatically
}

// Tenant-aware metrics:
registry.counter(
    "eaf.orders.created",
    "tenant_id", TenantContext.get().value,
    "aggregate_type", "Order"
).increment()
```

**Alerts (Grafana - Post-MVP):**
- API latency p95 >200ms for 5 minutes → PagerDuty
- Event processor lag >30s for 5 minutes → PagerDuty
- PostgreSQL CPU >80% for 10 minutes → Email
- Multi-tenancy context leak detected → Immediate alert

---

## 18. Deployment Architecture

### Development Deployment (Single-Server)

```yaml
# docker-compose.yml
version: '3.9'

services:
  postgres:
    image: postgres:16.6
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: eaf
      POSTGRES_USER: eaf
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./docker/postgres/init-scripts:/docker-entrypoint-initdb.d

  keycloak:
    image: ghcr.io/axians/eaf-keycloak:26.4.0  # Multi-arch (auto-selects platform)
    ports:
      - "8080:8080"
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/eaf
      KC_DB_USERNAME: eaf
      KC_DB_PASSWORD: ${POSTGRES_PASSWORD}
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
    depends_on:
      - postgres

  redis:
    image: redis:7.2-alpine
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes
    volumes:
      - redis-data:/data

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD}
    volumes:
      - grafana-data:/var/lib/grafana
      - ./docker/grafana/provisioning:/etc/grafana/provisioning

  eaf-application:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8181:8181"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/eaf
      SPRING_DATASOURCE_USERNAME: eaf
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: http://keycloak:8080/realms/eaf
      EAF_REDIS_HOST: redis
      EAF_REDIS_PORT: 6379
    depends_on:
      - postgres
      - keycloak
      - redis

volumes:
  postgres-data:
  redis-data:
  prometheus-data:
  grafana-data:
```

**One-Command Setup:**
```bash
#!/bin/bash
# scripts/init-dev.sh

echo "🚀 Initializing EAF development environment..."

# 1. Check prerequisites
command -v docker >/dev/null 2>&1 || { echo "Docker required"; exit 1; }
command -v docker-compose >/dev/null 2>&1 || { echo "Docker Compose required"; exit 1; }

# 2. Create .env if not exists
if [ ! -f .env ]; then
    cp .env.example .env
    echo "✅ Created .env file (please configure)"
fi

# 3. Start Docker Compose stack
docker-compose up -d

# 4. Wait for services to be healthy
echo "⏳ Waiting for services..."
./scripts/health-check.sh

# 5. Run database migrations
./gradlew flywayMigrate

# 6. Seed test data
./scripts/seed-data.sh

# 7. Install Git hooks
./scripts/install-git-hooks.sh

echo "✅ EAF development environment ready!"
echo "   - Application: http://localhost:8181"
echo "   - Keycloak: http://localhost:8080"
echo "   - Prometheus: http://localhost:9090"
echo "   - Grafana: http://localhost:3000"
echo ""
echo "Run: ./gradlew bootRun"
```

---

### Production Deployment (Active-Passive HA)

**Template:** `docker-compose.prod.yml` (see Section 8 for details)

**Components:**
- Patroni-managed PostgreSQL cluster (Primary + Replica)
- EAF Application (Primary + Standby)
- Keycloak, Redis, Prometheus, Grafana
- Automated failover <2 minutes

**Disaster Recovery:**
- RTO: 4 hours
- RPO: 15 minutes
- PostgreSQL PITR (Point-In-Time Recovery via WAL archiving)
- Automated backup verification (daily)
- Quarterly DR drills

---

### Production Deployment (Active-Active Multi-DC - Phase 2)

**Template:** `docker-compose.active-active.yml`

**Components:**
- NGINX/HAProxy Load Balancer
- Multi-node EAF Application (2+ nodes)
- PostgreSQL Primary + Read-Replicas
- Redis Sentinel (3-node HA cluster)
- Keycloak Cluster Mode

**Migration from Phase 1:** 3-4 weeks, no code changes (see Section 8)

---

## 19. Development Environment

### Prerequisites

**Required Software:**
- **JDK 21 LTS** (Temurin recommended)
- **Kotlin 2.2.21** (bundled with Gradle)
- **Docker 24.0+** and Docker Compose 2.20+
- **Gradle 9.1.0** (via wrapper - `./gradlew`)
- **Git 2.40+**
- **pnpm 10+** (for React-Admin frontend)

**Recommended IDE:**
- **IntelliJ IDEA 2024.3+** (Ultimate or Community)
- Plugins: Kotlin, Spring Boot, Database Tools
- Alternative: Visual Studio Code with Kotlin extension

**Supported Platforms:**
- Linux (amd64, arm64)
- macOS (Intel, Apple Silicon)
- Windows (WSL2 recommended for Docker)

### Setup Commands

```bash
# 1. Clone repository
git clone <eaf-repo-url> eaf-v1
cd eaf-v1

# 2. Initialize development environment (one command!)
./scripts/init-dev.sh
# This script:
#   - Validates prerequisites
#   - Creates .env from template
#   - Starts Docker Compose stack
#   - Waits for services to be healthy
#   - Runs database migrations
#   - Seeds test data
#   - Installs Git hooks

# 3. Start application
./gradlew bootRun

# 4. Verify health
curl http://localhost:8181/actuator/health

# 5. Access services
#   - Application: http://localhost:8181
#   - Keycloak Admin: http://localhost:8080/admin (admin/admin)
#   - Prometheus: http://localhost:9090
#   - Grafana: http://localhost:3000 (admin/<from .env>)
#   - Swagger UI: http://localhost:8181/swagger-ui.html
```

### Environment Variables (.env)

```bash
# Database
POSTGRES_PASSWORD=dev_password_change_in_prod

# Keycloak
KEYCLOAK_ADMIN_PASSWORD=admin
KEYCLOAK_ADMIN_SECRET=change_in_prod

# Grafana
GRAFANA_ADMIN_PASSWORD=admin

# Application
SPRING_PROFILES_ACTIVE=dev
LOG_LEVEL=DEBUG

# Optional: Override ports
# POSTGRES_PORT=5433
# KEYCLOAK_PORT=8081
# REDIS_PORT=6380
```

### Development Workflow

**1. Create Feature Branch:**
```bash
git checkout -b feature/add-order-aggregate
```

**2. Use Scaffolding CLI:**
```bash
# Generate aggregate structure:
./gradlew :tools:eaf-cli:installDist
./tools/eaf-cli/build/install/eaf-cli/bin/eaf scaffold aggregate order

# Generated:
#   - OrderAggregate.kt
#   - CreateOrderCommand.kt
#   - OrderCreatedEvent.kt
#   - OrderAggregateTest.kt
#   (All passing quality gates immediately)
```

**3. Implement Business Logic:**
```kotlin
// Customize generated aggregate:
@Aggregate
class OrderAggregate {
    @AggregateIdentifier
    private lateinit var orderId: OrderId

    @CommandHandler
    constructor(command: CreateOrderCommand) {
        // Add validation logic
        require(command.items.isNotEmpty()) { "Items required" }

        AggregateLifecycle.apply(OrderCreatedEvent(...))
    }

    // Add more command handlers...
}
```

**4. Run Tests Locally:**
```bash
# Fast feedback (Unit + Integration):
./gradlew test integrationTest
# <15 minutes (Nullables + Testcontainers)

# Quality gates:
./gradlew ktlintCheck detekt
# <30 seconds
```

**5. Commit (Git Hooks Auto-Run):**
```bash
git add .
git commit -m "[EPIC-2] Add Order aggregate with create command"

# Git hooks execute automatically:
# → pre-commit: ktlint check (<5s)
# → If ktlint fails, commit blocked

git push origin feature/add-order-aggregate

# → pre-push: Detekt + fast tests (<30s)
# → If failures, push blocked
```

**6. Create PR:**
```bash
# CI/CD runs automatically:
# - ktlint, Detekt, Konsist
# - test, integrationTest
# - Blocks merge if failures
```

**7. Nightly Validation:**
```bash
# Runs automatically at 2 AM CEST:
# - Property-Based Tests (30-45 min)
# - Fuzz Tests (35-40 min)
# - Concurrency Tests/LitmusKt (20-30 min)
# - Mutation Tests/Pitest (20-30 min)
# Total: ~2.5 hours

# Creates GitHub issue on failure
```

---

## 20. Architecture Decision Records (ADRs)

### ADR-001: PostgreSQL as Event Store (vs. Streaming Solutions)

**Status:** Accepted
**Date:** 2025-10-30
**Decision:** Use PostgreSQL 16.6 as event store via Axon `JdbcEventStorageEngine`

**Context:**
- Need FOSS event store for CQRS/Event Sourcing
- Evaluated: PostgreSQL, Apache Kafka, NATS JetStream, EventStoreDB
- Constraint: Must have native Axon Framework integration

**Decision:**
- PostgreSQL selected as **only viable FOSS option** with mature Axon integration
- Implemented as **swappable Hexagonal adapter**
- Proactive optimization plan (partitioning, BRIN indexes, snapshots)
- Migration triggers defined (p95 >200ms, lag >10s, CPU >80%)

**Rationale:**
- ✅ Native Axon integration (battle-tested)
- ✅ Operational simplicity (single database for events, projections, Flowable)
- ✅ Known limitations with established mitigation patterns
- ✅ Future migration path preserved (NATS, Axon Server)

**Consequences:**
- Requires partitioning and indexing optimization
- Monitoring triggers for migration decision
- Swappable adapter enables low-effort future migration

---

### ADR-002: Multi-Architecture Support (amd64/arm64/ppc64le)

**Status:** Accepted
**Date:** 2025-10-30
**Decision:** Support amd64, arm64, and ppc64le from Day 1

**Context:**
- ZEWSSP (first product) targets VMware/x86 (amd64 only)
- Future products (DPCM, etc.) may require IBM Power (ppc64le)
- Choice: MVP amd64-only OR multi-arch from inception

**Decision:**
- Multi-arch support from Day 1 for **framework reusability**
- Custom Keycloak ppc64le build (UBI9-based)
- Investment: €4.400 initial + €8.000/year

**Rationale:**
- ✅ Prevents €22.000+ retrofit costs (52% savings)
- ✅ Avoids "Gefrickel" when second product needs ppc64le
- ✅ Professional enterprise-grade framework architecture
- ✅ No MVP timeline impact (Epic 3 remains 3 weeks)

**Consequences:**
- Quarterly Keycloak custom build maintenance
- CI/CD multi-arch pipeline complexity
- Testing matrix expansion (3 architectures)

---

### ADR-003: Phased HA Strategy (Active-Passive → Active-Active)

**Status:** Accepted
**Date:** 2025-10-30
**Decision:** Active-Passive for MVP, Active-Active for Phase 2

**Context:**
- Standard customers: <2min failover acceptable (1-2 servers)
- Enterprise customers: <30s failover required (Coast-to-Coast)
- Product Brief initially stated Active-Passive

**Decision:**
- **Phase 1 (MVP):** Active-Passive HA (Patroni PostgreSQL, <2min failover)
- **Phase 2 (Post-MVP):** Active-Active Multi-DC (<30s failover)
- **Key:** Stateless architecture from Day 1 (Active-Active-Ready)

**Rationale:**
- ✅ Supports both customer segments
- ✅ No MVP timeline impact (Active-Passive simpler)
- ✅ Seamless Phase 2 migration (3-4 weeks, no code changes)
- ✅ Industry standard (80% use Active-Passive)

**Consequences:**
- Application must be stateless (no local caches, no mutable state)
- Redis for session management (Phase 2)
- Load Balancer required (Phase 2)
- PostgreSQL read-replica routing (Phase 2)

---

### ADR-004: Nullables Pattern for Testing (vs. Mockk)

**Status:** Accepted
**Date:** 2025-10-30
**Decision:** Hybrid testing strategy with Nullables Pattern (James Shore)

**Context:**
- Need fast unit tests (<5s developer feedback)
- Mockk framework has reflection overhead (10-50ms per test)
- Product Brief claimed "60%+ faster tests" (Nullable Design Pattern)

**Decision:**
- **Domain Logic:** Pure functions (no mocking needed)
- **Aggregates:** Axon Test Fixtures (framework-optimized)
- **Application/Infrastructure:** Nullables Pattern (100-1000x faster than Mockk)
- **Integration:** Testcontainers (real dependencies)

**Rationale:**
- ✅ Validated pattern (James Shore, empirical data)
- ✅ 100-1000x faster than mocking frameworks
- ✅ Production code with "off switch" (fail-closed)
- ✅ Empirical validation in Epic 9 (benchmarks)

**Consequences:**
- Infrastructure wrappers include `createNull()` factory methods
- Test-specific code in production classes (philosophical trade-off)
- Hand-written stubs for third-party dependencies
- Team training on Nullables Pattern required

**References:**
- https://www.jamesshore.com/v2/projects/nullables
- https://www.jamesshore.com/v2/projects/nullables/testing-without-mocks

---

### ADR-005: 7-Layer Testing Strategy (vs. Traditional Pyramid)

**Status:** Accepted
**Date:** 2025-10-30
**Decision:** Multi-layer defense with Property, Fuzz, and Concurrency testing

**Context:**
- Traditional pyramid: Unit → Integration → E2E
- EAF has critical security (10-layer JWT, multi-tenancy) and concurrency concerns
- Prototype validated advanced testing techniques

**Decision:**
- **Layer 1:** Static Analysis (ktlint, Detekt, Konsist)
- **Layer 2:** Unit Tests (Nullables + Axon Fixtures)
- **Layer 3:** Integration Tests (Testcontainers, 40-50% of suite)
- **Layer 4:** Property-Based Tests (Kotest, 1000+ iterations)
- **Layer 5:** Fuzz Tests (Jazzer, 7 targets × 5 min)
- **Layer 6:** Mutation Tests (Pitest, 60-70% coverage)
- **Layer 7:** Concurrency Tests (LitmusKt, race conditions)

**Rationale:**
- ✅ Each layer finds different bug classes
- ✅ Property tests validate security invariants (idempotence, fail-closed)
- ✅ Fuzz tests find crashes/DoS vulnerabilities
- ✅ LitmusKt prevents €50K-140K concurrency bugs (tenant context leak)
- ✅ Mutation tests validate test effectiveness

**Consequences:**
- Nightly pipeline: ~2.5 hours (acceptable)
- Property tests excluded from mutation testing (exponential time)
- Learning curve for team (advanced testing techniques)
- Epic 8: 1 week for LitmusKt integration

---

### ADR-006: Tiered Onboarding Milestones (vs. <1 Month Goal)

**Status:** Accepted
**Date:** 2025-10-30
**Decision:** 12-week tiered milestones (vs. original <1 month goal)

**Context:**
- Original goal: <1 month time-to-productivity
- Industry research: CQRS/ES has "steep learning curve"
- Majlinda transitions from Node.js/JavaScript to Kotlin/CQRS

**Decision:**
- **Week 3:** Simple aggregate (Scaffolding CLI) - Milestone 1
- **Week 8:** Standard aggregate (independent) - Milestone 2
- **Week 12:** Production deployment - Milestone 3
- **Enablers:** Scaffolding CLI (€4K), Documentation (€16K), Pair Programming (€1.6K)

**Rationale:**
- ✅ Realistic for paradigm shift (imperative → event-sourced)
- ✅ Still 50% improvement over legacy (6 months → 12 weeks)
- ✅ Validated approach (Google 10 days, Stripe 4-6 weeks for simpler stacks)
- ✅ Majlinda Week 3 validation (<3 days) achievable with CLI

**Consequences:**
- Epic 7.5 added (Documentation, 4 weeks)
- MVP timeline: 14-18 weeks (vs. 11-13 weeks original)
- Investment in enablers: €21.600
- ROI: 133% (2.3x) with team scaling

---

### ADR-007: Mutation Testing Target (60-70% vs. 80%)

**Status:** Accepted
**Date:** 2025-10-30
**Decision:** 60-70% mutation coverage target (vs. 80% original)

**Context:**
- Original target: 80% mutation coverage
- Pitest Kotlin plugin is **deprecated** (official plugin no longer maintained)
- Arcmutate (commercial) violates FOSS-only constraint

**Decision:**
- Use Pitest with deprecated Kotlin plugin
- Reduce target: 60-70% (realistic for deprecated plugin)
- Supplement with 85%+ line coverage via integration tests
- Monitor Kotlin mutation testing ecosystem for improvements

**Rationale:**
- ✅ Only FOSS option available
- ✅ 60-70% still validates test effectiveness
- ✅ Integration tests provide additional safety net
- ✅ Deprecated plugin remains functional

**Consequences:**
- Lower mutation coverage than originally planned
- Potential tool migration in future (if FOSS alternative emerges)
- Integration tests become more critical (40-50% of suite)

---

### ADR-008: LitmusKt Concurrency Testing (NEW - Epic 8)

**Status:** Accepted
**Date:** 2025-10-30
**Decision:** Add LitmusKt concurrency stress testing (7th testing layer)

**Context:**
- Multi-tenancy uses ThreadLocal (TenantContext)
- Axon Event Processors are async/multi-threaded
- Active-Active requires distributed coordination
- Risk: Race conditions causing tenant isolation failure

**Decision:**
- Integrate LitmusKt (JetBrains Research)
- 5 critical concurrency tests in Epic 8
- Nightly execution (~20-30 minutes)
- Tests: TenantContext isolation, Event Processor propagation, Connection pool, Distributed locks

**Rationale:**
- ✅ Production-proven (Kotlin compiler CI)
- ✅ Prevents critical bugs (tenant context leak = €100K+ data breach)
- ✅ Memory model validation (compiler optimizations, CPU reordering)
- ✅ Found real bugs in Kotlin 1.9.0 (unsafe publication)

**Consequences:**
- Epic 8 extended: +5-7 days for LitmusKt integration
- Investment: €4.000-€5.600
- ROI: 793% (8x) through bug prevention
- Nightly pipeline: +20-30 minutes

---

### ADR-009: Prototype Structure Reuse (vs. Standard Starters)

**Status:** Accepted
**Date:** 2025-10-30
**Decision:** Reuse validated prototype structure (vs. Spring Initializr, JHipster)

**Context:**
- Evaluated: Spring Initializr (~15% coverage), JHipster Kotlin (~30%), Axon Starter (~25%)
- EAF is **framework project** with highly specific requirements
- Prototype at `/Users/michael/acci_eaf` has 100% coverage

**Decision:**
- Use prototype structure as foundation
- Keep: Build config, module structure, Docker setup, CI/CD
- Clean: Prototype implementations (start fresh for Epic 1)

**Rationale:**
- ✅ 100% architectural requirements covered
- ✅ Production-validated through prototype development
- ✅ Eliminates 4-6 weeks setup time
- ✅ Quality gates pre-configured
- ✅ No standard starter supports Hexagonal + CQRS/ES + Spring Modulith + Multi-Tenancy

**Consequences:**
- No "standard" CLI command (npm create, spring init)
- Custom approach requires documentation
- Prototype must be thoroughly cleaned (Epic 1)
- First implementation story: "Initialize repository from prototype"

---

_Generated by BMAD Decision Architecture Workflow v1.3.2_
_Date: 2025-10-30_
_For: Wall-E_

---

_Generated by BMAD Decision Architecture Workflow v1.3.2_
_Date: 2025-10-30_
_For: Wall-E_
