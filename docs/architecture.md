# Enterprise Application Framework (v0.1) Fullstack Architecture Document

## Introduction

This document outlines the complete fullstack architecture for the Enterprise Application Framework (v0.1), including backend systems, frontend implementation, and their integration. It serves as the single source of truth for AI-driven development, ensuring consistency across the entire technology stack. This unified approach combines what would traditionally be separate backend and frontend architecture documents, streamlining the development process for modern fullstack applications where these concerns are increasingly intertwined.

#### Starter Template or Existing Project
N/A - Greenfield project. This is a greenfield project being built from scratch to precisely match the non-negotiable technology stack (Kotlin/Spring/Axon/Postgres) and Gradle Monorepo structure defined in the v0.1 PRD and validated by the EAF prototype.

#### Change Log

| Date | Version | Description | Author |
| :--- | :--- | :--- | :--- |
| 2025-09-14 | 0.1.0 | Initial Architecture draft based on PRD v0.1 | Architect (Winston) |

---

## High Level Architecture

#### Technical Summary

This document outlines a unified full-stack architecture for the EAF. The system is a **Gradle Monorepo** designed for deployment on customer-hosted servers via **Docker Compose**.

The backend is a "Modular Monolith" implemented in **Kotlin 2.0.10** on **Spring Boot 3.3.5**. It is built using **Hexagonal Architecture** (with boundaries programmatically enforced by **Spring Modulith**), **CQRS/Event Sourcing** (via **Axon Framework 4.9.4**), and includes the **Flowable BPMN engine** to replace the legacy "Dockets" workflow functionality.

Per our mandated persistence strategy, the persistence layer uses **PostgreSQL 16.1+** as the event store (implemented as a swappable adapter) and also for read model projections.

The frontend consists of an internal **React-Admin Operator Portal** and supports external Product UIs (React, Vaadin, TUI). Security is federated to **Keycloak OIDC**. The entire framework is built using a mandatory **Constitutional TDD (Kotest/Testcontainers)** methodology.

#### Platform and Infrastructure Choice

This decision is mandated by the Project Brief and PRD constraints.

* **Platform:** **On-Premise / Customer-Hosted (via Docker Compose)**. The architecture is not designed for a specific serverless cloud vendor, but rather as a self-contained stack that a customer runs on their own hardware.
* **Key Services (The Core Stack):** The EAF `compose.yml` (from PRD Epic 1.3) must provide:
    1.  The EAF Application Service (Kotlin/Spring/Axon/Flowable).
    2.  PostgreSQL 16.1+ (This single instance will host multiple schemas: the event store, projections, and the Flowable engine schema).
    3.  Keycloak 26.0.0 (For identity and access management).
* **Deployment Constraints:** The architecture must support `amd64`, `arm64`, and `ppc64le` processor architectures.

#### Repository Structure

This decision is mandated by the PRD Technical Assumptions.

* **Structure:** **Gradle Multi-Module Monorepo**.
* **Tooling:** Gradle (with **Convention Plugins** in `build-logic` mandated by PRD Epic 1).
* **Package Organization:** The structure validated by the prototype will be used:
    * `framework/` (Core libraries)
    * `products/` (Deployable Spring Boot apps)
    * `shared/` (Shared code, including Kotlin API types and TS types)
    * `apps/admin/` (React-Admin UI)

#### High Level Architecture Diagram

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

#### Architectural and Design Patterns (Revised List)

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

## Tech Stack

### Critical Architecture Decisions

**PostgreSQL as Event Store**: Cost-constrained decision with mandatory optimizations (BRIN indexes, partitioning, autovacuum tuning). KPIs: p95 latency <200ms, processor lag <30s.

**Axon Framework 4.9.4 → 5.x Strategy**: Start with stable 4.9.4, parallel PoC with v5, migration before production.

**Constitutional TDD**: No mocks rule enforced - Testcontainers for stateful deps, Nullable Design Pattern for stateless.

#### Technology Stack Table (Revision 2)

**Core Backend Stack**
| Technology | Version | Purpose | Constraint Level |
| :--- | :--- | :--- | :--- |
| **Kotlin** | **2.0.10 (PINNED)** | Primary BE Language | **CRITICAL** - Locked for tool compatibility |
| **Spring Boot** | **3.3.5 (LOCKED)** | Core Framework | **MANDATORY** - Modulith compatibility |
| **Spring Modulith** | **1.3.0** | Module boundaries | **MANDATORY** - Kotlin ModuleMetadata required |
| **Axon Framework** | **4.9.4** | CQRS/ES Pattern | **MANDATORY** - Core architecture |
| **PostgreSQL** | **16.1+** | Event Store/Projections | **MANDATORY** - Single DB strategy |
| **Flowable** | **7.1.0** | BPMN Orchestration | **MANDATORY** - Workflow engine |
| **Keycloak** | **26.0.0** | OIDC/Identity | **MANDATORY** - Security provider |

