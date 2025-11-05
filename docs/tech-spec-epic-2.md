# Epic Technical Specification: Walking Skeleton - CQRS/Event Sourcing Core

Date: 2025-11-04
Author: Wall-E
Epic ID: 2
Status: Draft

---

## Overview

Epic 2 implements the first complete CQRS/Event Sourcing vertical slice that proves the EAF architecture viability by delivering an end-to-end flow from REST API → Command → Event → Projection → Query. This Walking Skeleton validates that Axon Framework 4.12.1 integrates correctly with PostgreSQL 16.10 event store, jOOQ 3.20.8 type-safe projections, and Spring Boot 3.5.7 REST APIs, while meeting performance targets (API p95 <200ms, event lag <10s). The epic establishes the reference pattern for all future CQRS/ES development across Epics 3-10, providing a proven implementation template that eliminates architectural uncertainty and enables confident domain modeling in subsequent epics.

Building on Epic 1's foundation (Gradle monorepo, Spring Modulith, DDD base classes, CI/CD pipelines), Epic 2 introduces event store optimizations (monthly partitioning, BRIN indexes, snapshot strategy), establishes Testcontainers-based integration testing patterns, implements OpenAPI documentation with Swagger UI, and validates the complete technology stack under load. Success criteria include a working Widget aggregate demonstrating Create/Update/Publish commands with full projection support, comprehensive end-to-end integration tests, and performance baselines documented for regression detection.

---

## Objectives and Scope

**In Scope:**

- **Axon Framework Integration** (Story 2.1):
  - CommandGateway and QueryGateway configuration
  - CommandBus, EventBus, QueryBus setup
  - Axon auto-configuration with Spring Boot
  - Framework/cqrs module with core CQRS infrastructure

- **PostgreSQL Event Store** (Stories 2.2-2.4):
  - JdbcEventStorageEngine configuration
  - Flyway migrations for Axon tables (domain_event_entry, snapshot_entry, saga tables)
  - Monthly time-based partitioning on domain_event_entry
  - BRIN indexes on timestamp and aggregate_identifier
  - Snapshot strategy (every 100 events) with Jackson serialization
  - Performance optimization for 100K+ events

- **Widget Demo Aggregate** (Story 2.5):
  - Widget.kt aggregate with WidgetId value object
  - Commands: CreateWidgetCommand, UpdateWidgetCommand, PublishWidgetCommand
  - Events: WidgetCreatedEvent, WidgetUpdatedEvent, WidgetPublishedEvent
  - Command handlers with business logic validation
  - Event sourcing handlers for state reconstruction
  - Axon Test Fixtures for command scenarios

- **jOOQ Projection Infrastructure** (Stories 2.6-2.8):
  - jOOQ 3.20.8 configuration with DSLContext bean
  - Gradle code generation from database schema
  - Flyway migration for widget_view projection table
  - WidgetProjectionEventHandler with @EventHandler methods
  - TrackingEventProcessor configuration (<10s lag target)
  - Widget query handlers (FindWidgetQuery, ListWidgetsQuery)
  - Cursor-based pagination (no offset-limit)

- **REST API Foundation** (Stories 2.9-2.10):
  - Framework/web module with Spring Web MVC
  - RFC 7807 ProblemDetail error handling
  - WidgetController with CRUD endpoints (POST, GET, PUT)
  - Request/Response DTOs with validation
  - OpenAPI 3.0 annotations

- **End-to-End Validation** (Stories 2.11-2.13):
  - Comprehensive integration test (REST → Command → Event → Projection → Query)
  - Testcontainers PostgreSQL for real database
  - OpenAPI documentation with Swagger UI
  - Performance baseline with JMeter/Gatling
  - Performance regression testing in nightly CI/CD

**Out of Scope:**

- Multi-tenancy (Epic 4)
- Authentication/Authorization (Epic 3)
- Observability beyond basic metrics (Epic 5)
- Workflow orchestration (Epic 6)
- Scaffolding CLI (Epic 7)
- Production-grade Widget features (Epic 10)
- Keycloak integration (Epic 3)
- Advanced error recovery (Epic 6)

---

## System Architecture Alignment

Epic 2 implements the core CQRS/Event Sourcing stack defined in architecture.md Sections 6-9:

