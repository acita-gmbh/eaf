# Enterprise Application Framework Product Requirements Document (PRD)

## Goals and Background Context

#### Goals

This PRD serves to execute the following primary business objectives defined in the Project Brief:

* **Accelerate Development Velocity & Efficiency:** Reduce developer overhead from 25% to <5%.
* **Drastically Improve Developer Onboarding & Productivity:** Reduce new developer time-to-productivity to <1 month (and time-to-first-production-deployment to <3 months).
* **Enhance Product Quality, Reliability, and Security:** Achieve "audit-readiness" (ISO 27001/NIS2) and meet ASVS 100% L1 / 50% L2 compliance targets by Q1 2026.
* **Unlock New Business & Market Opportunities:** Unblock the ZEWSSP product migration (by Q1 2026) and enable new enterprise customer acquisition (by Q2 2026).
* **Successfully Retire the Legacy DCA Framework:** Achieve full parity (excluding the deferred Dockets engine) and migrate the flagship DPCM product (by Q3 2026).

#### Background Context

The current DCA framework (2016) is a critical business bottleneck. It consumes 25% of developer effort in overhead, requires a 6+ month onboarding time for new engineers, and its architectural and security gaps (no multi-tenancy, impossible to audit) actively block new revenue opportunities by preventing updates to key products like ZEWSSP. This PRD defines the v0.1 EAF replacement, which solves these problems by providing a secure, testable, modern framework based on the prototype's successful architecture (Hexagonal/CQRS/Kotlin).

#### Change Log

| Date | Version | Description | Author |
| :--- | :--- | :--- | :--- |
| 2025-09-14 | 0.1.0 | Initial PRD draft based on v0.1 Project Brief | PM (John) |

---

## Requirements

#### Functional Requirements (FR)

These requirements are derived directly from the "Core Features (Must Have) for MVP" section of the brief:

* **FR1:** The EAF must provide a Scaffolding CLI (v1) capable of generating new domain modules, aggregates, commands, and events adhering to the framework patterns.
* **FR2:** The framework must provide a "One-Command Environment Setup" (e.g., Docker Compose) that initializes all core services required for local development, including PostgreSQL and Keycloak.
* **FR3:** The persistence layer must be pre-configured for Axon Framework (using the native `JpaEventStorageEngine` against PostgreSQL) and provide automatic audit trails for events.
* **FR4:** The framework must implement the prototype's validated 3-layer multi-tenancy model (Request Filter, Service Validation, and DB/RLS).
* **FR5:** The framework must provide default authentication and authorization modules integrated with Keycloak OIDC, enforcing the prototype's 10-layer JWT validation standard.
* **FR6:** The EAF must provide standardized modules for structured logging and metrics *collection*. (Note: Visualization dashboards like Grafana are explicitly Post-MVP).

#### Non-Functional Requirements (NFR)

These requirements are derived from the "Technical Considerations," "Strategy A" decision, and strategic constraints identified in the brief:

* **NFR1 (Deployment):** All EAF components must be deployable via Docker Compose onto customer-hosted single servers.
* **NFR2 (Hardware Compatibility):** All framework components, binaries, and dependencies must be compatible with `amd64`, `arm64`, and `ppc64le` processor architectures.
* **NFR3 (Stack Current):** The framework stack uses current stable versions: **Kotlin 2.2.20**, **Spring Boot 3.5.6**, and **Axon Framework 4.12.1**.
* **NFR4 (Architectural Pattern):** The architecture MUST adhere to Hexagonal Architecture, DDD, and CQRS/ES patterns, with boundaries programmatically enforced (via Spring Modulith).
* **NFR5 (Persistence Strategy):** The persistence layer (PostgreSQL) must be implemented as a swappable "adapter" (port), isolating the business logic to safeguard a future migration path to a streaming store (like NATS).
* **NFR6 (Extensibility):** The core command handling architecture must include the necessary interceptor "hooks" or "ports" (e.g., PRESCRIPT/POSTSCRIPT concepts) to allow the deferred Post-MVP "Dockets" orchestration engine (which will be Flowable) to plug in without requiring a core rewrite.
* **NFR7 (Security):** The MVP framework must be built to pass the ASVS 100% L1 / 50% L2 compliance targets.
* **NFR8 (Testing):** The framework must adhere to the "Integration-First" (Kotest/Testcontainers) philosophy and utilize patterns like the "Nullable Design Pattern".
* **NFR9 (Resilience):** The system must meet the RTO 4h / RPO 15m (HA/DR) requirement, utilizing PostgreSQL PITR and streaming replication.

---

## User Interface Design Goals

#### Overall UX Vision
The UX vision for the EAF (and the products built upon it) is one of utility, clarity, and operational efficiency. The interface must service both data-dense operator tasks (like the React-Admin UI concept from the prototype) and configuration-heavy developer tasks (like the TUI). The design must correct the "outdated UX" of the legacy system by being clean, responsive, and predictable.

