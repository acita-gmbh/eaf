# Epic 9: Licensing Server (MVP Validation)

**Epic Goal:** This is the capstone epic for the v0.1 MVP. Its purpose is not to build a new core feature, but to **validate the entire framework** by being the first *product* built using only the EAF components from Epics 1-8. This epic directly fulfills all three MVP Success Criteria.

**Note on Story Sequencing (2025-10-16):** Epic 9 has evolved from its original definition to adapt to implementation realities:

1. **Story 9.1 Replacement**: The original Story 9.1 ("Scaffold Licensing-Server Product Module") was replaced with "Implement React-Admin Consumer Application" (from Epic 7.4 validation) because demonstrating the framework requires a working UI first. The original 9.1 is deferred and will be addressed in Story 9.3 as part of Product Aggregate scaffolding.

2. **Story 9.2 Insertion**: Story 9.2 was inserted to address a P0 blocker discovered during Story 9.1 implementation. The widget-demo QueryHandler ExecutionException prevents validation of the React-Admin integration (Story 9.1 Tasks 5-7), which is foundational for demonstrating the framework to new teams.

3. **Story Renumbering**: Original Stories 9.2-9.5 have been renumbered to 9.3-9.6 to accommodate the insertion.

**Rationale**: Epic 9's goal is to validate the framework by building the first product. However, we cannot demonstrate scaffolding capabilities (Stories 9.3-9.4) without first having a working admin UI (Story 9.1) and functional query system (Story 9.2). This sequencing ensures each validation step builds on proven infrastructure.

### Story 9.1: Implement React-Admin Consumer Application
* **As an** Administrator, **I want** a functional React-Admin portal that integrates the framework shell with product UI modules, **so that** I can manage widgets through a modern web interface.
* **Status:** ✅ Done (2025-10-16)
* **Note:** This story validates the micro-frontend architecture from Epic 7.4. Frontend integration is complete, but backend QueryHandler issue discovered (see Story 9.2).

### Story 9.2: Fix widget-demo QueryHandler ExecutionException
* **As a** Developer, **I want** the widget-demo QueryGateway to execute FindWidgetsQuery successfully, **so that** the React-Admin portal can display widget data and complete Story 9.1 validation.
* **Status:** Draft (2025-10-16)
* **Priority:** P0 (blocks Story 9.1 validation and Epic 9 framework demonstration)
* **AC 1:** QueryGateway executes FindWidgetsQuery without ExecutionException
* **AC 2:** WidgetController GET /widgets returns 200 OK with empty list
* **AC 3:** TenantDatabaseSessionInterceptor executes before @Transactional methods
* **AC 4:** Frontend React-Admin portal displays "No Widgets found" (not 500 error)
* **AC 5:** Create widget succeeds and appears in list (end-to-end validation)

### Story 9.3: Scaffold and Implement 'Product' Aggregate
* **As a** Developer, **I want** to use the CLI (Epic 7) to scaffold a 'Product' aggregate (representing products that can be licensed), **so that** I can implement the basic CRUD business logic for managing products.
* **AC 1:** Execute the `eaf scaffold aggregate Product ...` and `eaf scaffold ra-resource Product` commands (built in Stories 7.3, 7.4).
* **AC 2:** The business logic for basic CRUD operations (Create, Update, Read) for Products is implemented using the generated templates.
* **AC 3:** All logic fully adheres to the Constitutional TDD (RED-GREEN-Refactor) mandate, using Kotest and Testcontainers (from Story 1.2).

### Story 9.4: Scaffold and Implement 'License' Aggregate
* **As a** Developer, **I want** to use the CLI (Epic 7) to scaffold the core 'License' aggregate and implement its business logic, **so that** the application can manage the lifecycle of a software license.
* **AC 1:** Execute the `eaf scaffold aggregate License ...` and `eaf scaffold ra-resource License` commands (Stories 7.3, 7.4).
* **AC 2:** Implement the core business logic commands (e.g., `IssueLicenseCommand`, `ActivateLicenseCommand`, `ValidateLicenseCommand`) within the generated Axon aggregate.
* **AC 3:** The 'License' aggregate correctly integrates with AuthN (Epic 3) and Multi-Tenancy (Epic 4) (e.g., a License is issued to a specific Tenant ID).
* **AC 4:** All integration tests (using the Keycloak Testcontainer from 3.1) successfully pass.

### Story 9.5: Validate Admin UI and Observability Integration
* **As an** Operator, **I want** to manage the new Licensing Server via the EAF's React-Admin portal and monitor its health, **so that** I can confirm the framework's default UI and observability hooks are working.
* **AC 1:** The generated React-Admin UI (from Story 7.4) successfully Lists, Creates, and Edits Products and Licenses via the generated APIs.
* **AC 2:** All operations performed in the Licensing Server correctly emit standardized JSON logs (from Story 5.1) tagged with the correct Tenant ID.
* **AC 3:** The `/actuator/prometheus` endpoint (from Story 5.2) correctly exposes distinct metrics (Axon, HTTP, etc.) for the new `licensing-server` module.

### Story 9.6: Pass Formal MVP Validation Criteria
* **As the** Product Manager (John), **I want** to formally validate that the completed Licensing Server (built on the EAF) satisfies our defined MVP Success Criteria, **so that** the v0.1 MVP can be approved.
* **AC 1:** **(Validation of SC #2):** A developer successfully completed Stories 9.2-9.4 within the 3-day time box, validating our <1 month productivity goal.
* **AC 2:** **(Validation of SC #3):** The Security Team formally reviews the completed Licensing Server (built on EAF Epics 1-8) and confirms it meets the ASVS 100% L1 / 50% L2 compliance targets.
* **AC 3:** **(Validation of SC #1):** The Licensing Server application is successfully deployed to a staging/production environment using the EAF's deployment patterns.