**CQRS/Event Sourcing Stack (architecture.md Section 6):**
- Axon Framework 4.12.1 (production stable, maintained until v5 migration Q3-Q4 2026)
- Command/Event/Query separation with dedicated buses
- Event Store as source of truth (PostgreSQL-based)
- Projections for optimized read models (jOOQ)

**Event Store Optimizations (architecture.md Section 7):**
- PostgreSQL 16.10 with mandatory optimizations
- Monthly time-based partitioning on domain_event_entry
- BRIN indexes for time-range queries (timestamp, aggregate_identifier)
- Snapshot strategy (every 100 events, configurable)
- HikariCP connection pooling with optimal settings

**Performance Targets (architecture.md Section 10):**
- API p95 latency: <200ms
- Event processing lag: <10s (TrackingEventProcessor)
- Query response time: <50ms (single), <200ms (paginated list)
- Snapshot performance improvement: >10x faster for 1000+ events

**Testing Strategy (architecture.md Section 4.2):**
- Kotest 6.0.4 with Axon Test Fixtures
- Testcontainers PostgreSQL (H2 explicitly forbidden)
- Integration-first testing approach
- Nullable Design Pattern for query handler unit tests
- Property-based tests for event invariants (nightly)

**Constraints:**
- Zero-Tolerance Policies: No wildcard imports, no generic exceptions, Kotest-only, Version Catalog required
- Constitutional TDD: Tests first (Red-Green-Refactor)
- Real dependencies: Testcontainers over mocks for stateful services
- @SpringBootTest Pattern: @Autowired field injection + init block (NOT constructor injection)

---

## Detailed Design

### Services and Modules

| Module | Responsibility | Key Components | Dependencies |
|--------|---------------|----------------|--------------|
| **framework/cqrs** | Axon Framework CQRS infrastructure | AxonConfiguration.kt, CommandGateway, QueryGateway, CommandBus, EventBus, QueryBus | framework/core, axon-spring-boot-starter |
| **framework/persistence** | Event Store + Projections | PostgresEventStoreConfiguration.kt, JooqConfiguration.kt, Flyway migrations, JdbcEventStorageEngine, DSLContext | framework/core, axon-jdbc, jooq, flyway, postgresql |
| **framework/web** | REST API foundation | ProblemDetailExceptionHandler.kt, RestConfiguration.kt, CursorPaginationSupport.kt | framework/core, spring-boot-starter-web |
| **products/widget-demo** | Reference CQRS aggregate | Widget.kt aggregate, Commands (Create/Update/Publish), Events, CommandHandlers, EventSourcingHandlers, WidgetProjectionEventHandler.kt, WidgetQueryHandler.kt, WidgetController.kt | framework/core, framework/cqrs, framework/persistence, framework/web |

**Module Dependencies:**
- framework/cqrs → framework/core
- framework/persistence → framework/core, framework/cqrs
- framework/web → framework/core
- products/widget-demo → framework/core, framework/cqrs, framework/persistence, framework/web

**Konsist Validation:** All dependencies verified against Spring Modulith boundary rules.

---

### Data Models and Contracts

**Aggregate Root - Widget:**

```kotlin
// products/widget-demo/src/main/kotlin/com/axians/eaf/products/widgetdemo/domain/Widget.kt
@Aggregate
class Widget(
    @AggregateIdentifier
    val id: WidgetId
) : AggregateRoot<WidgetId>(id) {
    private var name: String = ""
    private var status: WidgetStatus = WidgetStatus.DRAFT
    private var version: Int = 0

    @CommandHandler
    constructor(command: CreateWidgetCommand) {
        // Validation logic
        apply(WidgetCreatedEvent(
            widgetId = command.widgetId,
            name = command.name
        ))
    }

    @EventSourcingHandler
    fun on(event: WidgetCreatedEvent) {
        // State reconstruction
        this.name = event.name
        this.status = WidgetStatus.DRAFT
    }

    // Additional handlers for Update and Publish...
}
```

**Value Objects:**

```kotlin
data class WidgetId(override val value: String) : Identifier(value)

enum class WidgetStatus {
    DRAFT, PUBLISHED, ARCHIVED
}
```

**Commands:**

```kotlin
// shared/shared-api/src/main/kotlin/com/axians/eaf/shared/api/widget/commands/

data class CreateWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId,
    val name: String
)

data class UpdateWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId,
    val name: String
)

data class PublishWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId
)
```

**Events:**

