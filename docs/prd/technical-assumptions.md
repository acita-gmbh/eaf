# Technical Assumptions

### Repository Structure: Monorepo
* (Revision 2) This is a **Gradle Multi-Module Monorepo**. This structure was validated by the prototype and is required to manage the framework libraries, product apps (like React-Admin), and shared code (like testing patterns) efficiently.

### Service Architecture
* (Revision 2) This is a critical decision. The v0.1 EAF architecture is defined by the successful prototype. The architecture **must** implement **Hexagonal Architecture** (with boundaries programmatically enforced by **Spring Modulith 1.4.3**), combined with **Domain-Driven Design (DDD)** and **CQRS/Event Sourcing (CQRS/ES)** patterns using the **Axon Framework 4.9.4**.

### Testing Requirements (Revision 2, incorporating Test Philosophy)
* The testing requirement is a **Constitutional Test-Driven Development (TDD)** process, defined as "Test-First is Law". All development must follow the mandatory **RED-GREEN-Refactor cycle**.
* This mandates an **Integration-First** approach, inverting the test pyramid to a target ratio of **40-50% Integration Tests**.
* Per this philosophy, testing mandates are:
    1.  Real dependencies (specifically **PostgreSQL via Testcontainers**) are mandatory for integration tests.
    2.  The use of in-memory databases (like H2) for integration tests is **explicitly forbidden**.
    3.  The **"Nullable Design Pattern"** (testing real business logic against stubbed infrastructure) is the preferred pattern for fast domain tests.
    4.  The test framework must be **Kotest** (replacing JUnit).

### Additional Technical Assumptions and Requests
* **Stack Current:** The stack uses current stable versions: **Kotlin 2.2.20** and **Spring Boot 3.5.6**.
* **Persistence Strategy:** Utilize **PostgreSQL (16.1+)**... implemented as a **swappable adapter**.
* **CPU Target:** Support for the **`ppc64le`** processor architecture is mandatory.
* **Security:** Authentication must integrate with **Keycloak OIDC**.
* **Workflow Engine:** Workflows (replacing legacy Dockets) will be managed by the **Flowable BPMN Engine**.

---
