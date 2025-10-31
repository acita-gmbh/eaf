# EAF v1.0 Technical Specification

**Author:** Wall-E
**Date:** 2025-10-31
**Project Level:** 2
**Status:** Draft
**Related Documents:**
- [PRD.md](./PRD.md) - Product Requirements
- [architecture.md](./architecture.md) - Detailed Architecture Decisions
- [epics.md](./epics.md) - Epic and Story Breakdown

---

## 1. Executive Summary

The Enterprise Application Framework (EAF) v1.0 technical specification defines the implementation approach for delivering a production-ready, event-sourced framework built on Hexagonal Architecture, CQRS/Event Sourcing (Axon Framework), and Spring Modulith with programmatic boundary enforcement.

**Core Technologies:**
- **Language:** Kotlin 2.2.21 on JVM 21 LTS
- **Framework:** Spring Boot 3.5.7 + Spring Modulith 1.4.4
- **CQRS/ES:** Axon Framework 4.12.1
- **Event Store:** PostgreSQL 16.10 (swappable adapter)
- **Identity:** Keycloak 26.4.2 OIDC
- **Workflow:** Flowable BPMN 7.2.0
- **Build:** Gradle 9.1.0 with Kotlin DSL

**Key Capabilities:**
- 3-layer multi-tenancy with defense-in-depth isolation
- 10-layer JWT validation for enterprise security
- 7-layer testing strategy (Static → Unit → Integration → Property → Fuzz → Concurrency → Mutation)
- Scaffolding CLI eliminating 70-80% boilerplate
- <3 day developer onboarding for basic aggregates

This specification maps each functional requirement (FR001-FR030) to concrete technical implementations.

---

## 2. Technology Stack

### 2.1 Core Technology Stack

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **Language** | Kotlin | 2.2.21 | Type-safe, null-safe, excellent Spring Boot support |
| **Runtime** | JVM | 21 LTS | Long-term support, mature ecosystem |
| **Application Framework** | Spring Boot | 3.5.7 | Industry standard, comprehensive ecosystem |
| **Architecture Enforcement** | Spring Modulith | 1.4.4 | Compile-time boundary verification |
| **CQRS/Event Sourcing** | Axon Framework | 4.12.1 | Mature CQRS/ES with PostgreSQL support |
| **Event Store** | PostgreSQL | 16.10 | FOSS event store with native Axon support |
| **Query Layer** | jOOQ | 3.20.8 | Type-safe SQL for projections |
| **Identity Provider** | Keycloak | 26.4.2 | Enterprise OIDC, multi-tenancy |
| **Workflow Engine** | Flowable BPMN | 7.2.0 | BPMN 2.0, compensating transactions |
| **Build Tool** | Gradle | 9.1.0 | Kotlin DSL, multi-module support |

### 2.2 Testing & Quality Stack

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **Testing Framework** | Kotest | 6.0.4 | Kotlin-native, BDD syntax, property testing |
| **Integration Testing** | Testcontainers | 1.21.3 | Real dependencies (PostgreSQL, Keycloak, Redis) |
| **Fuzz Testing** | Jazzer | 0.25.1 | Coverage-guided fuzzing for security |
| **Mutation Testing** | Pitest | 1.19.0 | Test effectiveness validation |
| **Code Formatting** | ktlint | 1.7.1 | Kotlin official style |
| **Static Analysis** | Detekt | 1.23.8 | Comprehensive code quality rules |
| **Architecture Testing** | Konsist | 0.17.3 | Module boundary verification |
| **Coverage** | Kover | 0.9.3 | Kotlin-native coverage tool |

### 2.3 Infrastructure Stack

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **Container Runtime** | Docker Compose | 2.40.3 | Local development stack |
| **Cache & Session** | Redis | 7.2 | JWT revocation blacklist |
| **Metrics** | Prometheus + Micrometer | 1.15.5 | Time-series metrics collection |
| **Distributed Tracing** | OpenTelemetry | 1.55.0 / 2.20.1 | Vendor-neutral tracing |
| **Structured Logging** | Logback + Logstash | 1.5.19 / 8.1 | JSON logging with context injection |
| **Dashboards** | Grafana | 12.2 | Visualization (Post-MVP) |

### 2.4 Developer Experience Stack

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **CLI Framework** | Picocli | 4.7.7 | Scaffolding CLI |
| **Template Engine** | Mustache | 0.9.14 | Code generation templates |
| **Frontend Framework** | shadcn-admin-kit | Latest | React-admin + shadcn/ui |
| **API Documentation** | Springdoc OpenAPI | 2.6.0 | OpenAPI 3.0 generation |

---

## 3. Functional Requirements Implementation

### FR001: Development Environment Setup and Infrastructure