```kotlin
// shared/shared-api/src/main/kotlin/com/axians/eaf/shared/api/widget/events/

data class WidgetCreatedEvent(
    val widgetId: WidgetId,
    val name: String
) : DomainEvent()

data class WidgetUpdatedEvent(
    val widgetId: WidgetId,
    val name: String
) : DomainEvent()

data class WidgetPublishedEvent(
    val widgetId: WidgetId
) : DomainEvent()
```

**Projection Table Schema:**

```sql
-- Flyway migration: V100__widget_projections.sql

CREATE TABLE widget_view (
    widget_id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    status VARCHAR(50) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_widget_status ON widget_view(status);
CREATE INDEX idx_widget_created ON widget_view(created_at);
```

**Event Store Schema (Axon Standard):**

```sql
-- Flyway migration: V001__event_store_schema.sql
-- Uses Axon Framework standard schema

CREATE TABLE domain_event_entry (
    global_index BIGSERIAL PRIMARY KEY,
    event_identifier VARCHAR(255) NOT NULL UNIQUE,
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255),
    timestamp VARCHAR(255) NOT NULL,
    payload_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    meta_data TEXT,
    UNIQUE (aggregate_identifier, sequence_number)
);

-- Partitioning applied in V002__partitioning_setup.sql
-- BRIN indexes applied in V003__brin_indexes.sql
```

---

### APIs and Interfaces

**Widget REST API:**

| Endpoint | Method | Request | Response | Status Codes |
|----------|--------|---------|----------|--------------|
| `/api/widgets` | POST | CreateWidgetRequest | WidgetResponse | 201 Created, 400 Bad Request |
| `/api/widgets/{id}` | GET | - | WidgetResponse | 200 OK, 404 Not Found |
| `/api/widgets` | GET | ?cursor, ?limit | WidgetListResponse | 200 OK |
| `/api/widgets/{id}` | PUT | UpdateWidgetRequest | WidgetResponse | 200 OK, 404 Not Found |

**Request DTOs:**

```kotlin
data class CreateWidgetRequest(
    @field:NotBlank
    val name: String
)

data class UpdateWidgetRequest(
    @field:NotBlank
    val name: String
)
```

**Response DTOs:**

```kotlin
data class WidgetResponse(
    val id: String,
    val name: String,
    val status: String,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class WidgetListResponse(
    val items: List<WidgetResponse>,
    val cursor: String?,
    val hasMore: Boolean
)
```

**Axon Gateways:**

```kotlin
// CommandGateway usage
val widgetId = commandGateway.sendAndWait<WidgetId>(
    CreateWidgetCommand(
        widgetId = WidgetId.generate(),
        name = request.name
    )
)

// QueryGateway usage
val widget = queryGateway.query(
    FindWidgetQuery(widgetId),
    ResponseTypes.instanceOf(WidgetResponse::class.java)
).join()
```

---

### Workflows and Sequencing

**CQRS Write Flow (Command → Event → Projection):**

```
1. Client → POST /api/widgets → WidgetController
2. Controller → CommandGateway.sendAndWait(CreateWidgetCommand)
3. CommandBus → Widget @CommandHandler
4. CommandHandler → Validation → apply(WidgetCreatedEvent)
5. EventBus → Persist to domain_event_entry (PostgreSQL)
6. EventBus → Publish to TrackingEventProcessor
7. WidgetProjectionEventHandler @EventHandler → jOOQ insert into widget_view
8. Controller ← HTTP 201 Created with WidgetResponse
```

**CQRS Read Flow (Query → Projection):**

```
1. Client → GET /api/widgets/{id} → WidgetController
2. Controller → QueryGateway.query(FindWidgetQuery)
3. QueryBus → WidgetQueryHandler @QueryHandler
4. QueryHandler → jOOQ SELECT from widget_view
5. QueryHandler → Map to WidgetResponse
6. Controller ← HTTP 200 OK with WidgetResponse
```

**Performance Critical Paths:**

- **Command Processing:** Widget creation <50ms (in-memory command handling)
- **Event Persistence:** Event store write <20ms (PostgreSQL with partitioning)
- **Projection Update:** Event handler execution <100ms (jOOQ insert/update)
- **Query Execution:** Projection query <50ms (jOOQ with indexes)
- **End-to-End:** POST /widgets → GET /widgets/{id} <200ms p95

---

## Non-Functional Requirements

### Performance