**Development & Quality Tools**
| Technology | Version | Purpose | Enforcement |
| :--- | :--- | :--- | :--- |
| **Kotest** | **5.9.1** | Testing Framework | **MANDATORY** - JUnit forbidden |
| **Testcontainers** | **1.20.4** | Integration Testing | **MANDATORY** - No mocks rule |
| **Konsist** | **0.18.0** | Architecture Testing | **MANDATORY** - Boundary verification |
| **Pitest** | **1.17.5** | Mutation Testing | **MANDATORY** - 80% coverage |
| **ktlint** | **1.4.2** | Code Formatting | **MANDATORY** - Style enforcement |
| **Detekt** | **1.23.7** | Static Analysis | **MANDATORY** - Zero violations |

**Frontend & Infrastructure**
| Technology | Version | Purpose | Notes |
| :--- | :--- | :--- | :--- |
| React + React-Admin | 18.x/Latest | Admin Portal UI | Material-UI dependency |
| TypeScript | 5.x | FE Language | Type safety required |
| **jOOQ** | 3.x | Read Projections | **MANDATORY** - No JPA for reads |
| **Arrow** | **1.2.4** | Functional Programming | **MANDATORY** - Either<E,A> pattern |
| Redis | 7.2.5 | Cache/Token Store | JWT blacklist management |
| Gradle | 8.14 | Build Tool | Version Catalog + Convention Plugins |

**Quality Requirements**:
- 85%+ line coverage, 80%+ mutation coverage
- PostgreSQL performance: BRIN indexes, partitioning, connection pooling
- Real dependencies only (Testcontainers), no H2/mocks

-----

## Data Models

#### Widget

  * **Purpose:** The 'Widget' is the simple, test aggregate used to validate the end-to-end "Walking Skeleton" flow (PRD Epic 2).
  * **Key Attributes:** `widgetId: UUID`, `name: String`, `tenantId: UUID`.
  * **TypeScript Interface (Code-Gen):** `WidgetProjection { widgetId, tenantId, name, createdDate, lastModifiedDate }`.
  * **Relationships:** Belongs to one (1) Tenant.

#### Tenant (Revision 2)

  * **Purpose:** The 'Tenant' aggregate is the root entity for all data isolation (PRD Epic 4), representing a customer organization. Manages the tenant's business lifecycle (Active/Suspended).
  * **Key Attributes:** `tenantId: UUID`, `name: String`, `status: Enum`, **`keycloakRealmOrGroupId: String`** (The critical link to the IAM system).
  * **TypeScript Interface (Code-Gen):** `TenantProjection { tenantId, name, status, keycloakRealmOrGroupId, createdDate }`.
  * **Relationships:** Root entity. External 1:1 mapping to Keycloak entity.

#### Product

  * **Purpose:** The 'Product' aggregate (PRD Epic 8.2). Represents software that can be licensed (e.g., 'DPCM'). This data is owned by the internal "Admin Tenant".
  * **Key Attributes:** `productId: UUID`, `tenantId: UUID` (Admin Owner), `sku: String`, `name: String`.
  * **TypeScript Interface (Code-Gen):** `ProductProjection { productId, tenantId, sku, name, description?, createdDate }`.
  * **Relationships:** Owned by Admin Tenant. 1:M relationship *with* License.

#### License (Revision 2)

  * **Purpose:** The core business entity (PRD Epic 8.3). Represents a customer Tenant's permission to use a Product.
  * **Key Attributes:** `licenseId: UUID`, `tenantId: UUID` (Customer Tenant Owner - the RLS key), `productId: UUID` (Reference to Product), `licenseKey: String`, `status: Enum`, `expires: Date`, **`licenseLimits: Map<String, String>`** (JSONB field for dynamic limits, e.g., {"cpu_cores": "8"}).
  * **TypeScript Interface (Code-Gen):** `LicenseProjection { licenseId, tenantId, productId, productName?, status, expires, licenseLimits: Record<string, string> }`.
  * **Relationships:** Belongs to one (1) Customer Tenant (RLS boundary). Belongs to one (1) Product.

-----

## API Specification (Revision 2)

Our API style is **REST / CQRS**. This requires an OpenAPI 3.0 specification.

#### REST API Specification (OpenAPI 3.0) - MVP v0.1

