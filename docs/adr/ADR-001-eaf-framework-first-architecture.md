# ADR-001: EAF Framework-First Architecture

**Status:** Accepted
**Date:** 2025-11-24
**Author:** DVMM Team
**Deciders:** Winston (Architect), Amelia (Dev), Murat (TEA), John (PM), Bob (SM), Sally (UX)

---

## Context

DVMM is not just a product—it's the pilot project for EAF (Enterprise Application Framework). The framework must emerge from DVMM but remain completely independent of it, enabling future products to use EAF without any DVMM dependencies.

## Decision

We adopt a **Framework-First Architecture** using Hexagonal Architecture principles within a Gradle multi-module monorepo structure.

## Architectural Principles

1. **Strict Dependency Direction:** EAF ← DVMM (never the reverse)
2. **Domain Independence:** Framework core has zero external dependencies
3. **Hexagonal Boundaries:** Ports & Adapters pattern for all integrations
4. **Module Isolation:** Kotlin `internal` modifier + Konsist enforcement
5. **First Consumer Pattern:** DVMM validates EAF, but doesn't define it

## Monorepo Structure

```
eaf-monorepo/
├── settings.gradle.kts              # Defines all modules
├── build.gradle.kts                 # Root build with conventions
├── gradle/
│   └── libs.versions.toml           # Version Catalog (centralized)
│
├── build-logic/                     # Shared Build Conventions
│   ├── settings.gradle.kts
│   └── conventions/
│       └── src/main/kotlin/
│           ├── eaf.kotlin-conventions.gradle.kts
│           ├── eaf.spring-conventions.gradle.kts
│           └── eaf.test-conventions.gradle.kts
│
├── eaf/                             # FRAMEWORK
│   ├── eaf-core/                    # Domain Primitives (zero deps)
│   │   └── src/main/kotlin/
│   │       └── com/eaf/core/
│   │           ├── domain/          # Entity, AggregateRoot, ValueObject
│   │           ├── events/          # DomainEvent, EventMetadata
│   │           └── validation/      # Validation abstractions
│   │
│   ├── eaf-cqrs-core/               # CQRS Core (Commands & Queries)
│   ├── eaf-eventsourcing/           # Event Sourcing Infrastructure
│   ├── eaf-tenant/                  # Multi-Tenancy Infrastructure
│   ├── eaf-auth/                    # Authentication & Authorization (IdP-AGNOSTIC)
│   ├── eaf-audit/                   # Audit Trail Infrastructure
│   ├── eaf-observability/           # Logging, Metrics, Tracing
│   ├── eaf-notifications/           # Notification Infrastructure
│   ├── eaf-testing/                 # Test Support
│   ├── eaf-starters/                # Modular Auto-Configuration
│   └── eaf-auth-providers/          # IdP Implementations
│
├── dvmm/                            # PRODUCT (First Consumer)
│   ├── dvmm-domain/                 # DVMM Business Logic
│   ├── dvmm-application/            # Use Cases / Application Services
│   ├── dvmm-api/                    # REST API (Input Adapters)
│   ├── dvmm-infrastructure/         # Output Adapters
│   └── dvmm-app/                    # Spring Boot Application
│
├── frontend/                        # UI Components
│   ├── packages/
│   │   ├── eaf-ui/                  # Framework UI Kit
│   │   └── dvmm-ui/                 # Product-specific UI
│   └── apps/
│       └── dvmm-web/                # DVMM Web Application
│
└── docs/                            # Documentation
```

## EAF Module Definitions

| Module | Responsibility | Dependencies | Can Use Standalone? |
|--------|---------------|--------------|---------------------|
| `eaf-core` | Domain primitives, events, validation | None (pure Kotlin) | Yes |
| `eaf-cqrs-core` | CommandBus, QueryBus, Gateways | eaf-core | Yes |
| `eaf-eventsourcing` | EventStore, Projection, Snapshot | eaf-core | Yes |
| `eaf-tenant` | Multi-tenancy, RLS support | eaf-core | Yes |
| `eaf-auth` | IdP-agnostic auth interfaces, RBAC, JWT | eaf-core, eaf-tenant | Yes |
| `eaf-auth-keycloak` | Keycloak `IdentityProvider` implementation | eaf-auth | Yes |
| `eaf-audit` | Audit interfaces + crypto-shredding | eaf-core | Yes |
| `eaf-observability` | Logging, metrics, tracing | eaf-core | Yes |
| `eaf-notifications` | Email/Push/SMS abstractions | eaf-core, eaf-tenant | Yes |
| `eaf-testing` | Test utilities | eaf-core, eaf-eventsourcing | Yes |

## Modular Starters (À La Carte)

| Starter | Includes | Use Case |
|---------|----------|----------|
| `eaf-starter-core` | eaf-core, eaf-observability | Minimal EAF footprint |
| `eaf-starter-cqrs` | + eaf-cqrs-core | Apps with Command/Query separation |
| `eaf-starter-eventsourcing` | + eaf-eventsourcing | Apps with Event Sourcing |
| `eaf-starter-tenant` | + eaf-tenant | Multi-tenant applications |
| `eaf-starter-auth` | + eaf-auth | Auth without specific IdP |
| `eaf-starter-notifications` | + eaf-notifications | Email/Push/SMS notifications |