**Target Metrics (NFR001):**

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| API Response Time (p95) | <200ms | JMeter load test (Story 2.13) |
| Event Processing Lag | <10s | Prometheus metrics tracking (Story 2.7) |
| Query Response (single) | <50ms | Integration test assertions (Story 2.8) |
| Query Response (list) | <200ms | Integration test with pagination (Story 2.8) |
| Snapshot Performance Gain | >10x faster | Benchmark test with 1000+ events (Story 2.4) |

**Load Test Scenarios (Story 2.13):**
- 100 concurrent users
- 1000 requests/second sustained
- 10K+ widgets in projection
- Performance regression tests in nightly CI/CD

**PostgreSQL Optimizations:**
- Monthly partitioning reduces query time from O(n) to O(partition_size)
- BRIN indexes optimized for time-range queries (timestamp-based event retrieval)
- Connection pooling (HikariCP) with optimal settings (minimize latency)

### Security

**Current Scope (Epic 2):**
- No authentication/authorization (Epic 3 scope)
- API endpoints publicly accessible (temporary)
- Input validation on all command fields (@NotBlank, @Size annotations)
- SQL injection prevention via jOOQ parameterized queries
- No PII in Widget aggregate (safe for development)

**Security Notes:**
- Epic 3 will add 10-layer JWT validation
- Epic 4 will add tenant isolation
- For Epic 2: Focus on preventing injection attacks, proper error handling

**OWASP Dependency Scanning:**
- Runs in security-review.yml on all dependency changes
- Critical vulnerabilities (CVSS ≥8.0) block builds
- Weekly scheduled scans

### Reliability/Availability

**Event Store Durability:**
- PostgreSQL with synchronous_commit = on (default)
- WAL (Write-Ahead Logging) ensures durability
- Backup strategy: PostgreSQL automated backups (Epic 10 deployment scope)

**Projection Consistency:**
- Eventual consistency model (<10s lag target)
- TrackingEventProcessor with auto-retry on failures
- Error handling logs failures for manual investigation
- Dead letter queue pattern (Epic 6 - Workflow Orchestration)

**Failure Modes:**
- Command validation failure → HTTP 400 Bad Request (no event persisted)
- Event store write failure → Axon retries, then fails command
- Projection failure → Logged, metrics emitted, projection remains stale until retry

**Health Checks:**
- /actuator/health endpoint (Spring Boot Actuator)
- PostgreSQL connection validation
- Axon configuration status

### Observability

**Logging:**
- Structured JSON logging foundation (Epic 5)
- Command execution logging (command type, aggregate ID)
- Event publication logging (event type, timestamp)
- Query execution logging (query type, execution time)

**Metrics (Prometheus/Micrometer):**
- Command processing duration (per command type)
- Event processing lag (per aggregate type)
- Query execution time (per query type)
- HTTP endpoint metrics (request duration, status codes)
- Widget-specific metrics: widget_created_total, widget_updated_total, widget_published_total

**Tracing:**
- Basic span creation (Epic 5 - OpenTelemetry integration)
- For Epic 2: Manual correlation via log context

**Performance Overhead Target:** <1% (validated in Story 2.13)

---

## Dependencies and Integrations

**Core Dependencies (From gradle/libs.versions.toml):**

```toml
[versions]
axon = "4.12.1"
jooq = "3.20.8"
flyway = "10.23.3"
postgresql = "42.7.8"
jackson = "2.18.2"
springdoc-openapi = "2.6.0"

[libraries]
# Axon Framework
axon-spring-boot-starter = { module = "org.axonframework:axon-spring-boot-starter", version.ref = "axon" }
axon-test = { module = "org.axonframework:axon-test", version.ref = "axon" }

# jOOQ
jooq = { module = "org.jooq:jooq", version.ref = "jooq" }
jooq-codegen = { module = "org.jooq:jooq-codegen", version.ref = "jooq" }

# Database
postgresql = { module = "org.postgresql:postgresql", version.ref = "postgresql" }
flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
flyway-database-postgresql = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }

# Testing
testcontainers-postgresql = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }

# API Documentation
springdoc-openapi-starter-webmvc-ui = { module = "org.springdoc:springdoc-openapi-starter-webmvc-ui", version.ref = "springdoc-openapi" }
```

**External Integrations:**

- **PostgreSQL 16.10:** Event Store + Projections (via Docker Compose from Story 1.5)
- **Prometheus:** Metrics collection (via Docker Compose from Story 1.5)
- **Testcontainers:** PostgreSQL container for integration tests (Story 2.2+)