#### Key Interaction Paradigms
The framework must support interfaces for:
* Data-dense grids and complex forms (for managing entities).
* Configuration editors (for YAML/JSON, like the Dockets script editor).
* Visualization tools (for workflows, like the Dockets DAG editor, and observability, which is Post-MVP).
* Terminal User Interfaces (TUI) for CLI operations.

#### Core Screens and Views (Conceptual)
The EAF itself (as an admin/operator portal) will require:
* Main Health Dashboard (monitoring core services).
* Security & Tenancy Configuration Panels (managing users/tenants via Keycloak).
* Observability Viewer (Post-MVP: viewing logs/metrics from Grafana).
* Dockets/Flowable: Workflow Builder & Execution Monitor (Post-MVP).

#### Accessibility
* **Accessibility:** WCAG AA (This is an assumption for enterprise-grade software, aligning with our ASVS security goals).

#### Branding
* All UIs must adhere to Axians corporate branding guidelines. The default aesthetic should be professional, clean, and data-focused.

#### Target Device and Platforms
* **Target Device and Platforms:** Web Responsive (Primarily desktop-focused for admin/dev tasks, but must be functional on tablets).

---

## Technical Assumptions

#### Repository Structure: Monorepo
* (Revision 2) This is a **Gradle Multi-Module Monorepo**. This structure was validated by the prototype and is required to manage the framework libraries, product apps (like React-Admin), and shared code (like testing patterns) efficiently.

#### Service Architecture
* (Revision 2) This is a critical decision. The v0.1 EAF architecture is defined by the successful prototype. The architecture **must** implement **Hexagonal Architecture** (with boundaries programmatically enforced by **Spring Modulith 1.4.3**), combined with **Domain-Driven Design (DDD)** and **CQRS/Event Sourcing (CQRS/ES)** patterns using the **Axon Framework 4.12.1**.

#### Testing Requirements (Revision 2, incorporating Test Philosophy)
* The testing requirement is a **Constitutional Test-Driven Development (TDD)** process, defined as "Test-First is Law". All development must follow the mandatory **RED-GREEN-Refactor cycle**.
* This mandates an **Integration-First** approach, inverting the test pyramid to a target ratio of **40-50% Integration Tests**.
* Per this philosophy, testing mandates are:
    1.  Real dependencies (specifically **PostgreSQL via Testcontainers**) are mandatory for integration tests.
    2.  The use of in-memory databases (like H2) for integration tests is **explicitly forbidden**.
    3.  The **"Nullable Design Pattern"** (testing real business logic against stubbed infrastructure) is the preferred pattern for fast domain tests.
    4.  The test framework must be **Kotest** (replacing JUnit).

#### Additional Technical Assumptions and Requests
* **Stack Current:** The stack uses current stable versions: **Kotlin 2.2.20** and **Spring Boot 3.5.6**.
* **Persistence Strategy:** Utilize **PostgreSQL (16.1+)**... implemented as a **swappable adapter**.
* **CPU Target:** Support for the **`ppc64le`** processor architecture is mandatory.
* **Security:** Authentication must integrate with **Keycloak OIDC**.
* **Workflow Engine:** Workflows (replacing legacy Dockets) will be managed by the **Flowable BPMN Engine**.

---

## Epic List (v0.1 - Revision 2)

1.  **Epic 1: Foundation & Onboarding**
    * **Goal:** Establish the complete Gradle monorepo, CI/CD pipeline, constitutional quality gates (ktlint, Detekt), and the one-command Docker Compose local environment.
2.  **Epic 2: Walking Skeleton (Hello Widget)**
    * **Goal:** Prove the core, end-to-end technical architecture: an API request that flows through the full Hexagonal/CQRS/ES pattern, persists to the PostgreSQL Event Store, and updates a read model projection.
3.  **Epic 3: Authentication (Core)**
    * **Goal:** Implement the complete Keycloak OIDC integration and the 10-layer JWT validation standard.
4.  **Epic 4: Multi-Tenancy Baseline**
    * **Goal:** Implement the core 3-layer tenant isolation model (filter, service, RLS).
5.  **Epic 5: Observability (Core Collection)** (Formerly Epic 6)
    * **Goal:** Implement the standardized modules for structured logging and metrics *collection*.
6.  **Epic 6: Core Framework Hooks (Flowable Prep)** (Formerly Epic 7)
    * **Goal:** Implement the architectural "hooks" and adapters (Axon-to-Flowable, Flowable-to-Ansible) required for the BPMN workflow engine.
7.  **Epic 7: Scaffolding CLI (v1)** (Formerly Epic 5)
    * **Goal:** Deliver the primary developer velocity tool: the v1 CLI capable of generating new Modulith modules, domain aggregates, APIs, and React-Admin components based on all finalized patterns from Epics 1-6.
8.  **Epic 8: Licensing Server (MVP Validation)**
    * **Goal:** Build the first complete internal application (the Licensing Server) *using only* the EAF components from Epics 1-7, validating all MVP Success Criteria.

---

## Epic 1: Foundation & Onboarding (Revision 2)

