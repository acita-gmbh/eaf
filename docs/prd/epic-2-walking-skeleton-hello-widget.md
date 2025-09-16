# Epic 2: Walking Skeleton (Hello Widget)

**Epic Goal:** This epic is the single most critical technical de-risking milestone in the project. Its goal is to build the first, thinnest possible vertical slice through our entire chosen stack (Hexagonal/CQRS/ES, Axon, and our Postgres "Strategy A" adapter). This proves that the core architectural patterns function correctly end-to-end before any complex business logic is built.

### Story 2.1: Define the 'Widget' Domain Aggregate
* **As a** Core Developer, **I want** to define a simple 'Widget' domain aggregate within its own Spring Modulith module, **so that** we have a testable entity for proving the CQRS/ES command flow.
* **AC 1:** A new module (e.g., `framework.widget`) is created and passes the Spring Modulith boundary checks (enforced by the gates from Epic 1).
* **AC 2:** The `Widget` aggregate is defined using Axon annotations (@Aggregate), adhering to Hexagonal principles.
* **AC 3:** The aggregate logic includes a `CreateWidgetCommand`, an `@CommandHandler`, a `WidgetCreatedEvent`, and an `@EventSourcingHandler`.
* **AC 4:** All logic is validated using "Constitutional TDD" (Kotest), specifically using the "Nullable Design Pattern" for fast domain tests (per NFR8).

### Story 2.2: Implement the Command Side (Event Store Persistence)
* **As a** Core Developer, **I want** the `CreateWidgetCommand` to be handled and the resulting `WidgetCreatedEvent` to be successfully persisted, **so that** I can prove the "write-side" of CQRS and the native Axon-PostgreSQL adapter (Strategy A) are working correctly.
* **AC 1:** The Axon Framework is configured to use the `JpaEventStorageEngine` (or `JdbcEventStorageEngine`) targeting the PostgreSQL instance launched via Docker Compose (from Story 1.3).
* **AC 2:** A basic API endpoint (e.g., `POST /widgets`) exists to dispatch the `CreateWidgetCommand`.
* **AC 3:** An integration test (using Kotest and Testcontainers, per NFR8) successfully dispatches the command.
* **AC 4:** The integration test verifies (by querying the Axon `domain_event_entry` table directly in the Testcontainer) that the `WidgetCreatedEvent` was correctly persisted with the proper aggregate ID and sequence number.

### Story 2.3: Implement the Read Side (Projection)
* **As a** Core Developer, **I want** a "Tracking Event Processor" to read the event stream from the Postgres event store and build a simple "Widget" read model (projection), **so that** the system's current state is available for queries.
* **AC 1:** A new JPA entity (e.g., `WidgetProjection`) and corresponding PostgreSQL table (the "read model") are created, separate from the event store tables.
* **AC 2:** An Axon Event Handler (Projection) is created (using `@EventHandler`) that subscribes to the event stream using a "Tracking Event Processor."
* **AC 3:** When a `WidgetCreatedEvent` is processed, the handler successfully creates and saves a new record in the `widget_projection` table.
* **AC 4:** The processor correctly persists its `TrackingToken` (processing checkpoint) to the database.

### Story 2.4: Implement the Query Side (API)
* **As a** Core Developer, **I want** to query the "Widget" read model via a simple API, **so that** I can retrieve the current state of the system and prove the "read-side" of CQRS is working.
* **AC 1:** A Query (e.g., `FindWidgetQuery`) and a corresponding `@QueryHandler` are implemented.
* **AC 2:** A new API endpoint (e.g., `GET /widgets/{id}`) is created which dispatches the query.
* **AC 3:** The Query Handler interacts *only* with the `widget_projection` table (the read model), never the event store.
* **AC 4:** A final end-to-end integration test (using Kotest/Testcontainers) confirms the full "Walking Skeleton" flow: (1) POST command, (2) (Wait for projection), (3) GET query, (4) Assert the returned data matches the command data.

---