**Technical Approach:**
- **Docker Compose Stack:** PostgreSQL 16.10, Keycloak 26.4.2, Redis 7.2, Prometheus, Grafana 12.2
- **One-Command Init:** `./scripts/init-dev.sh` orchestrates: Docker startup, health checks, migrations, seed data, Git hooks
- **Gradle Multi-Module:** Convention plugins in `build-logic/` enforce consistent configuration
- **Spring Modulith:** Programmatic boundary enforcement with Konsist architecture tests

**Epic:** Epic 1 (Stories 1.1-1.11)

---

### FR002: Code Generation with Bootstrap Fallbacks

**Technical Approach:**
- **Scaffolding CLI:** Built with Picocli 4.7.7 + Mustache 0.9.14 templates
- **Commands:**
  - `eaf scaffold module <name>` - Generate Spring Modulith module
  - `eaf scaffold aggregate <name>` - Generate CQRS aggregate (commands, events, handlers, tests)
  - `eaf scaffold api-resource <name>` - Generate REST controller with OpenAPI
  - `eaf scaffold projection <name>` - Generate jOOQ projection event handler
  - `eaf scaffold ra-resource <name>` - Generate shadcn-admin-kit UI components
- **Quality Gates:** Generated code immediately passes ktlint, Detekt, Konsist
- **Bootstrap Fallbacks:** Manual templates in `docs/templates/` if CLI unavailable

**Epic:** Epic 7 (Stories 7.1-7.12)

---

### FR003: Event Store with Integrity and Performance

**Technical Approach:**
- **Event Store Adapter:** PostgreSQL 16.10 via Axon JdbcEventStorageEngine (swappable hexagonal adapter)
- **Partitioning:** Time-based monthly partitioning on `domain_event_entry` table
- **Indexing:** BRIN indexes on `timestamp` and `aggregate_identifier` for time-series optimization
- **Snapshots:** Automatic snapshot every 100 events (configurable SnapshotTriggerDefinition)
- **Data Integrity:** jOOQ projections with transaction boundaries, optimistic locking
- **Performance Monitoring:** Prometheus metrics for event lag, throughput, latency
- **Hot Migration:** Flyway migrations for zero-downtime schema evolution

**Flyway Migrations:**
- `V001__event_store_schema.sql` - Axon tables (domain_event_entry, snapshot_entry, saga tables)
- `V002__partitioning_setup.sql` - Monthly partitioning with BRIN indexes
- `V003__brin_indexes.sql` - Performance optimization indexes
- `V005__token_store.sql` - Axon tracking token store

**Epic:** Epic 2 (Stories 2.1-2.4, 2.6)

---

### FR004: Multi-Tenancy with Isolation and Quotas

**Technical Approach:**
- **3-Layer Isolation (Defense-in-Depth):**
  - **Layer 1 (JWT Extraction):** `TenantContextFilter` extracts `tenant_id` from JWT claim → ThreadLocal
  - **Layer 2 (Service Validation):** Axon `TenantValidationInterceptor` validates command `tenantId` matches context
  - **Layer 3 (Database RLS):** PostgreSQL Row-Level Security policies enforce `tenant_id = current_setting('app.tenant_id')`
- **Context Propagation:** `AxonTenantInterceptor` propagates tenant context to async event processors
- **Leak Detection:** Prometheus metrics (`tenant_context_missing`, `cross_tenant_access_attempts`) + security audit logs
- **Resource Quotas:** Token bucket rate limiting per tenant (Bucket4j or similar)

**Flyway Migration:**
- `V004__rls_policies.sql` - PostgreSQL RLS policies on all tenant-scoped tables

**Epic:** Epic 4 (Stories 4.1-4.10)

---

### FR005: Observability with Performance Limits

**Technical Approach:**
- **Structured JSON Logging:** Logback + Logstash encoder with automatic context injection
  - Mandatory fields: `timestamp`, `level`, `logger`, `message`, `trace_id`, `tenant_id`, `service_name`
- **Prometheus Metrics:** Micrometer 1.15.5 integration
  - Default: JVM (memory, GC, threads), HTTP (latency, status), Axon (command/event processing)
  - Custom: Business metrics via injectable `MeterRegistry`
- **OpenTelemetry Tracing:** W3C Trace Context propagation across REST + Axon messages
  - Automatic spans: HTTP requests, Axon commands, Axon events, database queries
- **Performance Limits:**
  - Log rotation: Daily, 7-day retention, max 1GB/day
  - Trace sampling: 100% errors, 10% success (configurable)
  - Overhead budget: <1% CPU for observability

**Logback Configuration:** `logback-spring.xml` in `framework/observability/src/main/resources/`

**Epic:** Epic 5 (Stories 5.1-5.8)

---

### FR006: Authentication, Security, and Compliance