**Build Tool Integration:**

- **jOOQ Code Generation:** Gradle plugin generates type-safe classes from DB schema
  - Input: Flyway migration V100__widget_projections.sql
  - Output: Generated classes in `build/generated-src/jooq/`
  - Trigger: ./gradlew generateJooq

- **Flyway Migrations:** Automatic execution on application startup
  - Location: src/main/resources/db/migration/
  - Naming: V{version}__{description}.sql
  - Validation: Checksum verification prevents tampering

---

## Acceptance Criteria (Authoritative)

**Epic 2 Acceptance Criteria (Derived from 13 Stories):**

### Story-Level ACs (87 total across 13 stories):

**Story 2.1: Axon Core** (7 ACs)
1. framework/cqrs module created with Axon Framework 4.12.1
2. CommandGateway and QueryGateway beans configured
3. CommandBus, EventBus, QueryBus configured
4. Axon auto-configuration enabled
5. Unit tests verify gateways injectable
6. Tests pass in <10s
7. Module documented

**Story 2.2: Event Store** (8 ACs)
1. framework/persistence module with Axon JDBC + Flyway
2. JdbcEventStorageEngine configured
3. Flyway migration creates Axon tables
4. DataSource configuration for PostgreSQL
5. Integration test verifies event storage/retrieval
6. Testcontainers PostgreSQL for tests
7. Migration executes on Docker Compose PostgreSQL
8. Event store tables visible in database

**Story 2.3: Partitioning** (7 ACs)
1. Monthly partitioning migration
2. BRIN indexes migration
3. Partition creation script
4. Performance test with 100K+ events
5. Partitioning documented
6. Integration test validates partitioning
7. Query performance <200ms

**Story 2.4: Snapshots** (7 ACs)
1. SnapshotTriggerDefinition (every 100 events)
2. Jackson serialization configured
3. snapshot_entry schema validated
4. Integration test with 250+ events
5. Snapshot usage validated
6. Performance >10x improvement measured
7. Snapshot management documented

**Story 2.5: Widget Aggregate** (9 ACs)
1. products/widget-demo module created
2. Widget.kt aggregate with WidgetId
3. Commands: Create, Update, Publish
4. Events: Created, Updated, Published
5. Command handlers with validation
6. Event sourcing handlers
7. Axon Test Fixtures tests
8. Tests pass in <10s
9. Aggregate documented

**Story 2.6: jOOQ Config** (8 ACs)
1. jOOQ 3.20.8 dependency
2. DSLContext bean configured
3. Code generation configured
4. Migration creates widget_view
5. Generated classes available
6. Type-safe query example
7. Integration test validates queries
8. generateJooq task works

**Story 2.7: Projection Handler** (7 ACs)
1. WidgetProjectionEventHandler created
2. Event handlers for all Widget events
3. jOOQ for insert/update
4. TrackingEventProcessor configured (<10s lag)
5. Integration test: command → projection
6. Projection lag meets target
7. Error handling implemented

**Story 2.8: Query Handler** (7 ACs)
1. FindWidgetQuery and ListWidgetsQuery
2. WidgetQueryHandler with @QueryHandler
3. jOOQ type-safe queries
4. Cursor pagination
5. Unit tests with Nullable Pattern
6. Integration test validates query
7. Performance <50ms / <200ms

**Story 2.9: REST Foundation** (7 ACs)
1. framework/web module
2. ProblemDetailExceptionHandler (RFC 7807)
3. Error responses with trace/tenant IDs
4. RestConfiguration
5. CursorPaginationSupport utility
6. Integration test validates errors
7. Exceptions mapped to HTTP codes

**Story 2.10: Widget API** (8 ACs)
1. WidgetController with @RestController
2. Endpoints: POST, GET, GET list, PUT
3. CommandGateway/QueryGateway usage
4. Request/Response DTOs with validation
5. OpenAPI annotations
6. Integration test validates CRUD
7. Swagger UI accessible
8. Correct HTTP status codes

**Story 2.11: E2E Test** (8 ACs)
1. WalkingSkeletonIntegrationTest.kt
2. Test scenario: POST → Command → Event → Projection → GET
3. Validates all CQRS components
4. Measures performance
5. Real PostgreSQL (Testcontainers)
6. No flakiness
7. <2 minutes execution
8. Documented as reference

