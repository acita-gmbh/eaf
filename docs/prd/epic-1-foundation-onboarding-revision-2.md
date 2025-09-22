# Epic 1: Foundation & Onboarding (Revision 2)

**Epic Goal:** This epic lays the complete foundational infrastructure for the entire EAF project. It delivers the Gradle monorepo structure, the automated "Constitutional" quality gates (ktlint, Detekt, TDD enforcement), and the "One-Command" local developer environment (Docker Compose stack) needed to support all subsequent development and meet our <1 month productivity goal.

### Story 1.1: Initialize Gradle Monorepo Structure
* **As a** Core Developer, **I want** a defined Gradle Multi-Module Monorepo structure configured with version catalogs, **so that** all framework libraries, product apps (like React-Admin), and shared code are managed consistently in one place.
* **AC 1:** The project root is initialized with a Gradle wrapper and a **`build-logic` directory** for convention plugins.
* **AC 2:** The monorepo includes the required directory structure (`framework/`, `products/`, `shared/`, `apps/admin/`) defined in the prototype architecture.
* **AC 3:** A central Gradle version catalog (`libs.versions.toml`) is created and populated with the current stable stack versions (Kotlin 2.2.20, Spring Boot 3.5.6, Axon 4.9.4, Kotest).
* **AC 4:** **All shared build logic (including dependency versions and plugin configurations) MUST be defined via convention plugins within `build-logic` to ensure consistency**.

### Story 1.2: Implement Constitutional Quality Gates
* **As a** Core Developer, **I want** automated quality gates for static analysis and testing integrated into the Gradle build, **so that** all code (human or AI-generated) strictly adheres to our mandatory standards.
* **AC 1:** The Gradle build integrates `ktlint` (1.4.0) and `Detekt` (1.23.7) with zero-tolerance failure policies; **this configuration MUST be applied to modules via the convention plugins** defined in Story 1.1.
* **AC 2:** The build process enforces the "Constitutional TDD" (RED-GREEN-Refactor) philosophy.
* **AC 3:** The build is configured to run Kotest and Testcontainers for the `integrationTest` source set; **this configuration MUST also be applied via the convention plugins**.

### Story 1.3: Create One-Command Dev Stack (Docker Compose)
* **As a** New Developer, **I want** a single command (e.g., `./scripts/init-dev.sh`) to launch the complete local development stack, **so that** I can be productive on day one, meeting the <1 month productivity goal.
* **AC 1:** A root `compose.yml` file (or set of files) is created.
* **AC 2:** Running the initialization script successfully starts all required backing services: PostgreSQL 16.1+ and Keycloak 26.0.0. (Note: Flowable (Epic 6) will use the same Postgres instance).
* **AC 3:** All services are configured with default users, passwords, ports, and data volumes required for local development.

### Story 1.4: Establish Foundational CI Pipeline
* **As a** Core Developer, **I want** a foundational CI (Continuous Integration) pipeline (e.g., GitHub Actions workflow), **so that** all pull requests are automatically validated against our mandatory quality gates.
* **AC 1:** The CI pipeline triggers automatically on all pull requests targeting the main branch.
* **AC 2:** The CI pipeline executes all required build steps: `compile`, `check` (ktlint/Detekt), and `test` (Kotest unit tests).
* **AC 3:** The CI pipeline executes the full `integrationTest` suite (using Testcontainers).
* **AC 4:** A pull request is algorithmically blocked from merging if any quality gate (linting, Detekt, or any test) fails.

---