**Epic Goal:** This epic lays the complete foundational infrastructure for the entire EAF project. It delivers the Gradle monorepo structure, the automated "Constitutional" quality gates (ktlint, Detekt, TDD enforcement), and the "One-Command" local developer environment (Docker Compose stack) needed to support all subsequent development and meet our <1 month productivity goal.

#### Story 1.1: Initialize Gradle Monorepo Structure
* **As a** Core Developer, **I want** a defined Gradle Multi-Module Monorepo structure configured with version catalogs, **so that** all framework libraries, product apps (like React-Admin), and shared code are managed consistently in one place.
* **AC 1:** The project root is initialized with a Gradle wrapper and a **`build-logic` directory** for convention plugins.
* **AC 2:** The monorepo includes the required directory structure (`framework/`, `products/`, `shared/`, `apps/admin/`) defined in the prototype architecture.
* **AC 3:** A central Gradle version catalog (`libs.versions.toml`) is created and populated with the current stable stack versions (Kotlin 2.2.20, Spring Boot 3.5.6, Axon 4.9.4, Kotest).
* **AC 4:** **All shared build logic (including dependency versions and plugin configurations) MUST be defined via convention plugins within `build-logic` to ensure consistency**.

#### Story 1.2: Implement Constitutional Quality Gates
* **As a** Core Developer, **I want** automated quality gates for static analysis and testing integrated into the Gradle build, **so that** all code (human or AI-generated) strictly adheres to our mandatory standards.
* **AC 1:** The Gradle build integrates `ktlint` (1.4.0) and `Detekt` (1.23.7) with zero-tolerance failure policies; **this configuration MUST be applied to modules via the convention plugins** defined in Story 1.1.
* **AC 2:** The build process enforces the "Constitutional TDD" (RED-GREEN-Refactor) philosophy.
* **AC 3:** The build is configured to run Kotest and Testcontainers for the `integrationTest` source set; **this configuration MUST also be applied via the convention plugins**.

#### Story 1.3: Create One-Command Dev Stack (Docker Compose)
* **As a** New Developer, **I want** a single command (e.g., `./scripts/init-dev.sh`) to launch the complete local development stack, **so that** I can be productive on day one, meeting the <1 month productivity goal.
* **AC 1:** A root `compose.yml` file (or set of files) is created.
* **AC 2:** Running the initialization script successfully starts all required backing services: PostgreSQL 16.1+ and Keycloak 26.0.0. (Note: Flowable (Epic 6) will use the same Postgres instance).
* **AC 3:** All services are configured with default users, passwords, ports, and data volumes required for local development.

#### Story 1.4: Establish Foundational CI Pipeline
* **As a** Core Developer, **I want** a foundational CI (Continuous Integration) pipeline (e.g., GitHub Actions workflow), **so that** all pull requests are automatically validated against our mandatory quality gates.
* **AC 1:** The CI pipeline triggers automatically on all pull requests targeting the main branch.
* **AC 2:** The CI pipeline executes all required build steps: `compile`, `check` (ktlint/Detekt), and `test` (Kotest unit tests).
* **AC 3:** The CI pipeline executes the full `integrationTest` suite (using Testcontainers).
* **AC 4:** A pull request is algorithmically blocked from merging if any quality gate (linting, Detekt, or any test) fails.

---

## Epic 2: Walking Skeleton (Hello Widget)

**Epic Goal:** This epic is the single most critical technical de-risking milestone in the project. Its goal is to build the first, thinnest possible vertical slice through our entire chosen stack (Hexagonal/CQRS/ES, Axon, and our Postgres "Strategy A" adapter). This proves that the core architectural patterns function correctly end-to-end before any complex business logic is built.

#### Story 2.1: Define the 'Widget' Domain Aggregate
* **As a** Core Developer, **I want** to define a simple 'Widget' domain aggregate within its own Spring Modulith module, **so that** we have a testable entity for proving the CQRS/ES command flow.
* **AC 1:** A new module (e.g., `framework.widget`) is created and passes the Spring Modulith boundary checks (enforced by the gates from Epic 1).
* **AC 2:** The `Widget` aggregate is defined using Axon annotations (@Aggregate), adhering to Hexagonal principles.
* **AC 3:** The aggregate logic includes a `CreateWidgetCommand`, an `@CommandHandler`, a `WidgetCreatedEvent`, and an `@EventSourcingHandler`.
* **AC 4:** All logic is validated using "Constitutional TDD" (Kotest), specifically using the "Nullable Design Pattern" for fast domain tests (per NFR8).

#### Story 2.2: Implement the Command Side (Event Store Persistence)
* **As a** Core Developer, **I want** the `CreateWidgetCommand` to be handled and the resulting `WidgetCreatedEvent` to be successfully persisted, **so that** I can prove the "write-side" of CQRS and the native Axon-PostgreSQL adapter (Strategy A) are working correctly.
* **AC 1:** The Axon Framework is configured to use the `JpaEventStorageEngine` (or `JdbcEventStorageEngine`) targeting the PostgreSQL instance launched via Docker Compose (from Story 1.3).
* **AC 2:** A basic API endpoint (e.g., `POST /widgets`) exists to dispatch the `CreateWidgetCommand`.
* **AC 3:** An integration test (using Kotest and Testcontainers, per NFR8) successfully dispatches the command.
* **AC 4:** The integration test verifies (by querying the Axon `domain_event_entry` table directly in the Testcontainer) that the `WidgetCreatedEvent` was correctly persisted with the proper aggregate ID and sequence number.