**Story 2.12: OpenAPI** (8 ACs)
1. Springdoc OpenAPI 2.6.0
2. OpenApiConfiguration with metadata
3. Bearer JWT security scheme (placeholder)
4. Swagger UI at /swagger-ui.html
5. Widget API documented
6. "Try it out" functional
7. JSON spec at /v3/api-docs
8. Examples and descriptions

**Story 2.13: Performance** (7 ACs)
1. Performance test suite (JMeter/Gatling)
2. Load scenarios (100 users, 1000 req/s)
3. Baseline measurements documented
4. Prometheus metrics for endpoints
5. Targets met (<200ms, <10s lag)
6. Regression test in nightly
7. Baseline documented

---

## Traceability Mapping

| AC Group | PRD Requirement | Architecture Section | Components | Test Strategy |
|----------|----------------|---------------------|------------|---------------|
| Axon Core (2.1) | FR003 (Event Store) | Section 6 (CQRS/ES Stack) | framework/cqrs: AxonConfiguration | Unit tests: Gateway injection |
| Event Store (2.2) | FR003 (Event Store) | Section 7 (PostgreSQL Optimizations) | framework/persistence: JdbcEventStorageEngine, Flyway | Integration test: Event persistence |
| Partitioning (2.3) | FR003 (Performance), FR011 (Fast Feedback) | Section 7.2 (Partitioning) | Flyway: V002/V003 migrations | Performance test: 100K events |
| Snapshots (2.4) | FR003 (Performance), FR011 (Fast Feedback) | Section 7.3 (Snapshot Strategy) | Axon: SnapshotTriggerDefinition | Benchmark: 1000+ events |
| Widget Aggregate (2.5) | FR002 (Code Generation - pattern) | Section 6.1 (Aggregate Pattern) | Widget.kt, Commands, Events | Axon Test Fixtures |
| jOOQ Config (2.6) | FR003 (jOOQ Projections) | Section 8 (jOOQ Integration) | framework/persistence: JooqConfiguration | Integration test: Query execution |
| Projection (2.7) | FR003 (Projections), FR014 (Consistency) | Section 8.2 (Projection Pattern) | WidgetProjectionEventHandler | Integration test: Projection lag |
| Query Handler (2.8) | FR003 (Projections) | Section 8.3 (Query Pattern) | WidgetQueryHandler | Unit + Integration tests |
| REST Foundation (2.9) | FR011 (Fast Feedback - API) | Section 9 (REST API) | framework/web: ProblemDetailExceptionHandler | Integration test: Error format |
| Widget API (2.10) | FR002 (Scaffolding pattern) | Section 9.2 (Controller Pattern) | WidgetController | Integration test: CRUD flow |
| E2E Test (2.11) | FR011 (Performance), NFR001 (Performance) | Section 4.3 (Integration Testing) | WalkingSkeletonIntegrationTest | E2E validation |
| OpenAPI (2.12) | FR015 (Documentation) | Section 9.3 (API Docs) | OpenApiConfiguration | Manual: Swagger UI |
| Performance (2.13) | NFR001 (Performance targets) | Section 10 (Performance KPIs) | JMeter test suite | Load testing |

---

## Risks, Assumptions, Open Questions

### Risks

**Risk 1: Axon Framework Learning Curve**
- **Description:** Team unfamiliar with Event Sourcing patterns may struggle with command handlers, event sourcing handlers, and aggregate reconstruction.
- **Severity:** Medium
- **Mitigation:**
  - Story Contexts include Axon code examples
  - Create CQRS/ES testing patterns doc before Story 2.1 (Retrospective commitment)
  - Widget aggregate serves as reference implementation
  - Axon Test Fixtures simplify testing

**Risk 2: Event Store Performance Under Load**
- **Description:** Partitioning and BRIN indexes unproven at 100K+ event scale in EAF context.
- **Severity:** Medium
- **Mitigation:**
  - Performance test in Story 2.3 validates 100K events
  - Story 2.13 load testing establishes baselines
  - Monitoring in place to detect performance degradation
  - Snapshot strategy (Story 2.4) mitigates aggregate loading cost

**Risk 3: Testcontainers Startup Time**
- **Description:** PostgreSQL Testcontainer startup may slow integration tests (target: <2 min per test).
- **Severity:** Low
- **Mitigation:**
  - Container reuse between tests (Testcontainers singleton pattern)
  - Parallel test execution where possible
  - Fast test profile excludes slow integration tests

