# Dynamic Virtual Machine Manager (DVMM)

**Enterprise-grade VM provisioning platform built on the EAF framework for ISO 27001-compliant organizations.**

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.2.21-blue.svg?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.8-brightgreen.svg?logo=spring" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Gradle-9.2.1-02303A.svg?logo=gradle" alt="Gradle">
  <img src="https://img.shields.io/badge/PostgreSQL-16-336791.svg?logo=postgresql" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/JUnit-6.0.1-25A162.svg" alt="JUnit 6">
  <img src="https://img.shields.io/badge/Coverage-80%25-success.svg" alt="Coverage">
</p>

---

## Overview

DVMM is a self-service VM provisioning platform designed for medium-sized enterprises (50-500 employees) in regulated industries. It streamlines the VM request-approval-provisioning workflow while maintaining full audit trails for compliance.

**Key Value Propositions:**
- **30-Second Audit Trail:** Complete request history accessible instantly for ISO 27001 audits
- **Self-Service Workflow:** End users request VMs, administrators approve, system provisions automatically
- **Multi-Tenant Architecture:** Complete data isolation via PostgreSQL Row-Level Security
- **VMware Integration:** Direct vCenter API integration for automated provisioning

## Core Features

- **Event Sourcing (CQRS):** Full audit trail with immutable event history
- **Multi-Tenancy:** 3-layer isolation (Request Filter, Service Validation, PostgreSQL RLS)
- **Hexagonal Architecture:** Clean separation of domain logic from infrastructure
- **Approval Workflow:** Configurable approval chains with email notifications
- **VMware Provisioning:** Automated VM creation via vSphere API
- **Compliance Ready:** ISO 27001, GDPR crypto-shredding support

### Module Map

| Module | Path | Purpose |
|--------|------|---------|
| **EAF Core** | `eaf/eaf-core` | Domain primitives (Entity, AggregateRoot, ValueObject, DomainEvent) |
| **EAF Event Sourcing** | `eaf/eaf-eventsourcing` | Event Store interfaces, projection base classes |
| **EAF Tenant** | `eaf/eaf-tenant` | Multi-tenancy with PostgreSQL RLS |
| **EAF Auth** | `eaf/eaf-auth` | IdP-agnostic authentication interfaces |
| **EAF Testing** | `eaf/eaf-testing` | Test utilities (InMemoryEventStore, TestClock) |
| **DVMM Domain** | `dvmm/dvmm-domain` | Business logic, aggregates (NO Spring) |
| **DVMM Application** | `dvmm/dvmm-application` | Use cases, command/query handlers |
| **DVMM API** | `dvmm/dvmm-api` | REST controllers, DTOs |
| **DVMM Infrastructure** | `dvmm/dvmm-infrastructure` | Persistence, VMware integration |
| **DVMM App** | `dvmm/dvmm-app` | Spring Boot entry point |

## Architecture

DVMM follows a **Hexagonal Architecture** with **CQRS/Event Sourcing**, enforced by Konsist architecture tests.

```mermaid
graph TD
    subgraph Frontend
        UI[React Frontend<br/>shadcn/ui]
    end

    subgraph DVMM Platform
        API[REST API<br/>Spring WebFlux]
        CMD[Command Handlers<br/>CQRS Write Side]
        EVT[Event Store<br/>PostgreSQL]
        PROJ[Projections<br/>jOOQ Read Models]

        API --> CMD
        CMD --> EVT
        EVT --> PROJ
        API --> PROJ
    end

    subgraph Security Layer
        KC[Keycloak OIDC]
        JWT[JWT Validation]
        RLS[PostgreSQL RLS<br/>Tenant Isolation]

        API --> JWT
        JWT --> KC
        EVT --> RLS
        PROJ --> RLS
    end

    subgraph External
        VC[vCenter API<br/>VM Provisioning]
        MAIL[Email Service<br/>Notifications]

        CMD --> VC
        CMD --> MAIL
    end

    UI --> API
```

## Getting Started

### Prerequisites

- **Java 21+** (Eclipse Temurin recommended)
- **Docker** and **Docker Compose** (for Testcontainers)
- **Git**

### Build & Test