**Technical Approach:**
- **IdP Abstraction:** Spring Security OAuth2 Resource Server with Keycloak OIDC
- **10-Layer JWT Validation:**
  1. **Format:** 3-part structure (header.payload.signature)
  2. **Signature:** RS256 validation via JWKS discovery
  3. **Algorithm:** Hardcoded RS256 (reject HS256)
  4. **Claims Schema:** Required claims (sub, iss, aud, exp, iat, tenant_id, roles)
  5. **Time-Based:** exp, iat, nbf with 30s clock skew
  6. **Issuer/Audience:** Expected issuer + audience validation
  7. **Revocation:** Redis blacklist cache (Layer 7)
  8. **Role:** Normalized role extraction from Keycloak structures
  9. **User:** Optional user existence validation (configurable)
  10. **Injection Detection:** Regex patterns for SQL/XSS in claims
- **Security Audit:** Structured JSON logs for all violations
- **GDPR Compliance:** Crypto-shredding (delete encryption keys), PII masking in logs
- **OWASP Scanning:** Automated dependency scanning in CI/CD

**Keycloak Configuration:**
- Docker Compose realm: `docker/keycloak/realm-export.json`
- Test users: admin (WIDGET_ADMIN role), viewer (WIDGET_VIEWER role)
- Custom ppc64le build: `docker/keycloak/Dockerfile.ppc64le` (UBI9-based)

**Epic:** Epic 3 (Stories 3.1-3.12)

---

### FR007: Workflow Orchestration with Recovery

**Technical Approach:**
- **Flowable BPMN 7.2.0:** Process engine with dedicated PostgreSQL schema (ACT_* tables)
- **Bidirectional Axon Bridge:**
  - **BPMN → Axon:** `AxonCommandGatewayDelegate` (JavaDelegate) dispatches commands from service tasks
  - **Axon → BPMN:** `FlowableEventListener` (@EventHandler) signals process instances from domain events
- **Tenant-Aware Workflows:** `tenant_id` as process variable, tenant-scoped queries
- **Ansible Adapter:** `AnsibleAdapter` (JavaDelegate) executes playbooks for legacy Dockets migration
- **Compensating Transactions:** BPMN error boundary events with compensation handlers
- **Workflow Recovery:**
  - Dead letter queue for failed commands (retry with exponential backoff, max 3 attempts)
  - Manual retry API: `POST /workflow/dlq/:id/retry`
  - Debugging API: `GET /workflow/instances/:id` (variables, current activity, history)

**BPMN Template:** `dockets-pattern.bpmn20.xml` in `framework/workflow/src/main/resources/processes/`

**Epic:** Epic 6 (Stories 6.1-6.10)

---

### FR008: Quality Gates with Configurable Profiles

**Technical Approach:**
- **Quality Gates:**
  - **ktlint 1.7.1:** Code formatting (Kotlin official style)
  - **Detekt 1.23.8:** Static analysis (comprehensive rules)
  - **Konsist 0.17.3:** Architecture boundaries (Spring Modulith validation)
  - **Pitest 1.19.0:** Mutation testing (60-70% target)
  - **Testcontainers 1.21.3:** Real dependencies for integration tests
- **Execution Profiles:**
  - **Fast (<30s):** ktlint + unit tests (pre-commit hook)
  - **Standard (<3min):** Fast + Detekt + integration tests (CI/CD PR validation)
  - **Thorough (<15min):** Standard + full test suite (CI/CD main branch)
  - **Deep (~2.5h):** Property tests + fuzz tests + concurrency tests + mutation tests (nightly)
- **Flaky Test Detection:** Test retry mechanism (max 3 attempts), failure patterns logged
- **Clear Diagnostics:** Structured error messages with remediation instructions

**Git Hooks:**
- `.git-hooks/pre-commit` - ktlint check (<5s)
- `.git-hooks/pre-push` - Detekt + fast unit tests (<30s)

**Epic:** Epic 1 (Stories 1.9-1.10), Epic 8 (Stories 8.1-8.10)

---

### FR010: Hexagonal Architecture with Swappable Adapters

**Technical Approach:**
- **Hexagonal Structure:**
  - **Domain (Core):** Aggregates, commands, events, domain logic (no infrastructure dependencies)
  - **Ports:** Interfaces defined by domain (e.g., `EventStorePort`)
  - **Adapters:** Infrastructure implementations (e.g., `PostgresEventStoreAdapter` implements `EventStorePort`)
- **Swappable Components:**
  - Event Store: PostgreSQL (default), EventStoreDB, MongoDB (via adapter pattern)
  - Identity Provider: Keycloak (default), Auth0, Okta (via IdP abstraction)
  - Cache: Redis (default), Hazelcast (via cache abstraction)
- **Spring Modulith Enforcement:** Konsist architecture tests validate hexagonal boundaries