#### Story 2.3: Implement the Read Side (Projection)
* **As a** Core Developer, **I want** a "Tracking Event Processor" to read the event stream from the Postgres event store and build a simple "Widget" read model (projection), **so that** the system's current state is available for queries.
* **AC 1:** A new JPA entity (e.g., `WidgetProjection`) and corresponding PostgreSQL table (the "read model") are created, separate from the event store tables.
* **AC 2:** An Axon Event Handler (Projection) is created (using `@EventHandler`) that subscribes to the event stream using a "Tracking Event Processor."
* **AC 3:** When a `WidgetCreatedEvent` is processed, the handler successfully creates and saves a new record in the `widget_projection` table.
* **AC 4:** The processor correctly persists its `TrackingToken` (processing checkpoint) to the database.

#### Story 2.4: Implement the Query Side (API)
* **As a** Core Developer, **I want** to query the "Widget" read model via a simple API, **so that** I can retrieve the current state of the system and prove the "read-side" of CQRS is working.
* **AC 1:** A Query (e.g., `FindWidgetQuery`) and a corresponding `@QueryHandler` are implemented.
* **AC 2:** A new API endpoint (e.g., `GET /widgets/{id}`) is created which dispatches the query.
* **AC 3:** The Query Handler interacts *only* with the `widget_projection` table (the read model), never the event store.
* **AC 4:** A final end-to-end integration test (using Kotest/Testcontainers) confirms the full "Walking Skeleton" flow: (1) POST command, (2) (Wait for projection), (3) GET query, (4) Assert the returned data matches the command data.

---

## Epic 3: Authentication (Core) (Revision 3)

**Epic Goal:** This epic delivers the complete, production-grade authentication (AuthN) layer for the EAF. It implements the prototype's validated 10-layer JWT standard, integrates with Keycloak, and builds the mandatory, **provisioned Keycloak Testcontainer** required for 100% accurate integration testing.

#### Story 3.1: Create Keycloak Testcontainer Configuration
* **As a** Core Developer, **I want** a configurable Keycloak Testcontainer build that is pre-provisioned with correct data, **so that** all integration tests run against a realistic, standardized identity provider that matches our production setup.
* **AC 1:** A configuration export (e.g., realm JSON file) is created that defines the EAF's required test realm, OIDC clients, and default test users/roles.
* **AC 2:** The Testcontainers configuration (defined in the convention plugins from Epic 1) is updated to automatically build and run Keycloak using this realm configuration on startup.
* **AC 3:** A utility is created within the test infrastructure to easily retrieve authentication tokens for specific test users (e.g., "admin-user," "basic-user") from the running container.

#### Story 3.2: Configure Spring Security & Keycloak OIDC Integration
* **As a** Core Developer, **I want** Spring Security configured to delegate authentication to Keycloak via the OIDC protocol, **so that** the EAF relies on our enterprise standard identity provider.
* **AC 1:** Required Spring Security dependencies (like `spring-boot-starter-oauth2-resource-server`) are added via the convention plugins.
* **AC 2:** Application configuration is added to connect to the OIDC discovery endpoint of the Keycloak instance (running via Docker Compose (Story 1.3) locally, or the Testcontainer (Story 3.1) in tests).
* **AC 3:** A new, basic "secured" endpoint (e.g., `/api/secure/hello`) is created.
* **AC 4:** An integration test (using Kotest) confirms: 1) A request without a token is rejected (401), and 2) A request using a valid token from the **provisioned Keycloak Testcontainer (Story 3.1)** is accepted (200).

#### Story 3.3: Implement 10-Layer JWT Validation Standard
* **As a** Core Developer, **I want** to implement the prototype's validated 10-layer JWT validation standard as a core framework component, **so that** all incoming API requests are robustly secured, meeting our ASVS goals.
* **AC 1:** A custom validation filter or Spring Security converter/validator chain is implemented.
* **AC 2:** The validation chain successfully performs all 10 validation layers (Format, Algorithm (RS256-only), Signature, Claims Schema, Time (exp/iat), Issuer/Audience, Token Blacklist (stubbed), Tenant ID presence, Rate Limiting (stubbed), and Audit Logging).
* **AC 3:** Integration tests (using the **provisioned Keycloak Testcontainer (3.1)**) confirm that valid tokens pass all layers, and tokens with invalid signatures, expired timestamps, missing tenant claims, or wrong algorithms are correctly rejected (401).