```yaml
openapi: 3.0.1
info:
  title: "Enterprise Application Framework (EAF) v0.1 API"
  version: "0.1.0"
  description: |
    The official API for the EAF. 
    This API uses CQRS patterns (Commands/Queries) exposed via REST endpoints.
    All endpoints (except OIDC discovery) are secured by Keycloak (JWT Bearer Token) and are multi-tenant aware.
servers:
  - url: /api/v1
    description: EAF v1 API Root

security:
  - bearerAuth: []

paths:
  /widgets:
    post:
      summary: "Epic 2.2: Create Widget (Dispatches CreateWidgetCommand)"
      operationId: createWidget
      security:
        - bearerAuth: []
      requestBody:
        description: The CreateWidgetCommand payload
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                name:
                  type: string
      responses:
        '202':
          description: "Accepted. The command has been dispatched successfully."
          content:
            application/json:
              schema:
                type: object
                properties:
                  widgetId:
                    type: string
                    format: uuid
        '400':
          description: "Bad Request. Response uses RFC 7807."
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
        '401':
          description: "Unauthorized (Invalid/Missing JWT)."
        '403':
          description: "Forbidden (Valid JWT, insufficient permissions or Tenant error)."

  /widgets/{id}:
    get:
      summary: "Epic 2.4: Get Widget Projection (Dispatches FindWidgetQuery)"
      operationId: getWidgetById
      security:
        - bearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: "Success. Returns the Widget read model projection."
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/WidgetProjection'
        '404':
          description: "Not Found. Response uses RFC 7807."
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT

  schemas:
    WidgetProjection:
      type: object
      properties:
        widgetId: { type: string, format: uuid }
        tenantId: { type: string, format: uuid }
        name: { type: string }
        createdDate: { type: string, format: date-time }
        lastModifiedDate: { type: string, format: date-time }
    
    # (TenantProjection, ProductProjection, and LicenseProjection schemas also defined here)

    ProblemDetail:
      type: object
      properties:
        type: { type: string, format: uri, description: "A URI reference that identifies the problem type." }
        title: { type: string, description: "A short, human-readable summary of the problem type." }
        status: { type: number, format: int32, description: "The HTTP status code." }
        detail: { type: string, description: "A human-readable explanation specific to this occurrence of the problem." }
        instance: { type: string, format: uri, description: "A URI reference that identifies the specific occurrence of the problem." }
```

#### Post-MVP Evolution: GraphQL Gateway

Per user requirement: The Post-MVP roadmap includes adding a GraphQL endpoint. This will be implemented as a **Gateway Layer** (using a tool capable of OpenAPI-to-GraphiQL conversion) that automatically provides GraphQL queries/mutations by consuming the versioned v0.1 OpenAPI (REST) specification defined above. This avoids logic duplication while providing a flexible API for future consumers.

-----

## Components

(Logical components within the Modular Monolith and Monorepo).

#### Component: CQRS/ES Core (Axon Engine) (Revision 2)

  * **Responsibility:** Manages the entire command/event/query lifecycle (PRD Epic 2).
  * **Dependencies:** Depends on all foundational components: `Database Schema`, `Data Models`, `Security`, `Tenancy`, `Observability`, and `Flowable Engine` (bidirectional dependency).
  * **Tech:** Axon Framework 4.9.4.

#### Component: BPMN Workflow Engine (Flowable)

  * **Responsibility:** Manages all workflow orchestration (replaces legacy Dockets, fulfills PRD Epic 7).
  * **Dependencies:** Depends on `Database Schema (flowable)` and the `CQRS Core` (to dispatch commands).
  * **Tech:** Flowable 7.x.

#### Component: Security Component (AuthN)

  * **Responsibility:** Implements core authentication (PRD Epic 3), the 10-Layer JWT standard, and the Keycloak Testcontainer config.
  * **Dependencies:** Depends on external Keycloak. Provides the `Principal` context to all other components.
  * **Tech:** Spring Security, Keycloak 26.0.0.

#### Component: Tenancy Component (Isolation)

  * **Responsibility:** Implements the 3-Layer tenancy model (Filter, Service Validation, RLS) and the mandatory async context propagation (PRD Epic 4), leveraging Micrometer Context Propagation.
  * **Dependencies:** Depends on `Security Component`. Provides the `TenantContext` to all components (CQRS, Flowable, Obs).
  * **Tech:** Spring Filters, Micrometer Context Propagation.

#### Component: Observability Component

  * **Responsibility:** Implements the "three pillars" collection (Logging, Metrics, Tracing) (PRD Epic 6).
  * **Dependencies:** Depends on `Tenancy Component` (for `tenant_id` tagging) and `Security Component` (to secure endpoints).
  * **Tech:** Micrometer/Prometheus, OTel, SLF4J/Logback (JSON).

#### Component: Scaffolding CLI (Application)

  * **Responsibility:** Standalone developer tool (PRD Epic 7) that automates code generation (Aggregates, UI resources) based on the finalized patterns from all framework components.
  * **Dependencies:** Consumes the patterns defined by all other components.
  * **Tech:** Kotlin, Picocli, Templating engine.

