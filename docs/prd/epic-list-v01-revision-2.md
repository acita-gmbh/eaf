# Epic List (v0.1 - Revision 2)

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
8.  **Epic 8: Code Quality & Architectural Alignment**
    * **Goal:** Systematically address critical architectural deviations and technical debt (jOOQ migration, disabled tests, React-Admin consumer app) to ensure framework aligns with specifications before MVP validation.
9.  **Epic 9: Licensing Server (MVP Validation)** (Formerly Epic 8)
    * **Goal:** Build the first complete internal application (the Licensing Server) *using only* the EAF components from Epics 1-8, validating all MVP Success Criteria.

---