#### Story 3.4: Secure the Walking Skeleton (Widget) API
* **As a** Core Developer, **I want** to apply the new security policies (from 3.3) to the Widget API (from Epic 2), **so that** our "Walking Skeleton" is fully secured end-to-end.
* **AC 1:** The `POST /widgets` (Story 2.2) and `GET /widgets/{id}` (Story 2.4) endpoints are now secured and require a valid, authenticated token.
* **AC 2:** The end-to-end integration test from Story 2.4 (AC4) is updated.
* **AC 3:** The updated test now uses the test utility (from 3.1) to fetch a valid JWT from the **provisioned Keycloak Testcontainer** before executing the test, confirming the full CQRS flow works when secured.
* **AC 4:** A new integration test confirms that requests to the Widget API *without* a token fail (401).

---

## Epic 4: Multi-Tenancy Baseline (Revision 4)

**Epic Goal:** This epic delivers the complete data isolation architecture for the EAF. It implements the prototype's validated **3-layer tenant isolation model**, ensuring that all data access (Commands, Queries, and Projections) is programmatically isolated by tenant, fulfilling a critical security requirement. This epic also solves the async context propagation required by CQRS.

#### Story 4.1: Implement Layer 1 (Request Layer): TenantContext Filter
* **As a** Core Developer, **I want** a request filter that extracts the validated tenant ID (from the JWT) and places it into a secure, request-scoped `TenantContext`, **so that** all downstream application logic and database sessions can reliably access the active tenant ID.
* **AC 1:** A `TenantContext` (likely using `ThreadLocal`) is created to hold the active `tenant_id`.
* **AC 2:** A Spring `Filter` is implemented that runs *after* the AuthN filter (from Epic 3).
* **AC 3:** The filter extracts the `tenant_id` claim (which was validated in Story 3.3) from the security principal and populates the `TenantContext`.
* **AC 4:** The filter implements a **"Fail-Closed" design**: If the JWT is valid but the `tenant_id` claim is missing, the request is immediately rejected (403 Forbidden).
* **AC 5:** The filter ensures the `TenantContext` is cleared (e.g., in a `finally` block) after the request completes to prevent context leakage.

#### Story 4.2: Implement Layer 2 (Service Layer): Tenant Boundary Validation
* **As a** Core Developer, **I want** the Axon Command Handlers (Service Layer) to validate that the active tenant (from `TenantContext`) is authorized to operate on the requested aggregate, **so that** tenants cannot maliciously issue commands against another tenant's data (defense-in-depth).
* **AC 1:** All new aggregates (like `CreateWidgetCommand`) must be automatically assigned the `tenant_id` from the `TenantContext` when created.
* **AC 2:** When loading an existing aggregate (e.g., for an Update command), the framework must verify that the aggregate's stored tenant ID matches the `TenantContext` tenant ID.
* **AC 3:** If the tenant IDs do not match, a Security Exception is thrown, the transaction is rolled back, and the operation fails (e.g., 403 Forbidden or 404 Not Found).
* **AC 4:** An integration test (using the Keycloak Testcontainer from Epic 3) verifies that User A (Tenant A) *cannot* issue a valid command against an aggregate belonging to Tenant B.

#### Story 4.3: Implement Layer 3 (Database Layer): PostgreSQL Row-Level Security (RLS)
* **As a** Core Developer, **I want** Row-Level Security (RLS) enabled on all tenant-owned tables (both event store and projections), **so that** data is isolated at the database layer itself as the final security guarantee.
* **AC 1:** A database interceptor (e.g., a JDBC interceptor or JPA listener) is created that sets the current PostgreSQL session variable (e.g., `SET app.current_tenant_id = ?`) using the ID from the `TenantContext` (from 4.1) at the start of every transaction.
* **AC 2:** RLS Policies are enabled via migration scripts on the `domain_event_entry` (Event Store) and `widget_projection` (Read Model) tables.
* **AC 3:** The RLS policies strictly enforce that all SELECT, INSERT, UPDATE, and DELETE operations can only apply to rows where the `tenant_id` column matches the `app.current_tenant_id` session variable.
* **AC 4:** Integration tests verify that even a direct SQL query (simulating an SQL injection vulnerability) attempting to read data *without* the session variable correctly set returns zero rows.

#### Story 4.4: Implement Tenant Context Propagation for Async Processors
* **As a** Core Developer, **I want** the `TenantContext` to be reliably propagated to all asynchronous Axon Event Processors, **so that** the projection handlers (our read-side) can successfully write data to the RLS-protected database tables.
* **AC 1:** A mechanism (such as an Axon Message Interceptor or custom Unit of Work component) is implemented to read the `tenant_id` from the metadata of every event being processed by a Tracking Event Processor.
* **AC 2:** This mechanism correctly populates the `TenantContext` (from Story 4.1) *before* the asynchronous `@EventHandler` logic executes.
* **AC 3:** The database interceptor (from Story 4.3) successfully reads this propagated `TenantContext` and sets the PostgreSQL session variable (`app.current_tenant_id`) for the projection's transaction.
* **AC 4:** The end-to-end integration tests are updated to verify that the `widget_projection` table *is successfully written to* (passing the RLS check from 4.3) and that the data is correct.