```bash
# Clone the repository
git clone git@github.com:acita-gmbh/eaf.git
cd eaf

# Build with all quality checks (recommended before commits)
./gradlew clean build

# Run tests only
./gradlew test

# Generate coverage report
./gradlew jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html

# Run mutation testing
./gradlew pitest
# Report: build/reports/pitest/index.html
```

### Module-Specific Commands

```bash
# Build specific module
./gradlew :dvmm:dvmm-app:build
./gradlew :eaf:eaf-core:build

# Test specific module
./gradlew :dvmm:dvmm-domain:test

# Run single test
./gradlew :dvmm:dvmm-app:test --tests "ArchitectureTest"
```

## Quality Gates

All quality gates are enforced in CI and block merges if not met:

| Gate | Threshold | Tool |
|------|-----------|------|
| Test Coverage | ≥80% | JaCoCo |
| Mutation Score | ≥70% | Pitest |
| Architecture Rules | All pass | Konsist |
| Security Scan | Zero critical | OWASP |

### Architecture Rules (ADR-001)

Enforced by Konsist tests in `ArchitectureTest.kt`:

- EAF modules **MUST NOT** import from `de.acci.dvmm.*`
- DVMM modules **CAN** import from `de.acci.eaf.*`
- `dvmm-domain` **MUST NOT** import from `org.springframework.*`

## Testing

The project follows a **Tests First** approach with integration tests taking priority:

```bash
# Run all tests
./gradlew test

# Run with detailed output
./gradlew test --info

# Run architecture tests
./gradlew :dvmm:dvmm-app:test --tests "*ArchitectureTest*"
```

### Testing Stack

- **JUnit 6** - Test framework
- **MockK** - Kotlin-native mocking
- **Testcontainers** - PostgreSQL, Keycloak integration tests
- **Konsist** - Architecture rule enforcement

## Project Structure

```
eaf/
├── build-logic/              # Gradle convention plugins
│   └── conventions/
│       └── src/main/kotlin/
│           ├── eaf.kotlin-conventions.gradle.kts
│           ├── eaf.spring-conventions.gradle.kts
│           ├── eaf.test-conventions.gradle.kts
│           └── eaf.pitest-conventions.gradle.kts
├── eaf/                      # Framework modules (reusable)
│   ├── eaf-core/
│   ├── eaf-eventsourcing/
│   ├── eaf-tenant/
│   ├── eaf-auth/
│   └── eaf-testing/
├── dvmm/                     # Product modules
│   ├── dvmm-domain/
│   ├── dvmm-application/
│   ├── dvmm-api/
│   ├── dvmm-infrastructure/
│   └── dvmm-app/
├── docs/                     # Documentation
│   ├── architecture.md
│   ├── prd.md
│   ├── epics.md
│   └── sprint-artifacts/
├── gradle/
│   └── libs.versions.toml    # Version catalog
└── settings.gradle.kts
```

## Contributing

We follow **Conventional Commits** and require all quality gates to pass:

```bash
# Commit message format
git commit -m "feat: implement VM request validation"
git commit -m "fix: correct tenant isolation in event store"
git commit -m "[DVMM-123] feat: add approval workflow"

# Branch naming
git checkout -b feature/story-1.2-eaf-core-module
git checkout -b fix/tenant-leak-in-projections
```

### Before Submitting a PR

1. Run `./gradlew clean build` - all tests must pass
2. Ensure ≥80% test coverage for new code
3. Follow architecture rules (Konsist will enforce)
4. Update documentation if needed

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture](docs/architecture.md) | System design, ADRs, module structure |
| [PRD](docs/prd.md) | 90 FRs + 95 NFRs, success criteria |
| [Epics](docs/epics.md) | 5 Epics, 51 Stories for MVP |
| [Security](docs/security-architecture.md) | Threat model, STRIDE, compliance |
| [Test Design](docs/test-design-system.md) | Testability concerns TC-001–TC-004 |
| [DevOps](docs/devops-strategy.md) | CI/CD, quality gates, monitoring |
| [UX Design](docs/ux-design-specification.md) | Design system, user journeys |

## License

Copyright 2025 ACCI (Axians Competence Center Infrastructure). All rights reserved.

---

<p align="center">
  <sub>Built with the <a href="docs/architecture.md">Enterprise Application Framework (EAF)</a></sub>
</p>
