# Requirements

### Functional Requirements (FR)

These requirements are derived directly from the "Core Features (Must Have) for MVP" section of the brief:

* **FR1:** The EAF must provide a Scaffolding CLI (v1) capable of generating new domain modules, aggregates, commands, and events adhering to the framework patterns.
* **FR2:** The framework must provide a "One-Command Environment Setup" (e.g., Docker Compose) that initializes all core services required for local development, including PostgreSQL and Keycloak.
* **FR3:** The persistence layer must be pre-configured for Axon Framework (using the native `JpaEventStorageEngine` against PostgreSQL) and provide automatic audit trails for events.
* **FR4:** The framework must implement the prototype's validated 3-layer multi-tenancy model (Request Filter, Service Validation, and DB/RLS).
* **FR5:** The framework must provide default authentication and authorization modules integrated with Keycloak OIDC, enforcing the prototype's 10-layer JWT validation standard.
* **FR6:** The EAF must provide standardized modules for structured logging and metrics *collection*. (Note: Visualization dashboards like Grafana are explicitly Post-MVP).

### Non-Functional Requirements (NFR)

These requirements are derived from the "Technical Considerations," "Strategy A" decision, and strategic constraints identified in the brief:

* **NFR1 (Deployment):** All EAF components must be deployable via Docker Compose onto customer-hosted single servers.
* **NFR2 (Hardware Compatibility):** All framework components, binaries, and dependencies must be compatible with `amd64`, `arm64`, and `ppc64le` processor architectures.
* **NFR3 (Stack Current):** The framework stack uses current stable versions: **Kotlin 2.2.20**, **Spring Boot 3.5.6**, and **Axon Framework 4.9.4**.
* **NFR4 (Architectural Pattern):** The architecture MUST adhere to Hexagonal Architecture, DDD, and CQRS/ES patterns, with boundaries programmatically enforced (via Spring Modulith).
* **NFR5 (Persistence Strategy):** The persistence layer (PostgreSQL) must be implemented as a swappable "adapter" (port), isolating the business logic to safeguard a future migration path to a streaming store (like NATS).
* **NFR6 (Extensibility):** The core command handling architecture must include the necessary interceptor "hooks" or "ports" (e.g., PRESCRIPT/POSTSCRIPT concepts) to allow the deferred Post-MVP "Dockets" orchestration engine (which will be Flowable) to plug in without requiring a core rewrite.
* **NFR7 (Security):** The MVP framework must be built to pass the ASVS 100% L1 / 50% L2 compliance targets.
* **NFR8 (Testing):** The framework must adhere to the "Integration-First" (Kotest/Testcontainers) philosophy and utilize patterns like the "Nullable Design Pattern".
* **NFR9 (Resilience):** The system must meet the RTO 4h / RPO 15m (HA/DR) requirement, utilizing PostgreSQL PITR and streaming replication.

---