**Module Structure:** See architecture.md Section 5 (Complete Project Structure)

**Epic:** Epic 1 (Stories 1.7-1.8), All subsequent epics

---

### FR011: Fast Feedback and Performance Monitoring

**Technical Approach:**
- **Fast Feedback Loops:**
  - Unit tests: <30s (Nullables Pattern, no mocking overhead)
  - Build: <2min (Gradle incremental compilation, caching)
  - Full test suite: <3min standard, <15min thorough
- **Performance SLAs:**
  - API p95 latency: <200ms (Prometheus histogram metrics)
  - Event processing lag: <10s (TrackingEventProcessor metrics)
  - Query timeout: 30s hard limit
- **Performance Budgets:** Enforced via Prometheus alerts, failing builds on regression
- **Automated Regression Testing:** JMeter/Gatling load tests in nightly CI/CD

**Prometheus Metrics:**
- `http_request_duration_seconds` (histogram)
- `axon_event_processing_lag_seconds` (gauge)
- `projection_update_duration_seconds` (histogram)

**Epic:** Epic 2 (Story 2.13), Epic 5 (Story 5.6)

---

### FR012: Framework Migration and Multi-Version Support

**Technical Approach:**
- **Versioned Migrations:** Flyway for database schema evolution
- **Automated Upgrade Tooling:** Scripts in `scripts/upgrade/` for major version migrations
- **Version Compatibility Matrix:** Documented in `docs/reference/version-compatibility.md`
- **Breaking Change Detection:** CI/CD pipeline compares public API signatures (e.g., via Metalava or japicmp)
- **Multi-Version Support:** Framework modules published with semantic versioning (Maven local for MVP)

**Post-MVP:** Maven Central publishing, automated changelog generation

**Epic:** Epic 1 (Story 1.12 - deferred to Epic 12 Post-MVP)

---

### FR013: Event Sourcing Debugging Capabilities

**Technical Approach:**
- **Event Replay:** Axon `ReplayEventsCommand` for rebuilding projections
- **Time-Travel Debugging:** Load aggregate at specific point in time via event sequence
- **Aggregate State Reconstruction:** Query event store, replay events to rebuild state
- **Visual Event Stream Inspection:** Admin UI (Post-MVP) or CLI tool showing event stream for aggregate

**Developer Tools:**
- `eaf debug replay-events --aggregate-id=<id>` (CLI command)
- `GET /debug/aggregates/:id/events` (REST API for dev environment)

**Epic:** Epic 7 (Story 7.13 - deferred to Post-MVP)

---

### FR014: Data Consistency and Concurrency Control

**Technical Approach:**
- **Strong Consistency:** Optimistic locking via Axon `@AggregateVersion`
- **Conflict Resolution:** Axon handles concurrency exceptions, retry with backoff
- **Eventual Consistency (Projections):** TrackingEventProcessor with <10s lag target
- **Concurrency Testing:** LitmusKt tests for race conditions (TenantContext, event processors)

**Axon Configuration:**
- Optimistic locking enabled by default
- Conflict retry strategy: Exponential backoff, max 3 retries

**Epic:** Epic 2 (Story 2.4), Epic 8 (Stories 8.4-8.5)

---

### FR015: Comprehensive Onboarding and Learning

**Technical Approach:**
- **Progressive Complexity Learning Paths:**
  - **Milestone 1 (3 days):** Simple aggregate with Axon Test Fixtures
  - **Milestone 2 (1 week):** Standard aggregate with projections, API, multi-tenancy
  - **Milestone 3 (3 months):** Production aggregate with workflows, observability, deployment
- **Golden Path Documentation:**
  - Getting Started: Prerequisites, first aggregate, CQRS fundamentals
  - Tutorials: Simple, standard, production aggregates
  - How-To Guides: Validation, business rules, testing, debugging
  - Reference: Architecture decisions, API docs, configuration
- **Interactive Tutorials:** Step-by-step with validation checkpoints
- **Troubleshooting Guides:** Common errors with solutions
- **Aggregate Correctness Checks:** `eaf validate aggregate <name>` CLI command
- **Architecture Q&A Tools:** AI-powered docs assistant (Post-MVP)
- **Scalable Cohort Onboarding:** 10-50 developers with peer support (12-week program)

**Documentation Structure:** See `docs/` directory structure in architecture.md

**Epic:** Epic 9 (Stories 9.1-9.14)

---

### FR016: Framework Extension and Customization

**Technical Approach:**
- **Extension Points:**
  - **Hooks:** Pre/post command execution, pre/post event handling
  - **Interceptors:** Axon command/event/query interceptors
  - **Validation Logic:** Custom validators injected via Spring
  - **Plugin Architecture:** SPI-based extensions (Post-MVP)
- **Documentation:** Extension point reference in `docs/reference/extension-points.md`

