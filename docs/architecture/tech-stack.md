# Tech Stack

## Critical Architecture Decisions

**PostgreSQL as Event Store (Cost‑Constrained, Pragmatic Choice)**:

* PostgreSQL is the sole event store for cost reasons; no Axon Server/EventStoreDB in scope
* **Mandatory optimizations**: BRIN indexes, time‑based partitioning, autovacuum/vacuum tuning, connection pooling
* **KPIs tracked for health**: p95 latency <200ms, processor lag <30s, conflict rate <1%
* Future alternatives may be revisited as a budgeted initiative; no migration commitment in current scope

**Axon Framework 4.9.4 → 5.x Strategy**:

* Start with stable 4.9.4 for immediate development
* Parallel PoC development against Axon 5 milestones
* Design patterns to be "v5-ready" (programmatic configuration, immutable entities)
* Migration planned before production deployment to avoid technical debt

**Concurrency Model Decision**:

* Use Spring Boot virtual threads (Java 21) exclusively
* Avoid mixing with Kotlin coroutines to prevent complexity
* Reevaluate only if performance monitoring indicates clear need

### Technology Stack Table (Revision 2)

| Category | Technology | Version | Purpose | Rationale |
| :--- | :--- | :--- | :--- | :--- |
| **Backend Language** | **Kotlin** | **2.0.10 (PINNED)** | Primary BE Language | **CRITICAL CONSTRAINT**: Pinned to this exact version for compatibility with Spring Boot 3.3.5 and critical static analysis tools (`ktlint 1.4.2`, `detekt 1.23.7`). Version 2.0.21+ breaks tool compatibility. This version has been proven to work reliably with Spring Boot 3.3.x in EAF4 production environments. **NO UPGRADES PERMITTED** without extensive compatibility validation. |
| **Backend Framework** | **Spring Boot** | **3.3.5 (LOCKED)** | Core Application Framework | **Mandatory Constraint.** Locked version for Modulith/Tooling compatibility. |
| **Modularity** | **Spring Modulith** | **1.3.0** | Enforces modular architecture | Verifies logical dependencies between modules at runtime, crucial for maintaining a clean DDD structure. Requires Kotlin-specific ModuleMetadata classes. |
| **CQRS Framework** | **Axon Framework** | **4.9.4** | CQRS/ES Pattern | **Mandatory Constraint.** Core of the EAF architecture. Migration to 5.x planned. |
| **Database (All)** | **PostgreSQL** | **16.1+** | Primary DB | **Mandatory.** Hosts Event Store, Projections, and Flowable schemas. Cost‑constrained decision: Event store remains PostgreSQL‑backed (no Axon Server/EventStoreDB). Apply BRIN indexes, time partitioning, autovacuum tuning, and pooled connections. |
| **Cache/Token Store** | **Redis** | **7.2.5** | Caching / Messaging | Pinned to stable GA version. 7.4 is unreleased. Required for JWT token blacklist management and emergency security procedures. |
| **Workflow Engine** | **Flowable** | **7.1.0** | BPMN / Orchestration | **Mandatory Constraint.** Pinned to stable 2024 release, not 2025.1.x. A powerful, Java-based engine for orchestrating complex, customizable workflows. |
| **Authentication** | **Keycloak** | **26.0.0** | OIDC / Identity | **Mandatory Constraint.** Pinned to prevent API drift. Open-source IAM solution that supports OIDC and meets our flexible deployment requirements. |
| **Func. Programming**| **Arrow** | **1.2.4** | Functional Error Handling | Mandated by prototype for Either<E,A> domain error handling. |
| **Data Access (Query)**| **jOOQ** | 3.x (Latest) | Type-Safe SQL Queries | Mandated for all Read-Side Projections (CQRS queries) to replace JPA for reads. |
| **Build Tool** | **Gradle (Monorepo)** | **8.14** | Build/Dependencies | **Mandatory Constraint.** Latest stable 8.x version as of January 2025. Will be configured with a Version Catalog and Convention Plugins to enforce standards. |
| **Containerization** | **Docker / Podman** | Latest | Packaging and running the application stack | Ensures a consistent, portable development and deployment environment. |
| Frontend Language | TypeScript | 5.x | Primary FE language | Required for React development and type safety. |
| Frontend Framework | React (with React-Admin) | Latest | Admin Portal UI | React-Admin is a powerful "batteries-included" solution for the required "cockpit" and management UIs, accelerating development. |
| UI Component Lib | Material-UI (MUI) | 5.x | Core UI Kit | Non-negotiable dependency of React-Admin. |
| State Management | Zustand / React Context | 4.x | FE State | Lightweight default for managing global UI state. |
| **CSS Framework** | **MUI (Emotion / Styled)** | 5.x | Styling | The required styling engine for the MUI component library. |
| **TUI Framework** | **Lanterna** | 3.1.x | Framework for Terminal User Interfaces | A robust Java/Kotlin library for building TUIs. Keeps the TUI within our core JVM stack for architectural consistency. |
| **Frontend Framework** | **Vaadin** | Latest | Alternative UI development for applications built on EAF | The PRD specifies Vaadin as another frontend option. |
| **Developer Portal** | **Docusaurus** | Latest | Documentation and learning platform | Provides a modern, searchable, and maintainable developer portal out of the box. |
| **Dev Tooling** | **Node.js / npm** | LTS | Prerequisite for Docusaurus and Git hooks | Required for running the developer portal and managing script-based tooling like pre-commit hooks. |
| API Style | REST / CQRS | N/A | API Pattern | CQRS (Axon) for core logic; REST endpoints for external interaction (per PRD Epic 2). |
| Bundler | Vite | Latest | FE Build Tool | Modern default for bundling React/TS applications. |
| IaC / Deployment | Docker Compose | Latest | On-Prem Deployment | **Mandatory Constraint.** Required deployment target. |
| CI/CD | GitHub Actions | N/A | CI Pipeline | Mandated by PRD Epic 1.4. |
| **Monitoring** | **Micrometer + Prometheus**| N/A | Metrics Collection | Mandated by PRD Epic 6.2. |
| **Logging** | **SLF4J/Logback (JSON)** | N/A | Structured Logging | Mandated by PRD Epic 6.1. |