**Risk 4: jOOQ Code Generation Complexity**
- **Description:** jOOQ code generation from Flyway migrations may introduce build complexity.
- **Severity:** Low
- **Mitigation:**
  - Clear Gradle task dependencies (flywayMigrate → generateJooq)
  - Generated code excluded from source control (build/ directory)
  - Story 2.6 documents generation process

### Assumptions

**Assumption 1:** PostgreSQL 16.10 partitioning features work as documented (monthly range partitioning supported).

**Assumption 2:** Axon Framework 4.12.1 Spring Boot 3.5.7 compatibility confirmed (verified in architecture.md version matrix).

**Assumption 3:** jOOQ 3.20.8 generates code compatible with Kotlin 2.2.21 (verified in prototype).

**Assumption 4:** Testcontainers PostgreSQL 1.21.3 supports M1/M2 Macs (arm64 architecture) - verified in Epic 1.

**Assumption 5:** Widget aggregate complexity sufficient to validate CQRS pattern without over-engineering.

### Open Questions

**Question 1:** Should we implement optimistic locking in Widget aggregate for concurrent updates?
- **Context:** FR014 mentions conflict resolution, but Story 2.5 doesn't specify
- **Decision Needed By:** Story 2.5 implementation
- **Recommendation:** Start without optimistic locking (YAGNI), add in Epic 4 if needed

**Question 2:** What load test tool - JMeter or Gatling?
- **Context:** Story 2.13 AC1 mentions both
- **Decision Needed By:** Story 2.13 planning
- **Recommendation:** Gatling (better Kotlin integration, programmatic test definition)

**Question 3:** Should projection updates be synchronous or asynchronous?
- **Context:** <10s lag suggests async, but simpler to start sync
- **Decision Needed By:** Story 2.7 implementation
- **Recommendation:** Start with TrackingEventProcessor (async, default), aligns with <10s lag requirement

---

## Test Strategy Summary

### Test Layers for Epic 2

**Layer 1: Static Analysis** (<5s)
- ktlint formatting (pre-commit hook)
- Detekt static analysis (pre-push hook)
- Konsist architecture validation (./gradlew check)

**Layer 2: Unit Tests** (<30s)
- **Axon Test Fixtures** (Story 2.5):
  - Test command handlers: given events → when command → expect events
  - Test validation failures: when invalid command → expect exception
  - Test event sourcing handlers: given events → then aggregate state

- **Nullable Pattern** (Story 2.8):
  - Query handler logic with stubbed dependencies
  - Fast execution without database

- **Example (Story 2.5):**
  ```kotlin
  class WidgetTest : FunSpec({
      test("should create widget with valid name") {
          fixture.givenNoPriorActivity()
              .`when`(CreateWidgetCommand(WidgetId("w1"), "Test Widget"))
              .expectEvents(WidgetCreatedEvent(WidgetId("w1"), "Test Widget"))
      }

      test("should reject widget with blank name") {
          fixture.givenNoPriorActivity()
              .`when`(CreateWidgetCommand(WidgetId("w1"), ""))
              .expectException(ValidationException::class.java)
      }
  })
  ```

**Layer 3: Integration Tests** (<3min)
- **Testcontainers PostgreSQL** (Stories 2.2, 2.6, 2.7, 2.8, 2.10):
  - Real PostgreSQL container
  - Flyway migrations executed
  - jOOQ queries against real database
  - Axon event persistence validated

- **Spring Boot Integration** (Stories 2.10, 2.11):
  - Full Spring context loaded
  - @SpringBootTest with @Autowired field injection + init block
  - MockMvc for REST API testing

- **Pattern (Story 2.10):**
  ```kotlin
  @SpringBootTest
  @ActiveProfiles("test")
  class WidgetControllerIntegrationTest : FunSpec() {
      @Autowired
      private lateinit var mockMvc: MockMvc

      init {
          extension(SpringExtension())

          test("should create widget via REST API") {
              mockMvc.perform(post("/api/widgets")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("""{"name":"Test Widget"}"""))
                  .andExpect(status().isCreated())
          }
      }
  }
  ```

**Layer 4: End-to-End Tests** (<2min per test)
- **Walking Skeleton Test** (Story 2.11):
  - Full CQRS flow: REST → Command → Event → Projection → Query
  - Performance assertions: <200ms API p95, <10s projection lag
  - Real infrastructure (Testcontainers)