#### Component: React-Admin Portal (Application)

  * **Responsibility:** The internal Operator UI (living in `apps/admin/`). Primary consumer of the API Spec.
  * **Dependencies:** Must adhere to the UI/UX Spec. Codebase is modified by the `Scaffolding CLI`.
  * **Tech:** React 18, React-Admin, MUI, Vite.

#### Component: Licensing Server Module (Application)

  * **Responsibility:** The first "Product Application" built *on* the EAF (PRD Epic 8). The MVP Validation capstone.
  * **Dependencies:** Consumes ALL foundational components (CQRS, Security, Tenancy, Obs).
  * **Tech:** Inherits the full EAF stack.

-----

## External APIs

#### Keycloak Identity and Admin API

  * **Purpose:** (1) OIDC/AuthN validation for the EAF (Epic 3). (2) Programmatic provisioning of tenant realms/groups via the Admin API (Epic 4 / UX Flow 1).
  * **Integration:** Admin API calls (Sagas) MUST be orchestrated via the Flowable Engine (PRD Epic 7) to manage the distributed transaction risk.

#### Ansible / SSH Protocol Interface

  * **Purpose:** Allows the Flowable engine (Epic 7.4) to execute automation playbooks (replacing legacy Dockets).
  * **Integration:** This requires replicating the legacy environment (JSON callbacks, custom collections, ENV VARs). This interface faces a **critical API mismatch** (Legacy GraphQL vs. New REST), requiring the legacy collections to be rewritten OR the Post-MVP GraphQL Gateway to be implemented first.

-----

## Core Workflows

#### Flow 1: CQRS "Walking Skeleton" (Create Widget & Query)

  * **Summary:** Illustrates the core end-to-end CQRS pattern, showing the decoupled Write Side (POST $\rightarrow$ 202 Accepted $\rightarrow$ Event Store) and Read Side (Async Projection $\rightarrow$ Read DB $\rightarrow$ GET Query).
  * **Risk Mitigation:** Identifies the need for the Frontend to handle Eventual Consistency (via WebSocket push).

#### Flow 2: Tenant Creation Saga (Error & Compensation Path)

  * **Summary:** Illustrates our most complex distributed transaction, managed by Flowable.
  * **Flow:** API $\rightarrow$ Start BPMN $\rightarrow$ Task 1 (Axon Command: Create Tenant) $\rightarrow$ Task 2 (Call Keycloak Admin API).
  * **Mitigation:** If Task 2 fails, the BPMN Error Event triggers Task 3 (Compensating Axon Command: MarkTenantFailed), ensuring data consistency.

#### Flow 3: Dockets/Flowable Orchestration (Happy Path)

  * **Summary:** Visualizes the Post-MVP replacement pattern for Dockets.
  * **Flow:** BPMN orchestrates: Task 1 (Ansible PRESCRIPT) $\rightarrow$ Task 2 (Axon CORECOMMAND) $\rightarrow$ (Wait for Event) $\rightarrow$ Task 3 (Ansible POSTSCRIPT). This matches the legacy requirement.

-----

## Database Schema

(Logical separation of the single Postgres instance).

1.  **`flowable` schema:** Auto-managed by the Flowable engine (Epic 7.1).
2.  **`eaf_event_store` schema (Axon):** Standard Axon DDL (e.g., `domain_event_entry`), **modified** to add the mandatory `tenant_id` column required for RLS (Epic 4.3).
3.  **`eaf_projections` schema (Read Models):** Custom, denormalized tables (managed by jOOQ) for our projections:
      * `tenant_projection` (Strict RLS)
      * `product_projection` (Global-Read / Admin-Write RLS Policy)
      * `license_projection` (Strict RLS, includes `license_limits JSONB` column)

-----

## Frontend Architecture (Revision 2)

  * **Component Architecture:** React-Admin/MUI components in `apps/admin/src/`.
  * **State Management:** Zustand + React Context.
  * **Routing:** React Router (via React-Admin `<Resource>` components), using `React.lazy()` for all routes.
  * **Frontend Services Layer (Critical Requirement):** Replaces the standard REST data provider. Must use a **Push-Based (WebSocket) strategy**. The Axon Projection (Backend) publishes to Redis Pub/Sub, the EAF server pushes this via WebSocket, and the React-Admin UI listens for this push notification (e.g., `PROJECTION_UPDATED`) to trigger a data refresh. This avoids polling and correctly handles our asynchronous `202 Accepted` CQRS response.

-----

## Frontend Implementation Guidance

### Directory Blueprint

