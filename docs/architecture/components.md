# Components

(Logical components within the Modular Monolith and Monorepo).

### Component: CQRS/ES Core (Axon Engine) (Revision 2)

  * **Responsibility:** Manages the entire command/event/query lifecycle (PRD Epic 2).
  * **Dependencies:** Depends on all foundational components: `Database Schema`, `Data Models`, `Security`, `Tenancy`, `Observability`, and `Flowable Engine` (bidirectional dependency).
  * **Tech:** Axon Framework 4.9.4.

### Component: BPMN Workflow Engine (Flowable)

  * **Responsibility:** Manages all workflow orchestration (replaces legacy Dockets, fulfills PRD Epic 7).
  * **Dependencies:** Depends on `Database Schema (flowable)` and the `CQRS Core` (to dispatch commands).
  * **Tech:** Flowable 7.x.

### Component: Security Component (AuthN)

  * **Responsibility:** Implements core authentication (PRD Epic 3), the 10-Layer JWT standard, and the Keycloak Testcontainer config.
  * **Dependencies:** Depends on external Keycloak. Provides the `Principal` context to all other components.
  * **Tech:** Spring Security, Keycloak 26.0.0.

### Component: Tenancy Component (Isolation)

  * **Responsibility:** Implements the 3-Layer tenancy model (Filter, Service Validation, RLS) and the mandatory async context propagation (PRD Epic 4), leveraging Micrometer Context Propagation.
  * **Dependencies:** Depends on `Security Component`. Provides the `TenantContext` to all components (CQRS, Flowable, Obs).
  * **Tech:** Spring Filters, Micrometer Context Propagation.

### Component: Observability Component

  * **Responsibility:** Implements the "three pillars" collection (Logging, Metrics, Tracing) (PRD Epic 6).
  * **Dependencies:** Depends on `Tenancy Component` (for `tenant_id` tagging) and `Security Component` (to secure endpoints).
  * **Tech:** Micrometer/Prometheus, OTel, SLF4J/Logback (JSON).

### Component: Scaffolding CLI (Application)

  * **Responsibility:** Standalone developer tool (PRD Epic 7) that automates code generation (Aggregates, UI resources) based on the finalized patterns from all framework components.
  * **Dependencies:** Consumes the patterns defined by all other components.
  * **Tech:** Kotlin, Picocli, Templating engine.

### Component: React-Admin Portal (Application)

  * **Responsibility:** The internal Operator UI (living in `apps/admin/`). Primary consumer of the API Spec.
  * **Dependencies:** Must adhere to the UI/UX Spec. Codebase is modified by the `Scaffolding CLI`.
  * **Tech:** React 18, React-Admin, MUI, Vite.

### Component: Licensing Server Module (Application)

  * **Responsibility:** The first "Product Application" built *on* the EAF (PRD Epic 8). The MVP Validation capstone.
  * **Dependencies:** Consumes ALL foundational components (CQRS, Security, Tenancy, Obs).
  * **Tech:** Inherits the full EAF stack.

-----