#### Story 4.5: Migrate Widget Domain Code from Framework to Product Module
* **As a** Framework Architect, **I want** the Widget domain code (aggregate, handlers, tests) migrated from `framework/widget/` to `products/widget-demo/`, **so that** framework modules contain only reusable infrastructure and can be published as libraries while domain logic lives in product modules.
* **AC 1:** All Widget domain code moved from `framework/widget/` to `products/widget-demo/`
* **AC 2:** `framework/widget/` module completely removed from project
* **AC 3:** Package names updated: `com.axians.eaf.framework.widget.*` → `com.axians.eaf.products.widgetdemo.*`
* **AC 4:** All imports updated across codebase (no compilation errors)
* **AC 5:** Integration tests migrated and passing in products/widget-demo
* **AC 6:** products/widget-demo builds and runs successfully
* **AC 7:** NO references to framework/widget remain in any build files or code
* **AC 8:** Documentation updated (component-specifications.md, CLAUDE.md if needed)

---

## Epic 5: Observability (Core Collection)

**Epic Goal:** This epic delivers the core data *collection* infrastructure for observability, fulfilling our MVP scope. This epic does *not* include building dashboards (like Grafana), which is deferred to Post-MVP. This epic solves the "Limited Observability" pain point of the legacy DCA framework and is a non-negotiable MVP requirement.

#### Story 5.1: Implement Standardized Structured Logging
* **As an** Operator, **I want** all framework and application logs to be output in a standardized, structured JSON format, **so that** they can be easily ingested and parsed by an external logging stack (like Loki/Grafana, per the prototype roadmap).
* **AC 1:** A logging convention (e.g., via a Logback/Logstash encoder configuration) is defined in the `build-logic` convention plugins (from Epic 1) and applied to all modules.
* **AC 2:** All log output MUST be in a structured JSON format.
* **AC 3:** All log entries MUST automatically include critical context fields: `service_name`, `trace_id`, and `tenant_id` (retrieved from the `TenantContext` [from Story 4.1]).
* **AC 4:** Integration tests confirm that logs are written in the correct JSON format and include the required context (especially the propagated `tenant_id`).

#### Story 5.2: Implement Prometheus Metrics (Micrometer)
* **As an** Operator, **I want** all services built on the EAF to expose standardized application metrics, **so that** we can monitor system health via a Prometheus-compatible endpoint.
* **AC 1:** The Spring Boot Actuator, Micrometer, and Prometheus registry dependencies are added and managed via the convention plugins.
* **AC 2:** The `/actuator/prometheus` endpoint is enabled, secured (requires AuthN from Epic 3), and exposed.
* **AC 3:** Core metrics (JVM, HTTP server requests, Axon message processing) are exposed by default.
* **AC 4:** All relevant metrics are automatically tagged with `tenant_id` (where applicable) and `service_name` to allow for granular filtering.

#### Story 5.3: Implement OpenTelemetry (Tracing) Configuration
* **As a** Core Developer, **I want** OpenTelemetry configured by default in the framework, **so that** all requests (API and internal Axon messages) generate and propagate distributed traces.
* **AC 1:** The necessary OpenTelemetry (OTel) dependencies and agent (if applicable) are integrated into the core framework.
* **AC 2:** Trace context (Trace IDs, Span IDs) is automatically propagated across all API requests and asynchronous Axon Commands/Events.
* **AC 3:** The active `trace_id` is automatically injected into the structured JSON logs (from Story 5.1) for log/trace correlation.

---

## Epic 6: Core Framework Hooks (Flowable Prep) (Revision 3)

**Epic Goal:** This epic replaces the core risk of the legacy "Dockets" engine. It integrates the **Flowable BPMN engine** into the EAF stack, providing a robust, industry-standard workflow platform. This epic delivers the core engine integration and the adapters needed for Flowable to communicate with Axon and Ansible.

#### Story 6.1: Integrate Flowable Engine & Database Schema
* **As a** Core Developer, **I want** the Flowable BPMN Engine libraries integrated into the stack and configured to use our primary PostgreSQL database, **so that** we have a foundational workflow capability managed within our single database instance.
* **AC 1:** Required Flowable Spring Boot Starter dependencies are added via convention plugins.
* **AC 2:** Flowable is configured to use the main PostgreSQL database (from Story 1.3) but in its own dedicated schema (e.g., `flowable`).
* **AC 3:** On application startup, Flowable correctly runs its migration scripts (or validates its schema) in Postgres.
* **AC 4:** An integration test confirms the Flowable Engine beans (e.g., `ProcessEngine`, `RuntimeService`) are correctly initialized in the Spring context.

#### Story 6.2: Create Flowable-to-Axon Bridge (Command Dispatch)
* **As a** BPMN Process, **I want** to execute a Java Delegate (Service Task) that dispatches an Axon Command via the `CommandGateway`, **so that** a workflow can initiate business logic in our CQRS aggregates.
* **AC 1:** A reusable Java Delegate (e.g., `DispatchAxonCommandTask`) is created that can be configured in a BPMN model.
* **AC 2:** The task correctly retrieves variables from the Flowable context and uses them to build and dispatch a valid Axon Command (e.g., `CreateWidgetCommand`).