**Example Extension:**
```kotlin
@Component
class CustomCommandInterceptor : CommandHandlerInterceptor<CommandMessage<*>> {
    override fun handle(unitOfWork: UnitOfWork<CommandMessage<*>>,
                       chain: InterceptorChain): Any {
        // Custom logic before command execution
        return chain.proceed()
    }
}
```

**Epic:** Post-MVP (not in current epic breakdown)

---

### FR018: Error Recovery and Dependency Resilience

**Technical Approach:**
- **Circuit Breakers:** Resilience4j for external dependencies (Keycloak, Redis, Prometheus)
- **Retry Strategies:** Exponential backoff with jitter (configurable max attempts)
- **Graceful Degradation:**
  - Keycloak unavailable: Skip revocation check, warn log, allow authentication
  - Redis unavailable: Skip cache, direct database queries
  - PostgreSQL unavailable: Return 503 Service Unavailable, retry connection pool
  - Prometheus unavailable: Drop metrics, continue processing (fail-open)
- **Health Checks:** Spring Boot Actuator `/actuator/health` for all dependencies
- **Fallback Behaviors:** Documented per dependency in `docs/reference/resilience.md`

**Resilience4j Configuration:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      keycloak:
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
```

**Epic:** Epic 3 (Story 3.6 - Redis fallback), Epic 5 (Story 5.6 - observability backpressure)

---

### FR025: Local Development Workflow Support

**Technical Approach:**
- **Hot Reload:** Spring Boot DevTools for automatic restart on code changes
- **Breakpoint Debugging:** IntelliJ IDEA remote debugging for event handlers
- **Local Feature Flags:** Environment variables in `.env` file
- **Pre-Commit Validation:** Git hooks with automated fixes (ktlint format)

**Developer Workflow:**
1. Run `./scripts/init-dev.sh` (one-time setup)
2. Start application: `./gradlew bootRun`
3. Make code changes → DevTools auto-restart
4. Run tests: `./gradlew test` (<30s fast feedback)
5. Commit: Pre-commit hooks run ktlint + tests

**Epic:** Epic 1 (Stories 1.6, 1.10)

---

### FR027: Business Metrics and Analytics

**Technical Approach:**
- **Custom Metric API:** Injectable `MeterRegistry` (Micrometer)
- **KPI Tracking:** Domain events translated to business metrics
- **Feature Adoption Analytics:** Usage counters per feature
- **A/B Testing Support:** Feature flags with metric correlation (Post-MVP)
- **Business Dashboard Integration:** Grafana dashboards (Post-MVP)

**Example Usage:**
```kotlin
@Component
class WidgetService(private val meterRegistry: MeterRegistry) {
    fun publishWidget(id: WidgetId) {
        // Business logic
        meterRegistry.counter("widget.published", "tenant", tenantId).increment()
    }
}
```

**Epic:** Epic 5 (Story 5.7), Epic 10 (Story 10.3)

---

### FR028: Release Management and Feature Control

**Technical Approach:**
- **Feature Flag System:** Environment variables (MVP), LaunchDarkly/Unleash (Post-MVP)
- **Canary Deployment:** Blue/green with health checks, auto-rollback on failures (Post-MVP)
- **Release Versioning:** Semantic versioning (Maven coordinates)
- **Deployment Health Checks:** Spring Boot Actuator readiness/liveness probes

**MVP Feature Flags (Environment Variables):**
```yaml
features:
  workflow-orchestration: ${FEATURE_WORKFLOW:true}
  mutation-testing: ${FEATURE_MUTATION_TESTS:false}
```

**Epic:** Post-MVP (not in current epic breakdown)

---

### FR030: Production Operations Support

**Technical Approach:**
- **Automated Deployment:** Docker Compose for dev, scripts for production (blue/green, rolling)
- **Event Store Backup/Restore:** PostgreSQL pg_dump/pg_restore scripts
- **Disaster Recovery:** Automated backups, documented restore procedures
- **Pre-Configured Alerts:** Prometheus Alertmanager rules for SLA violations
- **Capacity Planning:** Prometheus metrics + historical trend analysis

**Deployment Scripts:**
- `scripts/deploy-blue-green.sh` (Post-MVP)
- `scripts/backup-event-store.sh`
- `scripts/restore-event-store.sh`

**Epic:** Post-MVP (not in current epic breakdown)

---

## 4. Data Models

### 4.1 Event Store Schema

**Primary Tables (Axon Framework):**

```sql
-- Domain events (time-series optimized)
CREATE TABLE domain_event_entry (
    global_index BIGSERIAL PRIMARY KEY,
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    event_identifier VARCHAR(255) NOT NULL UNIQUE,
    meta_data BYTEA,
    payload BYTEA NOT NULL,
    payload_revision VARCHAR(255),
    payload_type VARCHAR(255) NOT NULL,
    time_stamp VARCHAR(255) NOT NULL,
    UNIQUE (aggregate_identifier, sequence_number)
) PARTITION BY RANGE (time_stamp);