```plaintext
apps/admin/src/
├── app/                    # App shell, layout, theme, routing bootstrap
├── components/             # Shared presentational components (`*.component.tsx`)
├── features/
│   ├── dashboard/
│   ├── security/
│   │   └── tenants/
│   └── licensing/
│       ├── products/
│       └── licenses/
├── pages/                  # Route-entry components (`*.view.tsx`)
├── providers/              # DataProvider, AuthProvider, WebSocket hooks
├── services/               # API interaction utilities (`*.service.ts`)
├── state/                  # Zustand stores and contexts
├── testing/                # RTL helpers, fixtures
└── index.tsx
```

Naming conventions:

  * Views end with `.view.tsx`; reusable components end with `.component.tsx`.
  * Feature hooks live in `features/<domain>/hooks/` and must be prefixed with `use`.
  * Storybook stories co-locate next to components as `<Name>.stories.tsx`.

### Component Template

All new components follow this accessibility-first template:

```tsx
type TenantSelectorProps = {
  value: string;
  onChange: (tenantId: string) => void;
  disabled?: boolean;
};

export function TenantSelector({ value, onChange, disabled = false }: TenantSelectorProps) {
  return (
    <Autocomplete
      aria-label="tenant selector"
      options={useTenantOptions()}
      value={value}
      onChange={(_, id) => onChange(String(id))}
      loadingText="Loading tenants"
      disabled={disabled}
      data-testid="tenant-selector"
    />
  );
}
```

Components must document expected states in JSDoc, expose `data-testid` selectors, and include keyboard-friendly props.

### Routing & Resource Table

| Route | Resource Key | Chunk | Roles | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `/dashboard` | `dashboard` | `dashboard.chunk.tsx` | Operator, Security | Default landing page with system health cards |
| `/security/tenants` | `tenants` | `security-tenants.chunk.tsx` | Security Admin | Tenant CRUD, RLS policy indicators |
| `/products` | `products` | `licensing-products.chunk.tsx` | Operator | SKU uniqueness guard, bulk import hooks |
| `/licenses` | `licenses` | `licensing-licenses.chunk.tsx` | Operator | Wizard flow honoring eventual consistency |

Register routes in `app/app-routes.tsx` and document breadcrumbs, access roles, and projection dependencies.

### Data Provider & WebSocket Pattern

  * `providers/data-provider.ts` wraps React-Admin `fetchJson` to inject Keycloak tokens and convert RFC 7807 payloads into RA-friendly errors.
  * `providers/projection-socket.ts` manages the WebSocket connection, subscribing to projection channels (`tenants`, `products`, `licenses`) and dispatching `invalidateResource` events into Zustand stores.
  * New projections extend the `ProjectionChannel` union type and register handlers in `projection-handlers.ts`.

### Developer Experience Assets

  * Storybook stories are required for any exported component.
  * RTL tests live in `__tests__/` folders and use the shared `renderWithProviders` helper plus `axe` accessibility assertions.
  * CI enforces `npm run lint`, `npm test`, and Chromatic visual regression checks on every PR.

-----

## Unified Project Structure

(This is the mandatory Gradle Monorepo layout).

```plaintext
eaf-monorepo/
├── .github/                     # CI/CD workflows (Story 1.4)
├── apps/
│   └── admin/                   # React-Admin Portal (FE Component)
├── build-logic/                 # Gradle Convention Plugins (Story 1.1)
├── framework/                   # Core Framework Modules (Security, Tenancy, Obs, Workflow, CQRS)
├── products/                    # Deployable Spring Boot Product Apps
│   └── licensing-server/        # (Epic 8)
├── shared/                      # Shared code
│   ├── shared-api/              # (NEW) Shared Kotlin (Axon API: Commands, Events, Queries)
│   └── shared-types/            # Code-generated TypeScript interfaces
├── gradle/
│   └── libs.versions.toml       # Version Catalog (Story 1.1)
├── scripts/
│   └── init-dev.sh              # One-Command Onboarding (Story 1.3)
├── compose.yml                  # Local dev stack (Postgres, Keycloak)
...
```

-----

## Development Workflow

**One-Command Onboarding with Comprehensive Quality Gates**

* **Core Principles:** Constitutional TDD, Quality-First, Integration-First, Modular Development with Spring Modulith boundaries
* **Onboarding:** Single `./scripts/init-dev.sh` script sets up Docker services, secrets, Git hooks, developer portal, IDE config
* **Daily Development:** Multi-terminal workflow (infrastructure, backend, frontend, docs portal)
* **Scaffolding CLI:** Production-ready code generation for modules, aggregates, APIs, tests, and documentation
* **Quality Enforcement:** Automated pre-commit hooks, architectural compliance, security scanning, performance baselines

**Development Commands:**
- `./gradlew clean build` - Full quality check (local CI simulation)
- `./gradlew test -P fastTests=true` - Fast feedback cycle with nullable pattern
- `./gradlew verifyAllModules` - Architecture compliance verification
- `eaf scaffold module <name>` - Generate compliant module structure