## Development & Quality Tools

| Category | Technology | Version | Purpose | Rationale |
| :--- | :--- | :--- | :--- | :--- |
| **Code Formatting** | **ktlint** | **1.4.2** | Kotlin code style enforcement | Validates EAF4 compatibility. Version must stay compatible with Kotlin 2.0.10. |
| **Static Analysis** | **Detekt** | **1.23.7** | Kotlin static analysis and security rules | Validates EAF4 compatibility. Enforces zero violations policy for quality gates. |
| **Architecture Testing** | **Konsist** | **0.18.0** | Architectural compliance verification | Validates Spring Modulith boundaries and hexagonal architecture compliance. |
| **Testing Framework** | **Kotest** | **5.9.1** | Primary testing framework (NOT JUnit) | **NO MOCKS ENFORCED**: Supports BehaviorSpec for "Integration-First, No Mocks" approach with Arrow extensions. Mocking libraries (Mockito, MockK) are BANNED. |
| **Integration Testing** | **Testcontainers** | **1.20.4** | Real dependencies for integration tests | **NO MOCKS ENFORCED**: Essential for database, Redis, and Keycloak integration testing. All dependencies must use real containers, never mocks or in-memory substitutes. Requires spring-boot-testcontainers for @ServiceConnection support. |
| **Mutation Testing** | **Pitest** | **1.17.5** | Test quality verification | Validates 80% minimum mutation coverage to ensure test effectiveness. |
| **API Documentation** | **Dokka** | **1.9.10** | Kotlin API documentation generation | Official JetBrains documentation engine for Kotlin projects. Generates multi-module HTML documentation with cross-references, GitHub source linking, and architectural layer visibility. Compatible with Kotlin 2.0.10 K2 compiler. |
| Frontend Testing | Jest + RTL | Latest | FE Unit/Integration | Industry standard for React. |
| E2E Testing | Playwright | Latest | End-to-End Validation | Modern standard for full-stack E2E testing. |

## PostgreSQL Performance Requirements

**Mandatory Optimizations for Event Store**:

* **BRIN Indexes**: Time-based indexing for event streams
* **Time-based Partitioning**: Monthly/quarterly partitions for event tables
* **Autovacuum Tuning**: Optimized for high-write event workloads
* **Connection Pooling**: PgBouncer or equivalent for connection management
* **Monitoring KPIs**:
  * p95 latency < 200ms for event writes
  * Projection processor lag < 30s
  * Lock conflict rate < 1%

## Testing Strategy Enforcement

**Constitutional TDD Requirements**:

* **No Mocks Rule**: Testcontainers for stateful dependencies, Nullable Design Pattern for stateless
* **Integration-First**: 40-50% integration tests, inverted test pyramid
* **Coverage Gates**: 85%+ line coverage, 80%+ mutation coverage
* **Real Dependencies**: PostgreSQL, Redis, Keycloak via Testcontainers
* **Kotest Only**: JUnit explicitly forbidden

-----
