# Hexagonal Architecture in DVMM

**Keeping our business logic clean, testable, and independent.**

Hexagonal Architecture (also known as Ports and Adapters) is a design pattern that protects our core business logic from external technologies. It ensures that our application doesn't care whether it's running on a web server or a CLI, using PostgreSQL or MySQL, or sending emails via SMTP or SendGrid.

In DVMM, this separation is strictly enforced by our build system and architecture tests.

---

## The Layers

Imagine the application as an onion. Dependencies can only point **inward**. The core knows nothing about the outer layers.

```mermaid
graph TD
    subgraph Infrastructure[Infrastructure Layer]
        DB[PostgreSQL Adapter]
        Web[REST Controllers]
        Auth[Keycloak Adapter]
    end

    subgraph Application[Application Layer]
        UseCases[Command Handlers]
        Ports[Ports (Interfaces)]
    end

    subgraph Domain[Domain Layer]
        Model[Aggregates]
        Rules[Business Logic]
    end

    Infrastructure --> Application
    Application --> Domain
```

### 1. Domain Layer (`dvmm-domain`)
*   **Role:** The "Brain". Contains purely business rules.
*   **Dependencies:** None. Zero. (No Spring, No SQL, No HTTP).
*   **Contents:**
    *   **Aggregates:** (`VmRequestAggregate`) consistency boundaries.
    *   **Value Objects:** (`VmId`, `VmSize`) type-safe data structures.
    *   **Domain Events:** (`VmRequestCreated`) facts of what happened.
    *   **Business Rules:** Validations and state transitions.

### 2. Application Layer (`dvmm-application`)
*   **Role:** The "Coordinator". Orchestrates the domain objects to fulfill use cases.
*   **Dependencies:** Depends on `dvmm-domain`.
*   **Contents:**
    *   **Command Handlers:** Receive a command, load an aggregate, invoke a domain method, and save events.
    *   **Ports (Interfaces):** Defines *contracts* for external services (e.g., `NotificationPort`, `HypervisorPort`) without implementing them.
    *   **Queries:** Definitions of read operations.

### 3. API Layer (`dvmm-api`)
*   **Role:** The "Entry Point". Exposes the application to the outside world.
*   **Dependencies:** Depends on `dvmm-application`.
*   **Contents:**
    *   **REST Controllers:** Handle HTTP requests and responses.
    *   **DTOs:** Data Transfer Objects for API contracts.
    *   **OpenAPI Specs:** Documentation of our API.

### 4. Infrastructure Layer (`dvmm-infrastructure`)
*   **Role:** The "Plumbing". Implements the interfaces defined in the Application layer.
*   **Dependencies:** Depends on `dvmm-application` (to implement ports) and external libraries.
*   **Contents:**
    *   **Persistence:** PostgreSQL implementations of `EventStore`.
    *   **Adapters:** `SmtpNotificationSender`, `VsphereAdapter`.
    *   **Configuration:** Database configs, Bean definitions.

---

## The Dependency Rule

The most critical rule is: **Source code dependencies can only point inward.**

*   `dvmm-domain` knows **nothing** about `dvmm-application`.
*   `dvmm-application` knows **nothing** about `dvmm-infrastructure`.
*   `dvmm-infrastructure` implements interfaces defined in `dvmm-application`.

This is achieved via **Dependency Inversion**:

1.  **Application** defines an interface: `interface NotificationPort { fun send(email: String) }`
2.  **Infrastructure** implements it: `class SmtpNotificationAdapter : NotificationPort { ... }`
3.  **Spring Boot** injects the implementation into the application at runtime.

## How It Works in Practice

### Scenario: Creating a VM

1.  **API Layer:** `VmRequestController` receives a JSON HTTP request. It converts it to a `CreateVmRequestCommand` and passes it to the...
2.  **Application Layer:** `CreateVmRequestHandler` receives the command. It uses the...
3.  **Domain Layer:** `VmRequestAggregate` to validate the logic and create the `VmRequestCreated` event.
4.  **Application Layer:** The handler then asks the `EventStore` (an interface) to save the event.
5.  **Infrastructure Layer:** The `PostgresEventStore` implementation actually writes the JSON to the `eaf_events` table.

## Why We Do This

1.  **Testability:** We can unit test the entire Core (Domain + Application) without spinning up a database or web server. We just mock the ports.
2.  **Maintainability:** Frameworks change. Databases change. Business logic persists. If we switch from PostgreSQL to MongoDB, we only rewrite the Infrastructure layer. The Domain remains untouched.
3.  **Clarity:** It's obvious where code belongs. Business rule? Domain. SQL query? Infrastructure. HTTP endpoint? API.

## Quality Gates

We don't just hope developers follow these rules—we enforce them.

*   **Konsist Tests:** Our build pipeline runs architecture tests that check import statements.
*   **Compilation:** The Gradle module structure physically prevents `dvmm-domain` from importing `spring-boot`.

If you try to import a Controller into an Aggregate, the build will fail.