**Performance Optimizations:**
- Container reuse patterns for 50% faster test runs
- Nullable pattern for 61.6% improvement in business logic tests
- Parallel test execution with timing baselines

**Full specification:** See `docs/architecture/development-workflow.md`.

-----

## Operational Playbooks

### Deployment Pipeline

  * GitHub Actions workflow stages: **Build** (compile, lint, test), **Image Publish** (multi-arch Docker build), **Staging Deploy** (docker compose target with smoke tests), **Production Approval** (manual gate), **Production Deploy** (rolling update, post-deploy verification).
  * Each stage uploads artefacts (SBOM, test reports) to enable traceability and audit.

### Environment Promotion

  * Environments: `dev` (shared), `staging` (release candidate), `prod` (customer). Promotion requires green integration tests plus manual QA sign-off documented in PR notes.
  * Database migrations run via Flyway with the `baselineOnMigrate` flag; staging migrations execute 24 h before production to catch schema drift.

### Rollback & Recovery

  * Playbook distinguishes configuration rollback (redeploy previous Helm/compose config) from code rollback (redeploy previous image tag). PITR (Point-In-Time Recovery) for Postgres is rehearsed quarterly; recovery point objective 15 minutes, recovery time objective 4 hours.
  * License issuance commands replay automatically after recovery via Axon tracking tokens.

### Infrastructure as Code

  * Terraform modules describe Docker hosts, Vault, Redis, Postgres, and monitoring stack. CI runs `terraform fmt`, `validate`, and `tflint` before applying changes.
  * State stored in Terraform Cloud with workspace-per-environment, guarded by Sentinel policies (e.g., forbid public security groups).

### Runbook Library & Escalation

  * `docs/runbooks/` contains operator guides for Keycloak outage, projection lag, Postgres failover, and Vault token exhaustion.
  * Escalation matrix: L1 (on-call engineer) -> L2 (platform specialist) -> L3 (vendor liaison). SLA: acknowledge P1 incidents within 15 minutes, resolve within 4 hours.

-----

## Deployment Architecture (Revision 2)

(Replaced "Unified Container" strategy).

  * **Strategy:** We will ship **two separate application containers** (a "Two-Container" approach):
    1.  **Frontend Container:** A lightweight NGINX container serving the static (production-built) React-Admin files.
    2.  **Backend Container:** The EAF Spring Boot "Modular Monolith" (containing the Java/Kotlin/Axon/Flowable code).
  * **CI/CD Pipeline:** The GitHub Actions pipeline (Validate Job) runs Testcontainers on `amd64`. The Build Job MUST create multi-arch images (`linux/amd64`, `linux/arm64`, `linux/ppc64le`) for BOTH containers (NGINX and Spring Boot).
  * **HA/DR:** Meets RTO/RPO goals. The FE (NGINX) container is stateless. The BE (Spring) container requires the Active-Passive model, coordinated with Postgres Streaming Replication.

-----

## Resilience and Performance Engineering

### Reliability Patterns

  * **Retries & Backoff:** All outbound adapters (Keycloak Admin API, Redis, Ansible runners) employ Spring Retry with jittered exponential backoff (max 5 attempts, base 250 ms, cap 30 s). Failures publish `IntegrationFailureEvent` records for operator awareness.
  * **Circuit Breaking:** Resilience4j circuit breakers guard Keycloak and Redis access (slow-call rate threshold 50% over 10 s, wait duration in open state 20 s). Breaker metrics surface via `/actuator/health` and Prometheus.
  * **Graceful Degradation:** When projections lag, the frontend displays cached read models stamped with `lastProjectionAt`. Commands acknowledged with HTTP 202 render "pending" status banners until projections refresh.

### Capacity Targets

  * SLO: 200 concurrent operator sessions, 50 sustained writes/sec, 500 sustained reads/sec.
  * Load testing: Gatling suite runs nightly against staging; regressions flagged when 95th percentile latency exceeds 350 ms for read endpoints or 500 ms for command submissions.
  * Baseline sizing: Backend container 2 vCPU / 4 GB RAM; frontend container 0.5 vCPU / 512 MB; Postgres primary 4 vCPU / 8 GB with streaming replica.

### Caching & Scaling

  * Redis-backed projection cache (TTL 5 s) accelerates dashboard queries while preserving eventual consistency semantics.
  * Horizontal scale via active-passive pairs behind Traefik; failover promoted within 60 s leveraging Postgres replication slots.
  * Background jobs record resource utilisation to inform autoscaling thresholds for customers who deploy to orchestration platforms.

### Operational Benchmarks

  * Circuit-breaker open events trigger PagerDuty if five occurrences happen within 15 minutes.
  * Projection lag >15 s raises warning; >60 s becomes a critical alert.
  * Compose stack includes `otel-collector` to capture performance traces for SLA debugging.