-- Monthly partitions created automatically
CREATE TABLE domain_event_entry_2025_10 PARTITION OF domain_event_entry
    FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');

-- BRIN indexes for time-series queries
CREATE INDEX idx_domain_event_timestamp ON domain_event_entry
    USING BRIN (time_stamp);
CREATE INDEX idx_domain_event_aggregate ON domain_event_entry
    USING BRIN (aggregate_identifier);

-- Snapshot storage
CREATE TABLE snapshot_entry (
    aggregate_identifier VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    event_identifier VARCHAR(255) NOT NULL UNIQUE,
    meta_data BYTEA,
    payload BYTEA NOT NULL,
    payload_revision VARCHAR(255),
    payload_type VARCHAR(255) NOT NULL,
    time_stamp VARCHAR(255) NOT NULL,
    PRIMARY KEY (aggregate_identifier, sequence_number)
);
```

### 4.2 Projection Schema Example (Widget)

```sql
-- Widget read model (projection)
CREATE TABLE widget_view (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL, -- DRAFT, PUBLISHED, ARCHIVED
    owner_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

-- PostgreSQL Row-Level Security (RLS)
ALTER TABLE widget_view ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON widget_view
    USING (tenant_id = current_setting('app.tenant_id', true));

-- Indexes for queries
CREATE INDEX idx_widget_tenant ON widget_view (tenant_id);
CREATE INDEX idx_widget_status ON widget_view (status);
CREATE INDEX idx_widget_created ON widget_view (created_at DESC);
```

### 4.3 Multi-Tenancy Context

**Tenant Context Propagation:**
- Extracted from JWT `tenant_id` claim
- Stored in ThreadLocal (`TenantContext`)
- Propagated to Axon event metadata
- Set as PostgreSQL session variable before queries

---

## 5. API Specifications

### 5.1 REST API Design Principles

- **Base URL:** `/api/v1`
- **Authentication:** Bearer JWT (OAuth2)
- **Response Format:** Direct response (no envelope)
- **Error Format:** RFC 7807 Problem Details
- **Pagination:** Cursor-based (no offset/limit)
- **Versioning:** URL path (`/v1`, `/v2`)

### 5.2 Standard Endpoints (Per Resource)

**Widget Example:**

```yaml
POST   /api/v1/widgets          # Create widget (returns 201 + Location header)
GET    /api/v1/widgets/:id      # Get single widget (returns 200)
GET    /api/v1/widgets          # List widgets with cursor pagination (returns 200)
PUT    /api/v1/widgets/:id      # Update widget (returns 200)
DELETE /api/v1/widgets/:id      # Delete widget (returns 204)
GET    /api/v1/widgets/search   # Search widgets (returns 200)
```

### 5.3 Error Response Format (RFC 7807)

```json
{
  "type": "https://eaf.axians.com/errors/validation-error",
  "title": "Validation Error",
  "status": 400,
  "detail": "Widget name cannot be empty",
  "instance": "/api/v1/widgets",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "tenantId": "tenant-a",
  "timestamp": "2025-10-31T15:30:00Z"
}
```

### 5.4 Cursor Pagination

**Request:**
```http
GET /api/v1/widgets?limit=50&cursor=eyJpZCI6IjEyMyIsInRpbWVzdGFtcCI6MTYzMDQ1MjAwMH0=
```

**Response:**
```json
{
  "data": [ /* ... widget objects ... */ ],
  "pagination": {
    "next": "eyJpZCI6IjE3MyIsInRpbWVzdGFtcCI6MTYzMDQ1MjAwMH0=",
    "previous": null,
    "hasMore": true
  }
}
```

### 5.5 OpenAPI Documentation

- **Generation:** Springdoc OpenAPI 2.6.0 automatic generation
- **Endpoint:** `/v3/api-docs` (JSON spec)
- **Swagger UI:** `/swagger-ui.html`
- **Security:** Bearer JWT scheme documented

---

## 6. Integration Points

### 6.1 Keycloak OIDC ↔ EAF Security Module

**Flow:**
1. User authenticates via Keycloak OIDC
2. Keycloak issues JWT with claims: `sub`, `iss`, `aud`, `exp`, `iat`, `tenant_id`, `roles`
3. EAF validates JWT (10 layers) via `JwtValidationFilter`
4. Security context populated with user, roles, tenant
5. All API requests require valid JWT

**Configuration:**
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8080/realms/eaf
          jwk-set-uri: http://keycloak:8080/realms/eaf/protocol/openid-connect/certs
```

---

### 6.2 Axon Framework ↔ PostgreSQL Event Store

**Flow:**
1. Command dispatched via `CommandGateway`
2. Aggregate handles command, emits events
3. Axon stores events in PostgreSQL (`JdbcEventStorageEngine`)
4. Events published to `EventBus`
5. Event handlers update projections
6. Queries retrieve data from projections

**Configuration:**
```kotlin
@Configuration
class AxonConfiguration {
    @Bean
    fun eventStorageEngine(dataSource: DataSource): EventStorageEngine {
        return JdbcEventStorageEngine.builder()
            .dataSource(dataSource)
            .transactionManager(NoTransactionManager.INSTANCE)
            .build()
    }
}
```

---

### 6.3 Flowable BPMN ↔ Axon Framework Bridge

**BPMN → Axon (Commands):**
```xml
<serviceTask id="createWidget"
             name="Create Widget"
             flowable:delegateExpression="${axonCommandGatewayDelegate}">
  <extensionElements>
    <flowable:field name="commandType" stringValue="CreateWidgetCommand"/>
    <flowable:field name="commandData" expression="${widgetData}"/>
  </extensionElements>
</serviceTask>
```

**Axon → BPMN (Events):**
```kotlin
@Component
class FlowableEventListener(private val runtimeService: RuntimeService) {
    @EventHandler
    fun on(event: WidgetPublishedEvent) {
        runtimeService.signalEventReceived(
            "widgetPublished",
            event.widgetId.toString()
        )
    }
}
```

---

### 6.4 shadcn-admin-kit ↔ EAF REST API

**Data Provider (React-Admin):**
```typescript
const dataProvider = createRestDataProvider({
  baseUrl: '/api/v1',
  authProvider: keycloakAuthProvider,
  paginationMode: 'cursor'
});
```

**Authentication Flow:**
1. User logs in via Keycloak (OIDC)
2. shadcn-admin-kit stores JWT in browser
3. All API requests include JWT in `Authorization: Bearer <token>` header
4. EAF validates JWT, returns data

---

## 7. Security Implementation

### 7.1 10-Layer JWT Validation

Implemented in `framework/security` module:

1. **Format Validation** - 3-part structure
2. **Signature Validation** - RS256 via JWKS
3. **Algorithm Validation** - RS256 only
4. **Claims Schema** - Required claims present
5. **Time-Based Validation** - exp, iat, nbf with 30s skew
6. **Issuer/Audience** - Expected values
7. **Revocation Check** - Redis blacklist
8. **Role Validation** - Normalized roles
9. **User Validation** - User exists (optional)
10. **Injection Detection** - SQL/XSS patterns

### 7.2 3-Layer Multi-Tenancy

1. **Layer 1 (Filter):** `TenantContextFilter` extracts `tenant_id` from JWT → ThreadLocal
2. **Layer 2 (Interceptor):** `TenantValidationInterceptor` validates command `tenantId` matches context
3. **Layer 3 (Database):** PostgreSQL RLS policies enforce tenant isolation

### 7.3 OWASP ASVS Compliance

**Target:** 100% Level 1, 50% Level 2

**Key Controls:**
- V2 Authentication: Keycloak OIDC, 10-layer JWT
- V4 Access Control: RBAC, multi-tenancy isolation
- V5 Validation: Input validation, output encoding
- V7 Error Handling: RFC 7807, no sensitive data in errors
- V8 Data Protection: Encryption at rest (PostgreSQL), in transit (TLS)
- V9 Communications: TLS 1.3, HSTS headers
- V10 Malicious Code: OWASP dependency scanning

---

## 8. Performance Targets

### 8.1 Performance Budgets (NFR001)

| Metric | Target | Measurement |
|--------|--------|-------------|
| API p95 latency | <200ms | Prometheus histogram |
| Event processing lag | <10s | TrackingEventProcessor metrics |
| Full test suite | <15min | CI/CD pipeline |
| Developer feedback | <3min | Local test execution |
| Query timeout | 30s | Hard limit (fail query) |

### 8.2 Optimization Strategies

- **Event Store:** BRIN indexes, monthly partitioning, snapshots every 100 events
- **Projections:** jOOQ compiled queries, database indexes
- **Caching:** Redis for JWT revocation, future query caching
- **Connection Pooling:** HikariCP with tuned settings
- **Observability Overhead:** <1% CPU via sampling and log rotation

---

## 9. Testing Strategy

### 9.1 7-Layer Defense-in-Depth

1. **Static Analysis** - ktlint, Detekt, Konsist (compile-time)
2. **Unit Tests** - Nullables Pattern, Kotest (100-1000x faster than mocking)
3. **Integration Tests** - Testcontainers (real PostgreSQL, Keycloak, Redis)
4. **Property-Based Tests** - Kotest Property (idempotence, invariants)
5. **Fuzz Tests** - Jazzer (7 targets × 5 min, security vulnerabilities)
6. **Concurrency Tests** - LitmusKt (race conditions, memory model)
7. **Mutation Tests** - Pitest (test effectiveness, 60-70% target)

### 9.2 Constitutional TDD

**Mandatory:** All production code written test-first (Red-Green-Refactor)

**Enforcement:**
- Git pre-commit hook: Verify test exists for new code
- CI/CD: Fail build if code lacks tests
- Code review: Require TDD evidence (test commits before implementation)

### 9.3 Coverage Targets

- **Line Coverage:** 85%+ (measured by Kover)
- **Mutation Score:** 60-70% (measured by Pitest)
- **Critical Paths:** >90% coverage (security, multi-tenancy)

---

## 10. Deployment Approach

### 10.1 Development Deployment

**Single-Server Stack (Docker Compose):**
- PostgreSQL 16.10 (event store + projections)
- Keycloak 26.4.2 (identity provider)
- Redis 7.2 (JWT revocation cache)
- Prometheus (metrics collection)
- Grafana 12.2 (dashboards - optional)
- EAF Application (Spring Boot)

**Deployment Command:**
```bash
./scripts/init-dev.sh  # One-command setup
./gradlew bootRun      # Start application
```

### 10.2 Production Deployment (Phase 1: Active-Passive HA)

**Components:**
- **Application Servers:** 2+ Spring Boot instances (stateless, load-balanced)
- **PostgreSQL:** Patroni-managed cluster (1 primary + 2 standbys)
- **Keycloak:** 2+ instances (shared PostgreSQL database)
- **Redis:** Sentinel-managed cluster (1 primary + 2 replicas)
- **Load Balancer:** HAProxy or NGINX
- **Failover:** <2 minutes (automatic Patroni failover)

### 10.3 Production Deployment (Phase 2: Active-Active Multi-DC)

**Post-MVP Enhancement:**
- **Multi-Region PostgreSQL:** Bi-directional replication
- **Distributed Locking:** Redis Redlock or PostgreSQL advisory locks
- **Conflict Resolution:** CRDTs or last-write-wins
- **Failover:** <30 seconds (load balancer health checks)

---

## 11. Migration from Prototype

### 11.1 Prototype Structure Reuse

**What to Keep:**
- Build system (Gradle, convention plugins, version catalog)
- Module structure (framework/, products/, shared/, apps/, tools/)
- Docker Compose stack
- CI/CD pipelines
- Git hooks
- Testing infrastructure

**What to Clean:**
```bash
rm -rf framework/*/src/     # Remove prototype implementations
rm -rf products/*/src/      # Remove prototype products
# Keep: Build config, empty module structure
```

### 11.2 First Implementation Steps

1. **Epic 1:** Project foundation (Stories 1.1-1.11)
2. **Epic 2:** Walking Skeleton CQRS demo (Stories 2.1-2.13)
3. Validate architecture viability with end-to-end flow
4. Continue with Epic 3-10 sequentially

---

## 12. Non-Functional Requirements Implementation

### NFR001: Performance

**Implementation:**
- Performance budgets enforced via Prometheus alerts
- Load testing in nightly CI/CD (JMeter/Gatling)
- Event store optimization (partitioning, BRIN indexes, snapshots)
- jOOQ compiled queries for projections
- Connection pooling (HikariCP)

### NFR002: Security and Compliance

**Implementation:**
- OWASP ASVS 5.0 compliance (100% L1, 50% L2)
- 10-layer JWT validation
- 3-layer multi-tenancy isolation
- OWASP dependency scanning (CI/CD)
- Security audit logging
- GDPR compliance (crypto-shredding, PII masking)

### NFR003: Developer Experience

**Implementation:**
- Scaffolding CLI (70-80% boilerplate elimination)
- Golden Path documentation (Getting Started, Tutorials, How-To, Reference)
- <3 day onboarding for simple aggregates (Majlinda validation)
- Fast feedback loops (<30s unit tests, <3min full suite)
- Code generation passes quality gates immediately
- Developer Net Promoter Score (dNPS) ≥+50 target

---

## 13. Appendix: Technology Version Summary

**Full version verification log:** See architecture.md Section 2

**Critical Dependencies:**
- Kotlin 2.2.21 (verified 2025-10-30)
- Spring Boot 3.5.7 (verified 2025-10-30)
- Spring Modulith 1.4.4 (verified 2025-10-31)
- Axon Framework 4.12.1 (verified 2025-10-31)
- PostgreSQL 16.10 (verified 2025-10-30)
- Keycloak 26.4.2 (verified 2025-10-30)

**Next Version Review:** Quarterly (align with Keycloak ppc64le rebuild schedule)

---

**End of Technical Specification**