#### Story 6.3: Create Axon-to-Flowable Bridge (Event Signal)
* **As a** BPMN Process, **I want** to pause and wait for a specific Axon Event, **so that** the workflow can react to business logic that has successfully completed.
* **AC 1:** A standard Axon Event Handler (Projection) is created that listens for specific events (e.g., `WidgetCreatedEvent`).
* **AC 2:** The handler correctly correlates the event to the running BPMN process (perhaps via an ID) and uses the Flowable `RuntimeService` to send a message or signal to a waiting process instance.
* **AC 3:** Integration tests confirm a BPMN process (with a "Receive Event Task") successfully pauses and resumes when the corresponding Axon event is published.

#### Story 6.4: Create Ansible Service Task Adapter
* **As a** Core Developer, **I want** a custom Flowable Java Delegate (Service Task) that can execute Ansible playbooks, **so that** we can replicate the core function of the legacy Dockets system.
* **AC 1:** A new Service Task (e.g., `RunAnsiblePlaybookTask`) is created.
* **AC 2:** This task is configurable via the BPMN model (e.g., passing in the playbook name, inventory, and variables).
* **AC 3:** This task utilizes the connection configuration and execution logic identified in the Dockets Analysis (connecting via SSH, running playbooks).
* **AC 4:** Integration tests confirm this service task can successfully execute a simple "hello world" Ansible playbook.

#### Story 6.5: Implement Workflow Error Handling (Compensating Actions)
* **As a** Core Developer, **I want** the framework to support compensating commands for failed workflows, **so that** we can manage the distributed transaction risk identified in our analysis (Risk Assessment 5).
* **AC 1:** A "compensating command" (e.g., `CancelWidgetCreationCommand`) and corresponding event handler are added to the Widget aggregate (from Epic 2).
* **AC 2:** A BPMN "Error Boundary Event" is configured in the workflow.
* **AC 3:** If a downstream step (like the Ansible Task) fails, the BPMN error path must successfully trigger the `DispatchAxonCommandTask` (from 6.2) to send the `CancelWidgetCreationCommand`, reversing the initial transaction.

#### Story 6.6: Implement "Dockets Pattern" BPMN Template
* **As a** Core Developer, **I want** a template BPMN 2.0 XML file that replicates the legacy "Dockets Hook" pattern, **so that** we have a clear path for migrating DPCM/ZEWSSP automation.
* **AC 1:** A BPMN 2.0 XML file is created that defines a workflow: (1) Start Event, (2) Service Task (Ansible PRESCRIPT), (3) Service Task (Dispatch Axon CORECOMMAND), (4) Receive Event Task (wait for Axon Event), (5) Service Task (Ansible POSTSCRIPT), (6) Error Boundary Event (Compensation), (7) End Event.
* **AC 2:** This BPMN process definition successfully deploys to the Flowable engine.

---

## Epic 7: Scaffolding CLI (v1)

**Epic Goal:** This epic delivers the core Developer Experience promise of the EAF. It creates the v1 Scaffolding CLI, a tool designed to automate the creation of all boilerplate required by our complex (Hexagonal/CQRS/Modulith/Flowable) stack, enabling a new developer to meet the <1 month productivity goal. (This epic is now correctly sequenced *after* all core patterns are finalized).

#### Story 7.1: Initialize CLI Framework Shell
* **As a** Core Developer, **I want** a basic, executable CLI application framework (e.g., using Kotlin with Picocli), **so that** we have a stable platform to build our specific code generators on.
* **AC 1:** A new Gradle sub-project (e.g., `eaf-cli`) is created in the monorepo.
* **AC 2:** The CLI application is executable via Gradle and produces basic output (e.g., `eaf --version`, `eaf scaffold --help`).
* **AC 3:** The CLI is configured with a templating engine (like Mustache or Velocity) to generate code from templates.

#### Story 7.2: Create "New Module" Generator
* **As a** Core Developer, **I want** the CLI to have a `scaffold module` command, **so that** I can create a new, empty Spring Modulith-compliant module instantly that adheres to our Foundation setup (Epic 1).
* **AC 1:** Running `eaf scaffold module <name>` creates a new, complete Gradle sub-project (e.g., in the `products/` directory).
* **AC 2:** The new module's `build.gradle.kts` file is automatically configured to use the convention plugins (from Story 1.2).
* **AC 3:** A valid `ModuleMetadata.kt` file (required by Spring Modulith) is generated to define the new module's boundaries and name.

#### Story 7.3: Create "New Aggregate" Generator
* **As a** Developer, **I want** the CLI to have a `scaffold aggregate` command, **so that** I can generate a complete CQRS/ES vertical slice (command, event, aggregate, projection, and API stubs) in seconds, adhering to all framework patterns.
* **AC 1:** Running `eaf scaffold aggregate <Name> --module <moduleName>` generates all required Kotlin files for a new Axon Aggregate inside the target module.
* **AC 2:** The generated files MUST include templates/stubs for:
    * The Domain Aggregate (`<Name>Aggregate.kt`) with Axon annotations.
    * The API (Commands, Events, and Queries files for the aggregate).
    * The Projection/EventHandler (`<Name>Projection.kt`).
    * API Endpoints (stubs for the Spring Controllers and Query Handlers).
