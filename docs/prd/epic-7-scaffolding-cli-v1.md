# Epic 7: Scaffolding CLI (v1)

**Epic Goal:** This epic delivers the core Developer Experience promise of the EAF. It creates the v1 Scaffolding CLI, a tool designed to automate the creation of all boilerplate required by our complex (Hexagonal/CQRS/Modulith/Flowable) stack, enabling a new developer to meet the <1 month productivity goal. (This epic is now correctly sequenced *after* all core patterns are finalized).

### Story 7.1: Initialize CLI Framework Shell
* **As a** Core Developer, **I want** a basic, executable CLI application framework (e.g., using Kotlin with Picocli), **so that** we have a stable platform to build our specific code generators on.
* **AC 1:** A new Gradle sub-project (e.g., `eaf-cli`) is created in the monorepo.
* **AC 2:** The CLI application is executable via Gradle and produces basic output (e.g., `eaf --version`, `eaf scaffold --help`).
* **AC 3:** The CLI is configured with a templating engine (like Mustache or Velocity) to generate code from templates.

### Story 7.2: Create "New Module" Generator
* **As a** Core Developer, **I want** the CLI to have a `scaffold module` command, **so that** I can create a new, empty Spring Modulith-compliant module instantly that adheres to our Foundation setup (Epic 1).
* **AC 1:** Running `eaf scaffold module <name>` creates a new, complete Gradle sub-project (e.g., in the `products/` directory).
* **AC 2:** The new module's `build.gradle.kts` file is automatically configured to use the convention plugins (from Story 1.2).
* **AC 3:** A valid `ModuleMetadata.kt` file (required by Spring Modulith) is generated to define the new module's boundaries and name.

### Story 7.3: Create "New Aggregate" Generator
* **As a** Developer, **I want** the CLI to have a `scaffold aggregate` command, **so that** I can generate a complete CQRS/ES vertical slice (command, event, aggregate, projection, and API stubs) in seconds, adhering to all framework patterns.
* **AC 1:** Running `eaf scaffold aggregate <Name> --module <moduleName>` generates all required Kotlin files for a new Axon Aggregate inside the target module.
* **AC 2:** The generated files MUST include templates/stubs for:
    * The Domain Aggregate (`<Name>Aggregate.kt`) with Axon annotations.
    * The API (Commands, Events, and Queries files for the aggregate).
    * The Projection/EventHandler (`<Name>Projection.kt`).
    * API Endpoints (stubs for the Spring Controllers and Query Handlers).
* **AC 3:** The generated templates must correctly include all required patterns (e.g., Tenancy checks from Epic 4, Logging from Epic 6).
* **AC 4:** The generated code (including generated test stubs) must pass all Constitutional TDD and Quality Gates (from Story 1.2) immediately upon generation.

### Story 7.4: Create "React-Admin" Generator
* **As a** Developer, **I want** the CLI to have a `scaffold ra-resource` command, **so that** it generates the basic React-Admin Typescript resource files needed for the aggregate I just created in the admin UI.
* **AC 1:** Running `eaf scaffold ra-resource <Name>` generates the necessary `.tsx` files (e.g., CreateResource, EditResource, ListResource) within the `apps/admin` codebase.
* **AC 2:** The generated UI resource components correctly reference the API endpoints and data structures (Typescript types) generated in Story 7.3.

---