**Layer 5: Performance Tests** (Nightly - Story 2.13)
- **Load Testing** (JMeter/Gatling):
  - 100 concurrent users
  - 1000 requests/second
  - Baseline measurements
  - Regression detection

**Layer 6: Property-Based Tests** (Nightly - Future)
- Event invariants validation (Epic 8 scope)
- Aggregate state consistency properties

**Layer 7: Mutation Testing** (Nightly - Story 2.13+)
- Pitest mutation coverage (60-70% target)
- Test effectiveness validation

### Test Coverage Targets

- **Line Coverage:** 85%+ (Kover)
- **Mutation Coverage:** 60-70% (Pitest)
- **Critical Path Coverage:** 100% (CQRS flow must be fully tested)

### Test Execution Performance

- **Unit tests:** <10s per module (Axon Test Fixtures are fast)
- **Integration tests:** <3min total (Testcontainer reuse)
- **E2E test:** <2min (Story 2.11)
- **Full suite:** <15min (CI target)

### Key Testing Patterns

**1. Axon Test Fixtures (Story 2.5)**
- Given-When-Then syntax
- Event-driven assertions
- Validation failure testing

**2. Testcontainers PostgreSQL (Story 2.2+)**
- Singleton pattern (start once per test class)
- @DynamicPropertySource for connection config
- Real database queries

**3. @SpringBootTest Pattern (Story 2.10+)**
- @Autowired field injection + init block
- NOT constructor injection (causes compilation errors)
- @ActiveProfiles("test") for test configuration

**4. Nullable Design Pattern (Story 2.8)**
- Fast query handler unit tests
- Stub dependencies (no database)
- Focus on business logic validation

**5. Eventually Polling Pattern (Story 2.7)**
- Handle asynchronous projection updates
- Kotest's `eventually` with timeout
- Prevents flaky tests

---

## Epic 2 Success Criteria

**Technical Success:**
1. ✅ Widget aggregate demonstrating complete CQRS pattern (Create/Update/Publish)
2. ✅ Event Store with 100K+ events performing within targets (<200ms queries)
3. ✅ Projections updating within <10s lag consistently
4. ✅ REST API with OpenAPI documentation functional
5. ✅ All 87 acceptance criteria met across 13 stories
6. ✅ Performance baselines documented and validated

**Quality Success:**
1. ✅ All tests passing (unit, integration, E2E, performance)
2. ✅ Zero ktlint/Detekt/Konsist violations
3. ✅ 85%+ line coverage, 60%+ mutation coverage
4. ✅ No flaky tests (deterministic execution)

**Pattern Validation:**
1. ✅ Axon Test Fixtures pattern established
2. ✅ Testcontainers integration pattern established
3. ✅ jOOQ projection pattern established
4. ✅ REST API pattern established with RFC 7807 errors

**Documentation:**
1. ✅ Event store optimization documented
2. ✅ CQRS testing patterns documented (Retrospective commitment)
3. ✅ Performance baselines documented
4. ✅ OpenAPI spec generated

**Business Value:**
- Walking Skeleton proves architecture viability
- Reference pattern available for Epic 3-10 development
- Development velocity baseline established
- Technical risk significantly reduced

---

**Epic 2 Dependencies on Epic 1:** ALL SATISFIED ✅

**Epic 2 Complexity:** High (CQRS/ES new to team)

**Epic 2 Estimated Duration:** 13 stories (2-3 sprints)

## Post-Review Follow-ups

- ✅ Restored event store uniqueness invariants while keeping monthly partitioning (`framework/persistence/src/main/resources/db/migration/V002__partitioning_setup.sql:120-179`).
- ✅ Reintroduced an ordered B-tree index for `(aggregateIdentifier, sequenceNumber)` alongside BRIN on timestamps (`framework/persistence/src/main/resources/db/migration/V003__brin_indexes.sql:9-15`).
- ✅ Story metadata and optimization reference updated to reflect the corrected constraints and active status (`docs/stories/epic-2/story-2.3-event-store-partitioning.md:1-184`; `docs/reference/event-store-optimization.md:1-38`).
- ✅ Partition maintenance script now validates schema/table arguments before executing SQL (`scripts/create-event-store-partition.sh:10-74`).

---

*Generated: 2025-11-04*
*Template Version: 6.0*
*Workflow: epic-tech-context*