**Example Usage (DVMM):**

```kotlin
// dvmm-app/build.gradle.kts
dependencies {
    implementation("com.eaf:eaf-starter-core")
    implementation("com.eaf:eaf-starter-cqrs")
    implementation("com.eaf:eaf-starter-eventsourcing")
    implementation("com.eaf:eaf-starter-tenant")
    implementation("com.eaf:eaf-starter-auth")
    implementation("com.eaf:eaf-starter-notifications")
    implementation("com.eaf:eaf-auth-keycloak")  // Concrete IdP implementation
}
```

## Critical Rule: Aggregates ALWAYS in Product Domain

Concrete aggregates (e.g., `VmRequestAggregate`, `ProjectAggregate`) MUST reside in `dvmm-domain`, never in `eaf-eventsourcing`. The EAF module provides only:
- `AggregateRoot<ID>` base class
- `DomainEvent` interface
- `EventStore` interface

Products implement their specific aggregates using these abstractions.

## Dependency Rules (Konsist Enforced)

Architecture rules are enforced using [Konsist](https://docs.konsist.lemonappdev.com/):

```kotlin
class ArchitectureRulesTest {

    @Test
    fun `EAF modules must not depend on DVMM`() {
        Konsist
            .scopeFromPackage("com.eaf..")
            .files
            .assertFalse {
                it.hasImport { import -> import.name.startsWith("com.dvmm") }
            }
    }

    @Test
    fun `DVMM domain must not depend on infrastructure or Spring`() {
        Konsist
            .scopeFromModule("dvmm-domain")
            .classes()
            .assertFalse {
                it.hasImport { import ->
                    import.name.startsWith("com.dvmm.infrastructure") ||
                    import.name.startsWith("com.dvmm.api") ||
                    import.name.startsWith("org.springframework")
                }
            }
    }

    @Test
    fun `EAF core must have zero external dependencies`() {
        Konsist
            .scopeFromModule("eaf-core")
            .files
            .assertTrue {
                it.imports.all { import ->
                    import.name.startsWith("com.eaf.core") ||
                    import.name.startsWith("kotlin") ||
                    import.name.startsWith("java")
                }
            }
    }
}
```

## Test Strategy by Module

| Module | Test Type | Dependencies | CI Gate |
|--------|-----------|--------------|---------|
| `eaf-core` | Unit only | Pure Kotlin | ≥90% coverage |
| `eaf-cqrs-core` | Unit only | Pure Kotlin + eaf-core | ≥85% coverage |
| `eaf-eventsourcing` | Unit + Integration | Testcontainers (PostgreSQL) | ≥70% coverage |
| `eaf-tenant` | Integration | Testcontainers (PostgreSQL) | RLS tests pass |
| `eaf-auth` | Unit + Contract | WireMock (Keycloak) | Contract tests pass |
| `dvmm-domain` | Unit only | eaf-testing | ≥85% coverage |
| `dvmm-application` | Unit + Contract | Mocks for ports | ≥70% coverage |
| `dvmm-infrastructure` | Integration + Contract | Pact, Testcontainers | Contract tests pass |
| `dvmm-app` | E2E | Full stack | All scenarios pass |

## Story Categorization

| Prefix | Type | Target | Example |
|--------|------|--------|---------|
| `EAF-CORE-xxx` | Framework Core | eaf-core | "Add Result type for error handling" |
| `EAF-CQRS-xxx` | CQRS Feature | eaf-cqrs | "Implement snapshot after N events" |
| `EAF-TENANT-xxx` | Multi-Tenancy | eaf-tenant | "Add RLS policy generator" |
| `EAF-AUTH-xxx` | Auth Feature | eaf-auth | "Configurable JWT claim mapping" |
| `DVMM-xxx` | Product Feature | dvmm-* | "VM Request Form with size selection" |
| `INT-xxx` | Integration | Cross-module | "DVMM uses EAF-CQRS for Request Aggregate" |

**Acceptance Criteria for EAF Stories:**

```gherkin
Given a new product "TestProduct" without DVMM dependency
When TestProduct includes EAF module {module}
Then TestProduct compiles without errors
And all EAF tests for {module} pass
And Konsist architecture rules pass
```

## Consequences

### Positive

- Future products can use EAF without DVMM code
- Clear boundaries enable parallel team development
- Framework quality is independently testable
- Consistent patterns across all EAF-based products

### Negative

- More initial setup complexity
- Need discipline to maintain boundaries
- Some code duplication between EAF and DVMM initially

### Mitigation

- Konsist tests in CI prevent boundary violations
- Regular architecture reviews
- Clear documentation of what belongs where

## References

- [Hexagonal Architecture with Spring Boot](https://www.happycoders.eu/software-craftsmanship/hexagonal-architecture-spring-boot/)
- [Gradle Multi-Project Builds](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [Konsist Documentation](https://docs.konsist.lemonappdev.com/)
- [Kotlin Multi-Module Best Practices](https://github.com/mrclrchtr/gradle-kotlin-spring)