-----

## Security

**Comprehensive Defense-in-Depth with Production-Validated Patterns**

* **Core Principles:** Input validation, Keycloak OIDC, 10-Layer JWT Validation, 3-Layer Tenancy Model (RLS), HashiCorp Vault, RFC 7807 API security, data protection, dependency security, SAST integration testing.

**10-Layer JWT Validation System:**
- Comprehensive validation covering format, signature, algorithm, claims, time, issuer/audience, revocation, roles, user, and injection detection
- Real cryptographic validation with RS256-only enforcement
- Redis-based token blacklist with emergency recovery procedures
- ASVS 5.0 Level 2 compliance targeting

**3-Layer Tenant Isolation:**
- **Layer 1:** Request filter extracts tenant from JWT
- **Layer 2:** Service boundary validation with AOP
- **Layer 3:** Database interceptor with automatic tenant filtering
- Defense-in-depth prevents cross-tenant data access

**Emergency Security Recovery:**
- 5-phase recovery process (0-120 hours)
- Automated security validation suite (43+ tests)
- Real-time threat detection and response
- ASVS compliance restoration within 5 days

**Advanced Security Features:**
- Security-lite testing profile for fast validation
- Production-validated implementation patterns
- Structured security logging and metrics
- Comprehensive attack scenario coverage

**Full specification:** See `docs/architecture/security.md`.

### Network Segmentation & Hardening

  * Deployment topology isolates tiers: Traefik ingress and NGINX frontend reside in a DMZ subnet; the Spring Boot service, Redis, and Flowable workers live in a protected application subnet; Postgres and Vault run in a data subnet exposed only to application security groups.
  * All east-west traffic uses mTLS with certificates rotated by Vault. External TLS terminates at Traefik with automatic Let's Encrypt renewal.

### Identity & Access Governance

  * Service-to-service access relies on short-lived Vault-issued credentials (24 h TTL, renewable). IAM policies grant least privilege (e.g., backend role can read Postgres credentials but cannot modify Vault mounts).
  * Operator roles map to Keycloak groups; licensing flows require `role_product_manager`, tenant administration requires `role_security_admin`.

### Rate Limiting & Abuse Controls

  * Traefik enforces per-tenant throttles of 100 requests/min with bursts of 20. Suspicious patterns move clients to a degrated rate of 20 requests/min for 15 minutes.
  * JWT validation checks issuer, audience, tenant claim, and token freshness (max age 5 minutes skew). Replay detection uses Redis nonce store for high-risk operations.

### Data Lifecycle & Retention

  * Event store retains 18 months of history by default; projections retain 24 months with monthly archival to cold storage. Audit logs remain 90 days online and 12 months in WORM storage.
  * Scheduled jobs (Spring Batch) clean expired licenses and anonymise soft-deleted tenants, respecting legal hold flags stored in Vault.

### Security Monitoring & Response

  * SIEM integration: Keycloak, application audit logs, and Vault events ship to the central SOC via Fluent Bit.
  * Alert thresholds: more than five failed logins per minute per tenant triggers a medium-severity alert; circuit breaker open events escalate to on-call.
  * Incident response runbooks define containment, eradication, and recovery steps; tabletop exercises occur twice per year.

-----

## Dependency Lifecycle Management

  * **Version Cadence:** Renovate automation raises weekly patch/minor updates. We conduct quarterly dependency retrospectives and plan major upgrades annually with compatibility testing.
  * **Compatibility Matrix:** `docs/architecture/compatibility-matrix.md` codifies supported combinations (e.g., Spring Boot 3.3.5 + Axon 4.9.4 + Kotlin 2.0.10). Deprecated stacks receive two release cycles of notice before removal.
  * **Licensing Compliance:** CI produces CycloneDX SBOMs; legal reviews new dependencies quarterly. GPL/SSPL packages are blocked without executive exemption.
  * **Fallback Strategies:** Keycloak upgrades execute with blue/green realms; Flowable runs in dual-write mode for one release prior to cutover; Redis deployments leverage Sentinel failover rehearsed monthly.
  * **Emergency Patching:** Critical CVEs trigger the hotfix playbook—branch from production tag, apply patch, run `./gradlew clean build` plus targeted regression suites, deploy via expedited pipeline, then backport to main.

-----

## Test Strategy and Standards (Revision 3)

**Hybrid Testing Strategy with Nullable Pattern Integration**

* **Philosophy:** Constitutional TDD (RED-GREEN-Refactor); Hybrid distribution (40-50% fast logic, 30-40% critical integration, 10-20% E2E); 85%+ Line Coverage; 80%+ Mutation Coverage.
* **Backend Framework:** **Kotest** (JUnit forbidden).
* **Core Strategy:** Integration-first philosophy enhanced with nullable pattern for fast business logic testing (61.6% performance improvement).

