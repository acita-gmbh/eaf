# High Level Architecture

### Technical Summary

This document outlines a unified full-stack architecture for the EAF. The system is a **Gradle Monorepo** designed for deployment on customer-hosted servers via **Docker Compose**.

The backend is a "Modular Monolith" implemented in **Kotlin 2.0.10** on **Spring Boot 3.3.5**. It is built using **Hexagonal Architecture** (with boundaries programmatically enforced by **Spring Modulith**), **CQRS/Event Sourcing** (via **Axon Framework 4.9.4**), and includes the **Flowable BPMN engine** to replace the legacy "Dockets" workflow functionality.

Per our mandated persistence strategy, the persistence layer uses **PostgreSQL 16.1+** as the event store (implemented as a swappable adapter) and also for read model projections.

The frontend consists of an internal **React-Admin Operator Portal** and supports external Product UIs (React, Vaadin, TUI). Security is federated to **Keycloak OIDC**. The entire framework is built using a mandatory **Constitutional TDD (Kotest/Testcontainers)** methodology.

### Platform and Infrastructure Choice

This decision is mandated by the Project Brief and PRD constraints.

* **Platform:** **On-Premise / Customer-Hosted (via Docker Compose)**. The architecture is not designed for a specific serverless cloud vendor, but rather as a self-contained stack that a customer runs on their own hardware.
* **Key Services (The Core Stack):** The EAF `compose.yml` (from PRD Epic 1.3) must provide:
    1.  The EAF Application Service (Kotlin/Spring/Axon/Flowable).
    2.  PostgreSQL 16.1+ (This single instance will host multiple schemas: the event store, projections, and the Flowable engine schema).
    3.  Keycloak 26.0.0 (For identity and access management).
* **Deployment Constraints:** The architecture must support `amd64`, `arm64`, and `ppc64le` processor architectures.

### Repository Structure

This decision is mandated by the PRD Technical Assumptions.

* **Structure:** **Gradle Multi-Module Monorepo**.
* **Tooling:** Gradle (with **Convention Plugins** in `build-logic` mandated by PRD Epic 1).
* **Package Organization:** The structure validated by the prototype will be used:
    * `framework/` (Core libraries)
    * `products/` (Deployable Spring Boot apps)
    * `shared/` (Shared code, including Kotlin API types and TS types)
    * `apps/admin/` (React-Admin UI)

### High Level Architecture Diagram

This diagram visualizes the interaction between the primary components (containers and logical blocks) defined in the PRD.

```mermaid
graph TD
    subgraph Customer Network
        direction TB
        subgraph EAF Docker Compose Stack (On-Prem Host)
            direction LR
            
            subgraph EAF Application Service (Spring Boot/Kotlin)
                direction TB
                API[API Layer (REST/GraphQL)]
                Modulith[Spring Modulith (Boundary Enforcement)]
                CQRS[Axon Engine (CQRS/ES)]
                BPMN[Flowable Engine (BPMN)]
                Adapters[Hexagonal Adapters (Ports)]
                API --> Modulith;
                Modulith -- contains --> CQRS;
                Modulith -- contains --> BPMN;
                CQRS --> Adapters;
                BPMN --> Adapters;
            end

            subgraph PostgreSQL (Database)
                direction TB
                ES(Schema 1: Event Store);
                PROJ(Schema 2: Projections);
                FLOW(Schema 3: Flowable Tables);
            end

            KEYCLOAK[Keycloak (OIDC)];

            Adapters -- JDBC --> ES;
            Adapters -- JDBC --> PROJ;
            Adapters -- JDBC --> FLOW;
            API -- AuthN/AuthZ --> KEYCLOAK;
        end

        USER[Operator (User)] -- HTTPS --> RA[React-Admin Portal (Browser)];
        RA -- API Calls --> API;
        
        subgraph External Products
             PROD_UI[Product UIs (React/Vaadin/TUI)]
             PROD_UI -- API Calls --> API;
        end
    end
````

### Architectural and Design Patterns (Revised List)

These patterns are mandated by the v0.1 PRD and derived from the successful prototype. This architecture *is* the implementation of these patterns:

  * **Hexagonal Architecture (enforced by Spring Modulith):** Mandated by PRD NFR4. Isolates our domain logic from infrastructure details.
  * **CQRS/Event Sourcing:** Mandated by PRD NFR4. Uses Axon Framework for implementation.
  * **Postgres-as-Adapter:** Our mandated persistence strategy (NFR5).
  * **BPMN Workflow Integration (Flowable):** Mandated replacement for legacy Dockets (PRD NFR6, Epic 7).
  * **Constitutional TDD / Integration-First Testing:** Our mandatory quality strategy (PRD NFR8).
  * **Functional Error Handling (Arrow):** Mandated by the prototype. Domain logic MUST return `Either<Error, Success>`.
  * **API Error Standardization (ProblemDetails):** All API error responses (exceptions) MUST be mapped (via `@ControllerAdvice`) to the standard RFC 7807 (Problem+JSON) format.
  * **Type-Safe Read Projections (jOOQ):** All "read-side" projections and query handlers (e.g., PRD Epic 2.4) MUST utilize jOOQ (rather than JPA) for building type-safe, optimized SQL queries.

-----
