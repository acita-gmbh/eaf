# Epic 9: Licensing Server (MVP Validation)

**Epic Goal:** This is the capstone epic for the v0.1 MVP. Its purpose is not to build a new core feature, but to **validate the entire framework** by being the first *product* built using only the EAF components from Epics 1-8. This epic directly fulfills all three MVP Success Criteria.

### Story 9.1: Scaffold the 'Licensing-Server' Product Module
* **As a** Developer (simulating a new onboarded user), **I want** to use the new Scaffolding CLI (from Epic 7) to generate the complete module structure for the new "Licensing Server" application, **so that** I can start building business logic in minutes, not months.
* **AC 1:** Execute the `eaf scaffold module licensing-server` command (built in Story 7.2).
* **AC 2:** The new Gradle module is created in the `/products/` directory and successfully passes the CI pipeline (from Story 1.4) on its initial (empty) commit.

### Story 9.2: Scaffold and Implement 'Product' Aggregate
* **As a** Developer, **I want** to use the CLI (Epic 7) to scaffold a 'Product' aggregate (representing products that can be licensed), **so that** I can implement the basic CRUD business logic for managing products.
* **AC 1:** Execute the `eaf scaffold aggregate Product ...` and `eaf scaffold ra-resource Product` commands (built in Stories 7.3, 7.4).
* **AC 2:** The business logic for basic CRUD operations (Create, Update, Read) for Products is implemented using the generated templates.
* **AC 3:** All logic fully adheres to the Constitutional TDD (RED-GREEN-Refactor) mandate, using Kotest and Testcontainers (from Story 1.2).

### Story 9.3: Scaffold and Implement 'License' Aggregate
* **As a** Developer, **I want** to use the CLI (Epic 7) to scaffold the core 'License' aggregate and implement its business logic, **so that** the application can manage the lifecycle of a software license.
* **AC 1:** Execute the `eaf scaffold aggregate License ...` and `eaf scaffold ra-resource License` commands (Stories 7.3, 7.4).
* **AC 2:** Implement the core business logic commands (e.g., `IssueLicenseCommand`, `ActivateLicenseCommand`, `ValidateLicenseCommand`) within the generated Axon aggregate.
* **AC 3:** The 'License' aggregate correctly integrates with AuthN (Epic 3) and Multi-Tenancy (Epic 4) (e.g., a License is issued to a specific Tenant ID).
* **AC 4:** All integration tests (using the Keycloak Testcontainer from 3.1) successfully pass.

### Story 9.4: Validate Admin UI and Observability Integration
* **As an** Operator, **I want** to manage the new Licensing Server via the EAF's React-Admin portal and monitor its health, **so that** I can confirm the framework's default UI and observability hooks are working.
* **AC 1:** The generated React-Admin UI (from Story 7.4) successfully Lists, Creates, and Edits Products and Licenses via the generated APIs.
* **AC 2:** All operations performed in the Licensing Server correctly emit standardized JSON logs (from Story 5.1) tagged with the correct Tenant ID.
* **AC 3:** The `/actuator/prometheus` endpoint (from Story 5.2) correctly exposes distinct metrics (Axon, HTTP, etc.) for the new `licensing-server` module.

### Story 9.5: Pass Formal MVP Validation Criteria
* **As the** Product Manager (John), **I want** to formally validate that the completed Licensing Server (built on the EAF) satisfies our defined MVP Success Criteria, **so that** the v0.1 MVP can be approved.
* **AC 1:** **(Validation of SC #2):** A developer successfully completed Stories 9.1-9.3 within the 3-day time box, validating our <1 month productivity goal.
* **AC 2:** **(Validation of SC #3):** The Security Team formally reviews the completed Licensing Server (built on EAF Epics 1-8) and confirms it meets the ASVS 100% L1 / 50% L2 compliance targets.
* **AC 3:** **(Validation of SC #1):** The Licensing Server application is successfully deployed to a staging/production environment using the EAF's deployment patterns.