**Testing Mandates (Enhanced "No Mocks" rule):**
1. **Stateful Dependencies (DB/Auth):** **Testcontainers ONLY** (Postgres, Keycloak).
2. **Stateless Dependencies (External APIs):** **Nullable Design Pattern** (Stubbed Adapters/Ports). (WireMock is forbidden).
3. **In-Memory DBs (H2):** Explicitly forbidden.
4. **Security Testing:** Security-lite profile for fast JWT tests (65% faster execution).

**Advanced Features:**
- **Nullable Pattern:** Factory-based infrastructure substitutes with contract testing
- **Security-Lite Profile:** Fast JWT/security tests without external dependencies
- **Mutation Testing:** Pitest with 80% minimum coverage
- **Performance Optimization:** Container reuse, parallel startup, baseline measurement
- **Anti-Patterns:** Comprehensive list of prohibited testing approaches

**Quality Gates:** P0 (security) → P1 (core) → P2 (features) with comprehensive coverage requirements.

**Full specification:** See `docs/architecture/test-strategy-and-standards-revision-3.md`.

-----

## Coding Standards (Revision 2)

**Enforcement:** These rules are **automated architectural tests** enforced by **Konsist** and `ktlint`/`Detekt` in the CI build (Story 1.2).

### Core Principles
1. **Ubiquitous Language:** All code must use the precise language of the business domain.
2. **Hexagonal Enforcement:** Code within a `domain` module must **never** depend on code from an `adapter` module.
3. **Immutability:** All Commands, Events, and Queries must be immutable Kotlin `data class` or `data object`.
4. **Static Analysis:** All commits will be required to pass `ktlint` and `detekt` checks.

### Critical Architectural Rules
1. **Functional Error Handling (Arrow):** Domain MUST return `Either<Error, Success>`.
2. **Read Model Querying (jOOQ):** Read projections MUST use jOOQ.
3. **No Generic Exceptions:** Always use specific exception types.
4. **No Wildcard Imports:** Every import must be explicit.
5. **Version Catalog Required:** All dependencies via Gradle Version Catalog.
6. **No Mocks:** Use Testcontainers for stateful deps, Nullable Design Pattern for stateless.
7. **No H2:** PostgreSQL Testcontainers only.
8. **TDD Required:** Constitutional TDD (RED-GREEN-Refactor).

### Advanced Patterns
* **Multi-Tenancy:** 3-Layer enforcement (Filter, Service Validation, RLS) with Micrometer Context Propagation.
* **Security Testing:** Security-lite profile for fast JWT tests with real cryptography.
* **Nullable Design Pattern:** For testing infrastructure with factory pattern (`createNull()`) and contract tests.
* **Spring Modulith:** Kotlin-specific configuration with `@PackageInfo` classes and `@ApplicationModule`.

**Full specification:** See `docs/architecture/coding-standards-revision-2.md`.

-----

## Error Handling Strategy

**The "Arrow-Fold-Throw-ProblemDetails" pattern with comprehensive error catalog.**

1. **Domain (Internal):** Returns `Either.Left(DomainError)`.
2. **Boundary (Controller):** "Folds" the Either, translates the `DomainError` into a specific `HttpException`.
3. **Framework (Advice):** A global `@ControllerAdvice` catches the `HttpException` and formats it as a standard **RFC 7807 ProblemDetail**.
4. **Frontend (Consumer):** The React Data Provider (WebSocket-based) parses the `application/problem+json` response to display errors.

**Error Response Format:** All API errors return RFC 7807 Problem Details with `traceId`, `tenantId`, and structured error catalogs by category (auth, validation, resources, rate limiting, system errors).

**Implementation Features:**
- Comprehensive error catalog with standardized URIs
- Functional error handling with Arrow Either types
- Context-aware error enrichment (tenant, trace, user)
- Frontend integration patterns for error display
- Structured logging and metrics collection

**Full specification:** See `docs/architecture/error-handling-strategy.md`.

-----

## Monitoring and Observability

  * **Stack:** Micrometer + Prometheus (Metrics), Structured JSON/Logback (Logging), OpenTelemetry (Tracing).
  * **Critical Mandate:** All three pillars (Logs, Metrics, Traces) MUST be automatically tagged with the `tenant_id` (retrieved from the `Tenancy Component`) and the `trace_id` (from OTel), leveraging the mandatory Micrometer Context Propagation.

-----

## Checklist Results Report

  * **Status:** **GO**.
  * **Analysis:** The architecture (10/10 PASS) fully implements all PRD constraints and successfully integrates the lessons learned from the prototype. All major risks (Postgres scaling, `ppc64le` testing, Dockets scope, Saga transactions, API incompatibility) have been identified, analyzed, and mitigated with specific, mandatory architectural patterns.