* **AC 3:** The generated templates must correctly include all required patterns (e.g., Tenancy checks from Epic 4, Logging from Epic 6).
* **AC 4:** The generated code (including generated test stubs) must pass all Constitutional TDD and Quality Gates (from Story 1.2) immediately upon generation.

#### Story 7.4: Create "React-Admin" Generator
* **As a** Developer, **I want** the CLI to have a `scaffold ra-resource` command, **so that** it generates the basic React-Admin Typescript resource files needed for the aggregate I just created in the admin UI.
* **AC 1:** Running `eaf scaffold ra-resource <Name>` generates the necessary `.tsx` files (e.g., CreateResource, EditResource, ListResource) within the `apps/admin` codebase.
* **AC 2:** The generated UI resource components correctly reference the API endpoints and data structures (Typescript types) generated in Story 7.3.

---

## Epic 8: Licensing Server (MVP Validation)

**Epic Goal:** This is the capstone epic for the v0.1 MVP. Its purpose is not to build a new core feature, but to **validate the entire framework** by being the first *product* built using only the EAF components from Epics 1-7. This epic directly fulfills all three MVP Success Criteria.

#### Story 8.1: Scaffold the 'Licensing-Server' Product Module
* **As a** Developer (simulating a new onboarded user), **I want** to use the new Scaffolding CLI (from Epic 7) to generate the complete module structure for the new "Licensing Server" application, **so that** I can start building business logic in minutes, not months.
* **AC 1:** Execute the `eaf scaffold module licensing-server` command (built in Story 7.2).
* **AC 2:** The new Gradle module is created in the `/products/` directory and successfully passes the CI pipeline (from Story 1.4) on its initial (empty) commit.

#### Story 8.2: Scaffold and Implement 'Product' Aggregate
* **As a** Developer, **I want** to use the CLI (Epic 7) to scaffold a 'Product' aggregate (representing products that can be licensed), **so that** I can implement the basic CRUD business logic for managing products.
* **AC 1:** Execute the `eaf scaffold aggregate Product ...` and `eaf scaffold ra-resource Product` commands (built in Stories 7.3, 7.4).
* **AC 2:** The business logic for basic CRUD operations (Create, Update, Read) for Products is implemented using the generated templates.
* **AC 3:** All logic fully adheres to the Constitutional TDD (RED-GREEN-Refactor) mandate, using Kotest and Testcontainers (from Story 1.2).

#### Story 8.3: Scaffold and Implement 'License' Aggregate
* **As a** Developer, **I want** to use the CLI (Epic 7) to scaffold the core 'License' aggregate and implement its business logic, **so that** the application can manage the lifecycle of a software license.
* **AC 1:** Execute the `eaf scaffold aggregate License ...` and `eaf scaffold ra-resource License` commands (Stories 7.3, 7.4).
* **AC 2:** Implement the core business logic commands (e.g., `IssueLicenseCommand`, `ActivateLicenseCommand`, `ValidateLicenseCommand`) within the generated Axon aggregate.
* **AC 3:** The 'License' aggregate correctly integrates with AuthN (Epic 3) and Multi-Tenancy (Epic 4) (e.g., a License is issued to a specific Tenant ID).
* **AC 4:** All integration tests (using the Keycloak Testcontainer from 3.1) successfully pass.

#### Story 8.4: Validate Admin UI and Observability Integration
* **As an** Operator, **I want** to manage the new Licensing Server via the EAF's React-Admin portal and monitor its health, **so that** I can confirm the framework's default UI and observability hooks are working.
* **AC 1:** The generated React-Admin UI (from Story 7.4) successfully Lists, Creates, and Edits Products and Licenses via the generated APIs.
* **AC 2:** All operations performed in the Licensing Server correctly emit standardized JSON logs (from Story 5.1) tagged with the correct Tenant ID.
* **AC 3:** The `/actuator/prometheus` endpoint (from Story 5.2) correctly exposes distinct metrics (Axon, HTTP, etc.) for the new `licensing-server` module.

#### Story 8.5: Pass Formal MVP Validation Criteria
* **As the** Product Manager (John), **I want** to formally validate that the completed Licensing Server (built on the EAF) satisfies our defined MVP Success Criteria, **so that** the v0.1 MVP can be approved.
* **AC 1:** **(Validation of SC #2):** A developer successfully completed Stories 8.1-8.3 within the 3-day time box, validating our <1 month productivity goal.
* **AC 2:** **(Validation of SC #3):** The Security Team formally reviews the completed Licensing Server (built on EAF Epics 1-7) and confirms it meets the ASVS 100% L1 / 50% L2 compliance targets.
* **AC 3:** **(Validation of SC #1):** The Licensing Server application is successfully deployed to a staging/production environment using the EAF's deployment patterns.
