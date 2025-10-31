# EAF - Epic Breakdown

**Author:** Wall-E
**Date:** 2025-10-31
**Project Level:** 2
**Target Scale:** Medium - Enterprise Framework for internal development teams

---

## Overview

This document provides the detailed epic breakdown for EAF, expanding on the high-level epic list in the [PRD](./PRD.md).

Each epic includes:

- Expanded goal and value proposition
- Complete story breakdown with user stories
- Acceptance criteria for each story
- Story sequencing and dependencies

**Epic Sequencing Principles:**

- Epic 1 establishes foundational infrastructure and initial functionality
- Subsequent epics build progressively, each delivering significant end-to-end value
- Stories within epics are vertically sliced and sequentially ordered
- No forward dependencies - each story builds only on previous work

---

## Epic 1: Foundation & Project Infrastructure

**Expanded Goal:**
Epic 1 establishes the foundational infrastructure for the entire EAF v1.0 project by creating a Gradle multi-module monorepo with Spring Modulith boundary enforcement, convention plugins for consistent configuration, a complete Docker Compose development stack with one-command initialization, DDD base classes for domain modeling, and CI/CD pipelines with Git hooks for quality enforcement. This epic delivers a fully functional, tested foundation that enables all subsequent epic development with architectural consistency from inception.

**Estimated Stories:** 11

---

**Story 1.1: Initialize Repository and Root Build System**

As a framework developer,
I want to initialize the EAF repository with Gradle 9.1.0 and Kotlin DSL configuration,
So that I have a working build system foundation for multi-module development.

**Acceptance Criteria:**
1. Git repository initialized with main branch
2. Gradle wrapper 9.1.0 configured (gradlew, gradlew.bat)
3. Root build.gradle.kts with Kotlin plugin and basic configuration
4. settings.gradle.kts with project name "eaf-v1"
5. .gitignore configured for Gradle, IDE, and build artifacts
6. ./gradlew build executes successfully (even with no modules yet)
7. README.md with project overview and setup instructions

**Prerequisites:** None (first story)

---

**Story 1.2: Create Multi-Module Structure**

As a framework developer,
I want to establish the multi-module monorepo structure (framework/, products/, shared/, apps/, tools/),
So that I have a logical organization matching the architectural design.

**Acceptance Criteria:**
1. Directory structure created: framework/, products/, shared/, apps/, tools/, docker/, scripts/, docs/
2. Each top-level directory has build.gradle.kts
3. settings.gradle.kts includes all modules
4. Framework submodules defined: core, security, multi-tenancy, cqrs, persistence, observability, workflow, web
5. All modules compile with empty src/ directories
6. ./gradlew projects lists all modules correctly

**Prerequisites:** Story 1.1

---

**Story 1.3: Implement Convention Plugins in build-logic/**

As a framework developer,
I want to create Gradle convention plugins for common configurations,
So that all modules share consistent build settings without duplication.

**Acceptance Criteria:**
1. build-logic/ composite build created
2. Convention plugins implemented:
   - eaf.kotlin-common.gradle.kts (Kotlin version, compiler settings)
   - eaf.spring-boot.gradle.kts (Spring Boot plugin, dependencies)
   - eaf.quality-gates.gradle.kts (ktlint, Detekt configuration)
   - eaf.testing.gradle.kts (Kotest, Testcontainers, test source sets)
3. All framework modules apply relevant convention plugins
4. ./gradlew build compiles all modules with consistent settings
5. Convention plugins tested with at least one framework module

**Prerequisites:** Story 1.2

---

**Story 1.4: Create Version Catalog with Verified Dependencies**

As a framework developer,
I want to define all dependency versions in gradle/libs.versions.toml,
So that version management is centralized and consistent across all modules.

**Acceptance Criteria:**
1. gradle/libs.versions.toml created with all 28+ dependencies
2. Core stack versions verified: Kotlin 2.2.21, Spring Boot 3.5.7, Spring Modulith 1.4.4, Axon 4.12.1, PostgreSQL 16.10
3. Testing stack versions: Kotest 6.0.4, Testcontainers 1.21.3, Pitest 1.19.0, ktlint 1.7.1, Detekt 1.23.8
4. All framework modules use version catalog references (no hardcoded versions)
5. ./gradlew dependencies shows correct version resolution
6. Version catalog validated against architecture.md specifications

**Prerequisites:** Story 1.3

---

**Story 1.5: Docker Compose Development Stack**

As a framework developer,
I want a Docker Compose stack with PostgreSQL, Keycloak, Redis, Prometheus, and Grafana,
So that I have all infrastructure services for local development.

**Acceptance Criteria:**
1. docker-compose.yml created with services: postgres (16.10), keycloak (26.4.2), redis (7.2), prometheus, grafana (12.2)
2. Custom Keycloak configuration in docker/keycloak/ with realm-export.json (test users, roles)
3. PostgreSQL init scripts in docker/postgres/init-scripts/ (schema creation, RLS setup)
4. Prometheus configuration in docker/prometheus/prometheus.yml
5. All services start successfully with docker-compose up
6. Health checks pass for all services
7. Configurable ports via environment variables (e.g., GRAFANA_PORT)

**Prerequisites:** Story 1.4

---

**Story 1.6: One-Command Initialization Script**

As a framework developer,
I want a single command that initializes the complete development environment,
So that onboarding is as simple as running one script.

**Acceptance Criteria:**
1. scripts/init-dev.sh script created
2. Script performs: Docker Compose startup, health check verification, Git hooks installation, dependency download
3. scripts/health-check.sh validates all services are ready (PostgreSQL connectable, Keycloak realm exists, Redis responding)
4. scripts/seed-data.sh loads test data (Keycloak users, sample tenants)
5. scripts/install-git-hooks.sh installs pre-commit and pre-push hooks
6. ./scripts/init-dev.sh completes successfully in <5 minutes on clean system
7. All services accessible after script completion
8. Script provides clear progress output and error messages

**Prerequisites:** Story 1.5

---

**Story 1.7: DDD Base Classes in framework/core**

As a framework developer,
I want DDD base classes (AggregateRoot, Entity, ValueObject, DomainEvent),
So that all domain models have consistent foundations.

**Acceptance Criteria:**
1. framework/core module structure created with src/main/kotlin/com/axians/eaf/framework/core/
2. Base classes implemented:
   - domain/AggregateRoot.kt (abstract base with identity)
   - domain/Entity.kt (abstract base with equals/hashCode)
   - domain/ValueObject.kt (abstract base for immutability)
   - domain/DomainEvent.kt (marker interface)
3. Common types implemented: Money.kt, Quantity.kt, Identifier.kt
4. Exception hierarchy: EafException, ValidationException, TenantIsolationException, AggregateNotFoundException
5. Unit tests for all base classes using Kotest
6. All tests pass in <5 seconds
7. Module compiles and publishes to local Maven repository

**Prerequisites:** Story 1.4

---

**Story 1.8: Spring Modulith Module Boundary Enforcement**

As a framework developer,
I want Spring Modulith configured to enforce module boundaries,
So that architectural violations are caught at compile time.

**Acceptance Criteria:**
1. Spring Modulith 1.4.4 dependency added to framework modules
2. Konsist 0.17.3 architecture tests created in shared/testing module
3. Tests verify: package-by-feature structure, no cyclic dependencies, hexagonal architecture compliance
4. Module boundary violations fail the build
5. Spring Modulith documentation generated (module canvas)
6. ./gradlew check includes Konsist architecture validation
7. All current modules pass boundary validation

**Prerequisites:** Story 1.7

---

**Story 1.9: CI/CD Pipeline Foundation**

As a framework developer,
I want GitHub Actions CI/CD pipelines for automated quality validation,
So that all commits and PRs are automatically tested.

**Acceptance Criteria:**
1. .github/workflows/ci.yml created (fast feedback pipeline <15min)
2. CI pipeline runs: build, ktlint, Detekt, unit tests, integration tests
3. .github/workflows/nightly.yml created (deep validation ~2.5h)
4. Nightly pipeline runs: property tests, fuzz tests, mutation tests, concurrency tests
5. .github/workflows/security-review.yml with OWASP dependency check
6. Pipeline runs on: push to main, pull requests, nightly schedule
7. All pipelines pass with current codebase
8. Pipeline results visible in GitHub Actions

**Prerequisites:** Story 1.8

---

**Story 1.10: Git Hooks for Quality Gates**

As a framework developer,
I want Git hooks that enforce quality gates before commits and pushes,
So that code quality issues are caught locally before CI/CD.

**Acceptance Criteria:**
1. .git-hooks/pre-commit script runs ktlint check (<5s)
2. .git-hooks/pre-push script runs Detekt + fast unit tests (<30s)
3. scripts/install-git-hooks.sh installs hooks (called by init-dev.sh)
4. Hooks can be bypassed with --no-verify flag (but discouraged)
5. .github/workflows/validate-hooks.yml ensures hooks match CI requirements
6. Clear error messages when hooks fail with remediation instructions
7. Hooks tested with intentional violations (formatting error, failing test)

**Prerequisites:** Story 1.8

---

**Story 1.11: Foundation Documentation and Project README**

As a framework developer,
I want comprehensive foundation documentation,
So that the project structure and setup process are clearly explained.

**Acceptance Criteria:**
1. README.md updated with: project overview, prerequisites, quick start (./scripts/init-dev.sh), architecture overview, contribution guidelines
2. docs/getting-started/00-prerequisites.md documents required tools (JDK 21, Docker, Git)
3. CONTRIBUTING.md with development workflow and quality standards
4. LICENSE file (Apache 2.0 or Axians choice)
5. .editorconfig for consistent code formatting across IDEs
6. All documentation tested by following instructions on clean system
7. Documentation links verified and working

**Prerequisites:** Story 1.6

---

## Epic 2: Walking Skeleton - CQRS/Event Sourcing Core

**Expanded Goal:**
Epic 2 implements the first complete CQRS/Event Sourcing vertical slice that proves the architecture viability by delivering an end-to-end flow from REST API → Command → Event → Projection → Query. This epic integrates Axon Framework with PostgreSQL event store, implements event store optimizations (partitioning, BRIN indexes, snapshots), configures jOOQ for type-safe projection queries, establishes REST API foundation with OpenAPI documentation, and validates performance targets. The Walking Skeleton serves as the reference pattern for all future domain development and validates that the architectural decisions are sound and implementable.

**Estimated Stories:** 13

---

**Story 2.1: Axon Framework Core Configuration**

As a framework developer,
I want Axon Framework configured with Command and Query Gateways,
So that I can dispatch commands and execute queries through Axon infrastructure.

**Acceptance Criteria:**
1. framework/cqrs module created with Axon Framework 4.12.1 dependency
2. AxonConfiguration.kt configures CommandGateway and QueryGateway beans
3. CommandBus, EventBus, and QueryBus configured with default settings
4. Axon auto-configuration enabled in Spring Boot
5. Unit tests verify gateways are injectable and functional
6. ./gradlew :framework:cqrs:test passes in <10 seconds
7. Module documented in README.md

**Prerequisites:** Epic 1 complete

---

**Story 2.2: PostgreSQL Event Store Setup with Flyway**

As a framework developer,
I want PostgreSQL configured as the Axon event store using JdbcEventStorageEngine,
So that events are persisted durably in PostgreSQL.

**Acceptance Criteria:**
1. framework/persistence module created with Axon JDBC and Flyway dependencies
2. PostgresEventStoreConfiguration.kt configures JdbcEventStorageEngine
3. Flyway migration V001__event_store_schema.sql creates Axon tables (domain_event_entry, snapshot_entry, saga tables)
4. DataSource configuration for PostgreSQL in application.yml
5. Integration test verifies events can be stored and retrieved
6. Testcontainers PostgreSQL used for integration tests
7. Migration executes successfully on docker-compose PostgreSQL
8. Event store tables visible in database

**Prerequisites:** Story 2.1

---

**Story 2.3: Event Store Partitioning and Optimization**

As a framework developer,
I want time-based partitioning and BRIN indexes on the event store,
So that query performance remains acceptable as event volume grows.

**Acceptance Criteria:**
1. Flyway migration V002__partitioning_setup.sql implements monthly partitioning on domain_event_entry
2. Flyway migration V003__brin_indexes.sql creates BRIN indexes on timestamp and aggregate_identifier
3. Partition creation script for automatic monthly partition generation
4. Performance test validates query performance with 100K+ events
5. Partitioning documented in docs/reference/event-store-optimization.md
6. Integration test validates events are correctly partitioned by timestamp
7. Query performance meets <200ms target for aggregate event retrieval

**Prerequisites:** Story 2.2

---

**Story 2.4: Snapshot Support for Aggregate Optimization**

As a framework developer,
I want automatic snapshot creation every 100 events for aggregates,
So that aggregate loading performance remains fast even with long event histories.

**Acceptance Criteria:**
1. SnapshotTriggerDefinition configured in Axon (every 100 events)
2. Snapshot serialization using Jackson configured
3. snapshot_entry table schema validated
4. Integration test creates aggregate with 250+ events and verifies snapshots created
5. Aggregate loading test validates snapshot usage (loads from snapshot, not full history)
6. Performance improvement measured and documented (>10x faster for 1000+ events)
7. Snapshot management documented

**Prerequisites:** Story 2.3

---

**Story 2.5: Demo Widget Aggregate with Commands and Events**

As a framework developer,
I want a simple Widget aggregate with CQRS commands and events,
So that I have a working example demonstrating the CQRS pattern.

**Acceptance Criteria:**
1. products/widget-demo module created
2. Widget.kt aggregate with WidgetId value object
3. Commands implemented: CreateWidgetCommand, UpdateWidgetCommand, PublishWidgetCommand
4. Events implemented: WidgetCreatedEvent, WidgetUpdatedEvent, WidgetPublishedEvent
5. Command handlers in Widget aggregate with business logic validation
6. Event sourcing handlers for state reconstruction
7. Axon Test Fixtures tests for all command scenarios (success and validation failures)
8. All tests pass in <10 seconds
9. Aggregate documented with inline KDoc

**Prerequisites:** Story 2.2

---

**Story 2.6: jOOQ Configuration and Projection Tables**

As a framework developer,
I want jOOQ configured for type-safe SQL queries on projection tables,
So that read models are queryable with compile-time safety.

**Acceptance Criteria:**
1. jOOQ 3.20.8 dependency added to framework/persistence
2. JooqConfiguration.kt configures DSLContext bean
3. jOOQ code generation configured in Gradle (generates classes from DB schema)
4. Flyway migration V100__widget_projections.sql creates widget_view table
5. jOOQ generated classes available for widget_view table
6. Type-safe query example documented
7. Integration test validates jOOQ query execution
8. ./gradlew generateJooq generates code successfully

**Prerequisites:** Story 2.3

---

**Story 2.7: Widget Projection Event Handler**

As a framework developer,
I want an event handler that projects Widget events into the widget_view read model,
So that queries can retrieve current Widget state efficiently.

**Acceptance Criteria:**
1. WidgetProjectionEventHandler.kt created as @Component with @EventHandler methods
2. Event handlers for: WidgetCreatedEvent, WidgetUpdatedEvent, WidgetPublishedEvent
3. Handlers use jOOQ DSLContext to insert/update widget_view table
4. TrackingEventProcessor configured for projection updates (real-time, <10s lag)
5. Integration test validates: dispatch command → event published → projection updated
6. Projection lag measured and meets <10s target
7. Error handling for projection failures (logged, metrics emitted)

**Prerequisites:** Story 2.5, Story 2.6

---

**Story 2.8: Widget Query Handler**

As a framework developer,
I want query handlers that retrieve Widget data from projections,
So that read operations are fast and don't load full event history.

**Acceptance Criteria:**
1. Queries implemented: FindWidgetQuery (by ID), ListWidgetsQuery (with cursor pagination)
2. WidgetQueryHandler.kt with @QueryHandler methods
3. Query handlers use jOOQ for type-safe SQL queries on widget_view
4. Cursor-based pagination implemented (no offset-limit)
5. Unit tests with Nullable Pattern for query logic
6. Integration test validates: store events → query returns projected data
7. Query performance <50ms for single widget, <200ms for paginated list

**Prerequisites:** Story 2.7

---

**Story 2.9: REST API Foundation with RFC 7807 Error Handling**

As a framework developer,
I want REST API foundation with standardized error responses,
So that API consumers have consistent, machine-readable error information.

**Acceptance Criteria:**
1. framework/web module created with Spring Web MVC dependencies
2. ProblemDetailExceptionHandler.kt implements RFC 7807 Problem Details
3. Error responses include: type, title, status, detail, instance, traceId, tenantId
4. RestConfiguration.kt with CORS, Jackson ObjectMapper, response formatting
5. CursorPaginationSupport.kt utility for cursor-based pagination
6. Integration test validates error response format
7. All framework exceptions mapped to appropriate HTTP status codes

**Prerequisites:** Story 2.1

---

**Story 2.10: Widget REST API Controller**

As a framework developer,
I want REST API endpoints for Widget CRUD operations,
So that the Widget aggregate is accessible via HTTP API.

**Acceptance Criteria:**
1. WidgetController.kt created with @RestController
2. Endpoints implemented: POST /widgets (create), GET /widgets/:id (find), GET /widgets (list), PUT /widgets/:id (update)
3. Controller uses CommandGateway for writes, QueryGateway for reads
4. Request/Response DTOs with validation annotations
5. OpenAPI 3.0 annotations for API documentation
6. Integration test validates full CRUD flow via REST API
7. Swagger UI accessible at /swagger-ui.html showing Widget endpoints
8. All API operations return correct HTTP status codes

**Prerequisites:** Story 2.8, Story 2.9

---

**Story 2.11: End-to-End Integration Test**

As a framework developer,
I want a comprehensive end-to-end test validating the complete CQRS flow,
So that I can prove the Walking Skeleton architecture works correctly.

**Acceptance Criteria:**
1. WalkingSkeletonIntegrationTest.kt created using Testcontainers
2. Test scenario: POST /widgets → CreateWidgetCommand → WidgetCreatedEvent → Projection updated → GET /widgets/:id returns data
3. Test validates: Command dispatch, Event persistence, Projection update, Query retrieval
4. Test measures and validates: API latency <200ms, Projection lag <10s
5. Test uses real PostgreSQL (Testcontainers), not mocks
6. Test passes consistently (no flakiness)
7. Test execution time <2 minutes
8. Test documented as reference example

**Prerequisites:** Story 2.10

---

**Story 2.12: OpenAPI Documentation and Swagger UI**

As a framework developer,
I want automatic OpenAPI 3.0 documentation generation,
So that API consumers have up-to-date, interactive API documentation.

**Acceptance Criteria:**
1. Springdoc OpenAPI 2.6.0 dependency added
2. OpenApiConfiguration.kt with API metadata (title, version, description)
3. Security scheme configured (Bearer JWT)
4. Swagger UI accessible at /swagger-ui.html
5. Widget API fully documented with request/response schemas
6. "Try it out" functionality works in Swagger UI (with test JWT)
7. OpenAPI JSON spec available at /v3/api-docs
8. API documentation includes examples and descriptions

**Prerequisites:** Story 2.10

---

**Story 2.13: Performance Baseline and Monitoring**

As a framework developer,
I want performance baseline measurements and monitoring for the Walking Skeleton,
So that I can validate performance targets and detect regressions.

**Acceptance Criteria:**
1. Performance test suite created with JMeter or Gatling
2. Load test scenarios: 100 concurrent users, 1000 requests/second
3. Baseline measurements documented: API p95 latency, event processing lag, throughput
4. Prometheus metrics configured for Widget endpoints
5. Performance meets targets: API p95 <200ms, event lag <10s
6. Performance regression test added to nightly CI/CD
7. Performance baseline documented in docs/reference/performance-baselines.md

**Prerequisites:** Story 2.11

---

## Epic 3: Authentication & Authorization

**Expanded Goal:**
Epic 3 establishes enterprise-grade authentication and authorization by integrating Keycloak OIDC with Spring Security OAuth2 Resource Server, implementing comprehensive 10-layer JWT validation (format, signature RS256, algorithm, claims schema, time-based, issuer/audience, Redis-cached revocation, role, user, injection detection), configuring role-based access control with normalized role mapping, and building custom ppc64le Keycloak Docker images for multi-architecture support. This epic delivers production-ready security that meets OWASP ASVS Level 1 compliance requirements and enables secure API access for all subsequent features.

**Estimated Stories:** 12

---

**Story 3.1: Spring Security OAuth2 Resource Server Foundation**

As a framework developer,
I want Spring Security configured as an OAuth2 Resource Server,
So that JWT-based authentication is enforced on all API endpoints.

**Acceptance Criteria:**
1. framework/security module created with Spring Security OAuth2 dependencies
2. SecurityConfiguration.kt configures HTTP security with JWT authentication
3. OAuth2 Resource Server configured with Keycloak issuer URI
4. All API endpoints require authentication by default (except /actuator/health)
5. Integration test validates unauthenticated requests return 401 Unauthorized
6. Valid JWT allows API access
7. Security filter chain documented

**Prerequisites:** Epic 2 complete

---

**Story 3.2: Keycloak OIDC Discovery and JWKS Integration**

As a framework developer,
I want automatic Keycloak public key discovery via JWKS endpoint,
So that JWT signature validation uses current Keycloak keys.

**Acceptance Criteria:**
1. KeycloakOidcConfiguration.kt configures OIDC discovery
2. JWK Set URI configured: http://keycloak:8080/realms/eaf/protocol/openid-connect/certs
3. Public key caching implemented (refresh every 10 minutes)
4. KeycloakJwksProvider.kt fetches and caches public keys
5. Integration test validates signature verification with Keycloak-signed JWT
6. Test uses Testcontainers Keycloak (26.4.2)
7. JWKS rotation handled gracefully (cache invalidation)

**Prerequisites:** Story 3.1

---

**Story 3.3: JWT Format and Signature Validation (Layers 1-2)**

As a framework developer,
I want JWT format and RS256 signature validation,
So that only properly formed and signed tokens are accepted.

**Acceptance Criteria:**
1. JwtValidationFilter.kt implements Layer 1 (format: 3-part structure) and Layer 2 (signature validation)
2. Token extraction from Authorization Bearer header
3. RS256 algorithm enforcement (reject HS256)
4. Invalid format tokens rejected with 401 and clear error message
5. Invalid signature tokens rejected with 401
6. Unit tests with Nullable Pattern for validation logic
7. Integration test validates both layers with real Keycloak tokens

**Prerequisites:** Story 3.2

---

**Story 3.4: JWT Claims Schema and Time-Based Validation (Layers 3-5)**

As a framework developer,
I want JWT claims schema and time-based validation,
So that tokens have required claims and are not expired or used before valid.

**Acceptance Criteria:**
1. Layer 3: Algorithm validation (RS256 only, hardcoded)
2. Layer 4: Claim schema validation (required: sub, iss, aud, exp, iat, tenant_id, roles)
3. Layer 5: Time-based validation (exp, iat, nbf with 30s clock skew tolerance)
4. Missing or invalid claims rejected with 401 and specific error message
5. Expired tokens rejected with 401
6. Unit tests for each validation layer
7. Integration test with intentionally invalid tokens (missing claims, expired)

**Prerequisites:** Story 3.3

---

**Story 3.5: Issuer, Audience, and Role Validation (Layers 6-8)**

As a framework developer,
I want issuer, audience, and role validation,
So that tokens are from trusted sources and contain required permissions.

**Acceptance Criteria:**
1. Layer 6: Issuer validation (expected: http://keycloak:8080/realms/eaf)
2. Layer 6: Audience validation (expected: eaf-api)
3. Layer 8: Role validation and normalization (handle Keycloak realm_access and resource_access structures)
4. RoleNormalizer.kt extracts and normalizes roles into flat list
5. @PreAuthorize annotations work with normalized roles
6. Tokens from wrong issuer rejected with 401
7. Property-based tests for role normalization edge cases (nested structures, missing keys)
8. Fuzz test for role extraction (Jazzer)

**Prerequisites:** Story 3.4

---

**Story 3.6: Redis Revocation Cache (Layer 7)**

As a framework developer,
I want JWT revocation checking with Redis blacklist cache,
So that revoked tokens cannot be used even before expiration.

**Acceptance Criteria:**
1. Redis 7.2 dependency added to framework/security
2. RedisRevocationStore.kt implements revocation check and storage
3. Layer 7: Revocation validation queries Redis for token JTI (JWT ID)
4. Revoked tokens stored with 10-minute TTL (matching token lifetime)
5. Revocation API endpoint: POST /auth/revoke (admin only)
6. Integration test validates: revoke token → subsequent requests rejected with 401
7. Redis unavailable fallback: skip revocation check with warning log (graceful degradation)
8. Revocation metrics emitted (revocation_check_duration, cache_hit_rate)

**Prerequisites:** Story 3.5

---

**Story 3.7: User Validation and Injection Detection (Layers 9-10)**

As a framework developer,
I want user existence validation and SQL/XSS injection detection in JWT claims,
So that tokens reference valid users and don't contain malicious payloads.

**Acceptance Criteria:**
1. Layer 9 (optional): User validation - check user exists and is active (configurable, performance trade-off)
2. Layer 10: Injection detection - regex patterns for SQL/XSS in all string claims
3. Invalid users rejected with 401
4. Injection patterns detected and rejected with 400 Bad Request
5. Fuzz test with Jazzer targets injection detection (SQL patterns, XSS payloads)
6. Performance impact measured (<5ms per request)
7. User validation can be disabled via configuration for performance

**Prerequisites:** Story 3.6

---

**Story 3.8: Complete 10-Layer JWT Validation Integration**

As a framework developer,
I want all 10 JWT validation layers integrated into a single filter chain,
So that every API request passes through comprehensive security validation.

**Acceptance Criteria:**
1. JwtValidationFilter.kt orchestrates all 10 layers in sequence
2. Validation failure at any layer short-circuits (fails fast)
3. Successful validation populates Spring SecurityContext
4. Validation metrics emitted per layer (validation_layer_duration, validation_failures_by_layer)
5. Integration test validates all 10 layers with comprehensive scenarios
6. Performance validated: <50ms total validation time
7. All 10 layers documented in docs/reference/jwt-validation.md

**Prerequisites:** Story 3.7

---

**Story 3.9: Role-Based Access Control on API Endpoints**

As a framework developer,
I want role-based authorization on Widget API endpoints,
So that only users with correct roles can perform operations.

**Acceptance Criteria:**
1. Widget API endpoints annotated with @PreAuthorize("hasRole('WIDGET_ADMIN')")
2. Keycloak realm configured with roles: WIDGET_ADMIN, WIDGET_VIEWER
3. Test users created with different role assignments
4. Integration test validates: WIDGET_ADMIN can create/update, WIDGET_VIEWER can only read
5. Unauthorized access returns 403 Forbidden with RFC 7807 error
6. Role requirements documented in OpenAPI spec
7. Authorization test suite covers all permission combinations

**Prerequisites:** Story 3.8

---

**Story 3.10: Testcontainers Keycloak for Integration Tests**

As a framework developer,
I want Keycloak Testcontainer provisioned for security integration tests,
So that authentication flows are tested against real Keycloak instance.

**Acceptance Criteria:**
1. Testcontainers Keycloak 26.4.2 configured in test dependencies
2. KeycloakTestContainer.kt utility creates container with realm import
3. Test realm includes: users (admin, viewer), roles, client configuration
4. Container reuse enabled for performance (start once per test class)
5. Integration tests use container-generated JWTs for authentication
6. Container startup time <30 seconds
7. All security integration tests pass using Testcontainers Keycloak

**Prerequisites:** Story 3.8

---

**Story 3.11: Custom ppc64le Keycloak Docker Image**

As a framework developer,
I want custom Keycloak Docker image built for ppc64le architecture,
So that EAF supports all required processor architectures (amd64, arm64, ppc64le).

**Acceptance Criteria:**
1. docker/keycloak/Dockerfile.ppc64le created (UBI9-based build)
2. Multi-stage build: UBI9 → Maven build → Runtime image
3. Build script: scripts/build-keycloak-ppc64le.sh automates image creation
4. Image tested on ppc64le emulation (QEMU) or real hardware if available
5. Image pushed to container registry with tag: keycloak:26.4.2-ppc64le
6. docker-compose.ppc64le.yml variant uses custom image
7. Build process documented in docs/reference/multi-arch-builds.md
8. Quarterly rebuild schedule documented (align with Keycloak releases)

**Prerequisites:** Story 3.1

---

**Story 3.12: Security Fuzz Testing with Jazzer**

As a framework developer,
I want fuzz tests for JWT validation components,
So that security vulnerabilities and edge cases are discovered automatically.

**Acceptance Criteria:**
1. Jazzer 0.25.1 dependency added to framework/security
2. Fuzz tests created in fuzzTest/kotlin/ source set:
   - JwtFormatFuzzer.kt (fuzzes token format parsing)
   - TokenExtractorFuzzer.kt (fuzzes Bearer token extraction)
   - RoleNormalizationFuzzer.kt (fuzzes role claim structures)
3. Each fuzz test runs 5 minutes (total 15 minutes for 3 security targets)
4. Corpus caching enabled for regression prevention
5. Fuzz tests integrated into nightly CI/CD pipeline
6. All fuzz tests pass without crashes or DoS conditions
7. Discovered vulnerabilities documented and fixed

**Prerequisites:** Story 3.8

---

## Epic 4: Multi-Tenancy & Data Isolation

**Expanded Goal:**
Epic 4 implements comprehensive multi-tenancy with defense-in-depth isolation through three layers: Layer 1 extracts tenant context from JWT claims into ThreadLocal storage, Layer 2 validates tenant context in command handlers, and Layer 3 enforces PostgreSQL Row-Level Security policies at the database level. This epic includes ThreadLocal context propagation to async Axon event processors, cross-tenant leak detection and monitoring, per-tenant resource quotas with automatic throttling, and LitmusKt concurrency testing to prevent race conditions in tenant context management. The 3-layer approach provides fail-closed security where missing tenant context immediately rejects requests, ensuring absolute data isolation between tenants.

**Estimated Stories:** 10

---

**Story 4.1: TenantContext and ThreadLocal Management**

As a framework developer,
I want ThreadLocal-based tenant context storage,
So that tenant ID is available throughout request processing without parameter passing.

**Acceptance Criteria:**
1. framework/multi-tenancy module created
2. TenantId.kt value object with validation
3. TenantContext.kt manages ThreadLocal storage with stack-based context
4. TenantContextHolder.kt provides static access (get/set/clear methods)
5. WeakReference used for memory safety (prevent ThreadLocal leaks)
6. Unit tests validate: set context → retrieve → clear
7. Thread isolation validated (context not shared between threads)
8. Context cleared after request completion (filter cleanup)

**Prerequisites:** Epic 3 complete

---

**Story 4.2: TenantContextFilter - Layer 1 Tenant Extraction**

As a framework developer,
I want a servlet filter that extracts tenant_id from JWT and populates TenantContext,
So that tenant context is available for all subsequent processing (Layer 1).

**Acceptance Criteria:**
1. TenantContextFilter.kt created as @Component with @Order(Ordered.HIGHEST_PRECEDENCE + 10)
2. Filter extracts tenant_id from JWT claim (after JWT validation in Epic 3)
3. TenantContext.set(tenantId) populates ThreadLocal
4. Missing tenant_id claim rejects request with 400 Bad Request
5. Filter ensures cleanup in finally block (TenantContext.clear())
6. Integration test validates tenant extraction from real Keycloak JWT
7. Metrics emitted: tenant_context_extraction_duration, missing_tenant_failures

**Prerequisites:** Story 4.1, Epic 3 complete

---

**Story 4.3: Axon Command Interceptor - Layer 2 Tenant Validation**

As a framework developer,
I want Axon command interceptor that validates tenant context matches aggregate,
So that commands cannot modify aggregates from other tenants (Layer 2).

**Acceptance Criteria:**
1. TenantValidationInterceptor.kt implements CommandHandlerInterceptor
2. Interceptor validates: TenantContext.get() matches command.tenantId
3. All commands must include tenantId field
4. Mismatch rejects command with TenantIsolationException
5. Missing context rejects command (fail-closed)
6. Integration test validates: tenant A cannot modify tenant B aggregates
7. Validation metrics: tenant_validation_failures, tenant_mismatch_attempts

**Prerequisites:** Story 4.2

---

**Story 4.4: PostgreSQL Row-Level Security Policies - Layer 3**

As a framework developer,
I want PostgreSQL RLS policies enforcing tenant isolation at database level,
So that even SQL injection or bugs cannot breach tenant boundaries (Layer 3).

**Acceptance Criteria:**
1. Flyway migration V004__rls_policies.sql enables RLS on all tenant-scoped tables
2. RLS policies created: widget_view table requires tenant_id = current_setting('app.tenant_id')
3. PostgreSQL session variable set by JooqConfiguration before queries
4. RLS policies tested: attempt cross-tenant query → returns empty result
5. Integration test validates Layer 3 blocks unauthorized access
6. Performance impact measured (<2ms overhead per query)
7. RLS policies documented in docs/reference/multi-tenancy.md

**Prerequisites:** Story 4.3

---

**Story 4.5: Tenant Context Propagation to Async Event Processors**

As a framework developer,
I want tenant context propagated to async Axon event processors,
So that projection updates and event handlers have tenant context available.

**Acceptance Criteria:**
1. AxonTenantInterceptor.kt implements EventMessageHandlerInterceptor
2. Interceptor extracts tenant_id from event metadata
3. TenantContext.set(tenantId) before event handler execution
4. Context cleared after handler completion
5. Event metadata enriched with tenant_id during command processing
6. Integration test validates: dispatch command → event handler has tenant context
7. Async event processors (TrackingEventProcessor) receive correct context

**Prerequisites:** Story 4.4

---

**Story 4.6: Multi-Tenant Widget Demo Enhancement**

As a framework developer,
I want Widget aggregate enhanced with tenant context validation,
So that the demo application demonstrates multi-tenancy correctly.

**Acceptance Criteria:**
1. Widget.kt commands include tenantId field
2. CreateWidgetCommand includes tenant_id from TenantContext
3. Command handler validates tenant context (Layer 2)
4. Widget events include tenant_id in metadata
5. widget_view projection table includes tenant_id column
6. Integration test creates widgets for multiple tenants
7. Cross-tenant access test validates isolation (tenant A cannot see tenant B widgets)
8. All Widget tests pass with tenant context

**Prerequisites:** Story 4.5

---

**Story 4.7: Tenant Isolation Integration Test Suite**

As a framework developer,
I want comprehensive tenant isolation tests validating all 3 layers,
So that I can prove multi-tenancy security is bulletproof.

**Acceptance Criteria:**
1. TenantIsolationIntegrationTest.kt validates all 3 layers
2. Test scenarios:
   - Layer 1: Missing tenant_id claim → 400 Bad Request
   - Layer 2: Command tenant mismatch → TenantIsolationException
   - Layer 3: Direct SQL query bypassing app → RLS blocks access
3. Cross-tenant attack scenarios tested (JWT with wrong tenant_id)
4. Test uses multiple Keycloak users with different tenant_id claims
5. All isolation tests pass
6. Test execution time <3 minutes
7. Test documented as security validation reference

**Prerequisites:** Story 4.6

---

**Story 4.8: Tenant Context Leak Detection and Monitoring**

As a framework developer,
I want monitoring and alerting for tenant context leaks,
So that isolation violations are detected immediately.

**Acceptance Criteria:**
1. Metrics emitted: tenant_context_missing, tenant_context_mismatch, cross_tenant_access_attempts
2. Security audit log for all tenant isolation violations
3. Structured JSON log includes: violation_type, user_id, attempted_tenant, actual_tenant, trace_id
4. Alerting rules configured in Prometheus for isolation violations
5. Dashboard query templates for tenant isolation monitoring
6. Integration test validates metrics are emitted on violations
7. Leak detection documented in docs/reference/security-monitoring.md

**Prerequisites:** Story 4.7

---

**Story 4.9: Per-Tenant Resource Quotas**

As a framework developer,
I want per-tenant resource quotas (event rate, storage, query limits),
So that one tenant cannot impact system performance for others.

**Acceptance Criteria:**
1. TenantQuotaManager.kt implements quota enforcement
2. Quotas configurable per tenant: max_events_per_second, max_storage_mb, max_query_complexity
3. Rate limiting using token bucket algorithm (Bucket4j or similar)
4. Quota exceeded returns 429 Too Many Requests with retry-after header
5. Quota usage tracked in Prometheus metrics per tenant
6. Hot tenant detection (sustained quota violations)
7. Integration test validates quota enforcement

**Prerequisites:** Story 4.8

---

**Story 4.10: LitmusKt Concurrency Testing for TenantContext**

As a framework developer,
I want concurrency stress tests for TenantContext ThreadLocal management,
So that race conditions and memory model violations are detected.

**Acceptance Criteria:**
1. LitmusKt dependency added (version TBD, JetBrains Research)
2. Concurrency tests in litmusTest/kotlin/ source set:
   - TenantContextIsolationTest.kt (validates no cross-thread context leakage)
   - EventProcessorPropagationTest.kt (validates async propagation correctness)
   - ConnectionPoolContextTest.kt (validates context with pooled connections)
3. Tests run with multiple thread scenarios (2, 4, 8, 16 threads)
4. Memory model violations detected and prevented
5. Tests integrated into nightly CI/CD
6. All concurrency tests pass without race conditions
7. LitmusKt testing documented

**Prerequisites:** Story 4.5

---

## Epic 5: Observability & Monitoring

**Expanded Goal:**
Epic 5 implements comprehensive observability infrastructure enabling production debugging and performance monitoring through structured JSON logging with automatic context injection (trace_id, tenant_id, service_name), Prometheus metrics collection with Micrometer instrumentation for all system components (JVM, HTTP, Axon processing), OpenTelemetry distributed tracing with W3C Trace Context propagation across REST and Axon messages, PII masking for GDPR compliance, and enforced performance limits ensuring <1% application overhead. This epic delivers production-grade observability that enables rapid issue diagnosis, performance optimization, and compliance with security audit requirements.

**Estimated Stories:** 8

---

**Story 5.1: Structured JSON Logging with Logback**

As a framework developer,
I want structured JSON logging configured with Logback and Logstash encoder,
So that logs are machine-parsable and queryable in log aggregation systems.

**Acceptance Criteria:**
1. framework/observability module created with Logback and Logstash encoder dependencies
2. logback-spring.xml configures JSON logging format
3. Log entries include mandatory fields: timestamp, level, logger, message, service_name, thread
4. ConsoleAppender for local development (pretty-printed JSON)
5. FileAppender configuration for production (rotated daily)
6. Integration test validates JSON structure and required fields
7. Log output validated as valid JSON

**Prerequisites:** Epic 4 complete

---

**Story 5.2: Automatic Context Injection (trace_id, tenant_id)**

As a framework developer,
I want automatic injection of trace_id and tenant_id into all log entries,
So that logs can be correlated across distributed requests and filtered by tenant.

**Acceptance Criteria:**
1. ContextEnricher.kt implements Logback MDC (Mapped Diagnostic Context)
2. trace_id extracted from OpenTelemetry Span and added to MDC
3. tenant_id extracted from TenantContext and added to MDC
4. All log entries automatically include trace_id and tenant_id fields
5. MDC cleanup after request completion
6. Integration test validates: make request → all logs include trace_id and tenant_id
7. Null safety when tenant_id or trace_id unavailable (log field omitted, not null)

**Prerequisites:** Story 5.1

---

**Story 5.3: PII Masking for GDPR Compliance**

As a framework developer,
I want automatic PII masking in log messages,
So that sensitive data is not exposed in logs (GDPR compliance).

**Acceptance Criteria:**
1. PiiMaskingFilter.kt implements Logback filter
2. Regex patterns detect and mask: email addresses, phone numbers, credit card numbers
3. Masking format: email → e***@example.com, phone → ***-***-1234
4. Configurable PII patterns (extensible for custom data types)
5. Unit tests validate masking for all PII types
6. Integration test validates: log message with email → email masked in output
7. Performance impact <1ms per log entry
8. PII masking documented in docs/reference/security-logging.md

**Prerequisites:** Story 5.2

---

**Story 5.4: Prometheus Metrics with Micrometer**

As a framework developer,
I want Prometheus metrics collection for all system components,
So that I can monitor performance, resource usage, and business metrics.

**Acceptance Criteria:**
1. Micrometer 1.15.5 dependency added to framework/observability
2. MicrometerConfiguration.kt configures Prometheus registry
3. Spring Boot Actuator endpoint /actuator/prometheus exposed
4. Default metrics enabled: JVM (memory, GC, threads), HTTP (request duration, status codes), Axon (command/event processing)
5. All metrics tagged with: service_name, tenant_id (where applicable)
6. Custom metrics API provided (MeterRegistry injectable)
7. Integration test validates metrics endpoint returns Prometheus format
8. Metrics scraped successfully by Prometheus container

**Prerequisites:** Story 5.1

---

**Story 5.5: OpenTelemetry Distributed Tracing**

As a framework developer,
I want OpenTelemetry distributed tracing with automatic instrumentation,
So that I can trace requests across REST API and async Axon event processing.

**Acceptance Criteria:**
1. OpenTelemetry 1.55.0 API/SDK dependencies added
2. OpenTelemetryConfiguration.kt configures auto-instrumentation
3. W3C Trace Context propagation enabled (traceparent header)
4. Automatic spans created for: HTTP requests, Axon commands, Axon events, database queries
5. trace_id extracted and injected into logs (Story 5.2 integration)
6. Trace export configured (OTLP exporter, endpoint configurable)
7. Integration test validates: REST call → command → event → full trace captured
8. Trace spans include tenant_id as attribute

**Prerequisites:** Story 5.2, Story 5.4

---

**Story 5.6: Observability Performance Limits and Backpressure**

As a framework developer,
I want enforced performance limits on observability components,
So that logging/metrics/tracing never impact application performance >1%.

**Acceptance Criteria:**
1. Log rotation configured: daily rotation, 7-day retention, max 1GB per day
2. Intelligent trace sampling: 100% errors, 10% success (configurable)
3. Metric collection performance validated: <1% CPU overhead
4. Backpressure handling: drop telemetry data when systems unavailable (don't block application)
5. Performance test validates: observability overhead <1% under load
6. Circuit breaker for telemetry exports (fail-open on errors)
7. Performance limits documented in architecture.md

**Prerequisites:** Story 5.5

---

**Story 5.7: Widget Demo Observability Enhancement**

As a framework developer,
I want Widget demo enhanced with custom business metrics and structured logging,
So that the demo demonstrates observability capabilities.

**Acceptance Criteria:**
1. Widget aggregate emits custom metrics: widget_created_total, widget_published_total, widget_processing_duration
2. Widget API logs structured messages with business context
3. Widget command/event processing traced with OpenTelemetry spans
4. Integration test validates: create widget → metrics incremented, logs emitted, traces captured
5. Prometheus metrics visible at /actuator/prometheus
6. Log correlation validated (all logs for single request share trace_id)
7. Custom metrics documented as example pattern

**Prerequisites:** Story 5.6

---

**Story 5.8: Observability Integration Test Suite**

As a framework developer,
I want comprehensive observability integration tests,
So that I can validate logging, metrics, and tracing work correctly under various scenarios.

**Acceptance Criteria:**
1. ObservabilityIntegrationTest.kt validates all three pillars (logs, metrics, traces)
2. Test scenarios:
   - Successful request → logs, metrics, traces captured
   - Failed request → error logged, error metrics incremented, trace marked as error
   - Multi-tenant request → tenant_id in all observability data
3. Log parsing test validates JSON structure
4. Metrics scraping test validates Prometheus format
5. Trace export test validates OTLP format
6. All observability tests pass
7. Test execution time <2 minutes

**Prerequisites:** Story 5.7

---

## Epic 6: Workflow Orchestration

**Expanded Goal:**
Epic 6 integrates Flowable BPMN 7.2.0 as the workflow orchestration engine, replacing legacy Dockets functionality with industry-standard BPMN 2.0 workflows. This epic implements bidirectional integration between Flowable and Axon Framework enabling BPMN service tasks to dispatch Axon commands and Axon event handlers to signal BPMN process instances, provides an Ansible adapter for legacy automation migration, implements compensating transaction patterns for workflow error handling, creates "Dockets Pattern" BPMN template for common migration scenarios, and delivers workflow recovery tools including dead letter queues and debugging utilities. This epic enables complex business processes and orchestration while maintaining tenant-awareness and integration with the event-sourced domain model.

**Estimated Stories:** 10

---

**Story 6.1: Flowable BPMN Engine Configuration**

As a framework developer,
I want Flowable BPMN engine integrated with dedicated PostgreSQL schema,
So that I can define and execute business workflows.

**Acceptance Criteria:**
1. framework/workflow module created with Flowable 7.2.0 dependencies
2. FlowableConfiguration.kt configures ProcessEngine, RuntimeService, TaskService
3. Dedicated PostgreSQL schema for Flowable tables (ACT_* tables)
4. Flyway migration creates Flowable schema
5. Flowable engine starts successfully on application startup
6. Integration test deploys and starts simple BPMN process
7. Flowable process tables visible in PostgreSQL

**Prerequisites:** Epic 5 complete

---

**Story 6.2: Tenant-Aware Process Engine**

As a framework developer,
I want Flowable processes to be tenant-aware,
So that workflow instances are isolated per tenant.

**Acceptance Criteria:**
1. TenantAwareProcessEngine.kt wraps RuntimeService
2. All process starts include tenant_id as process variable
3. Process instances queryable by tenant_id
4. Integration test validates: start process → tenant_id stored in process variables
5. Cross-tenant process access blocked (tenant A cannot query tenant B processes)
6. Tenant context available in BPMN service tasks and listeners
7. Tenant-aware queries documented

**Prerequisites:** Story 6.1, Epic 4 complete

---

**Story 6.3: Axon Command Gateway Delegate (BPMN → Axon)**

As a framework developer,
I want BPMN service tasks to dispatch Axon commands,
So that workflows can trigger domain operations (Flowable → Axon direction).

**Acceptance Criteria:**
1. AxonCommandGatewayDelegate.kt implements JavaDelegate for BPMN service tasks
2. Delegate extracts command details from process variables
3. CommandGateway.send() dispatches command to Axon
4. Command results stored back in process variables
5. Tenant_id propagated from process variable to command
6. Integration test: BPMN service task → dispatches CreateWidgetCommand → Widget created
7. Error handling: command rejection triggers BPMN error boundary event
8. Delegate usage documented with BPMN example

**Prerequisites:** Story 6.2

---

**Story 6.4: Flowable Event Listener (Axon → BPMN)**

As a framework developer,
I want Axon event handlers to signal Flowable process instances,
So that workflows can react to domain events (Axon → Flowable direction).

**Acceptance Criteria:**
1. FlowableEventListener.kt with @EventHandler methods
2. Event handlers call RuntimeService.signalEventReceived() to signal BPMN processes
3. Signal correlation using process instance ID or business key
4. Tenant_id from event metadata used to filter process instances
5. Integration test: Axon WidgetPublishedEvent → signals waiting BPMN process
6. Process continues after signal received
7. Bidirectional integration validated: BPMN ↔ Axon ↔ BPMN
8. Signal patterns documented

**Prerequisites:** Story 6.3

---

**Story 6.5: Widget Approval Workflow (BPMN Demo)**

As a framework developer,
I want a Widget approval workflow demonstrating Flowable-Axon integration,
So that I have a working example of orchestrated business processes.

**Acceptance Criteria:**
1. widget-approval.bpmn20.xml process definition created
2. Workflow: Start → Axon: CreateWidget → Wait for manual approval → Axon: PublishWidget → End
3. Service tasks use AxonCommandGatewayDelegate
4. Signal event receives WidgetApprovedEvent from Axon
5. Process deployed to Flowable engine
6. Integration test executes complete workflow end-to-end
7. Workflow visualized in Flowable UI (if available) or documented with diagram
8. Approval workflow documented as reference example

**Prerequisites:** Story 6.4

---

**Story 6.6: Ansible Adapter for Legacy Migration**

As a framework developer,
I want an Ansible adapter allowing BPMN service tasks to execute Ansible playbooks,
So that legacy Dockets automation can be migrated to Flowable.

**Acceptance Criteria:**
1. AnsibleAdapter.kt implements JavaDelegate
2. JSch 0.2.18 dependency for SSH execution
3. Adapter executes Ansible playbooks from BPMN with process variable parameters
4. Playbook execution results captured and stored in process variables
5. Error handling: playbook failures trigger BPMN error events
6. Integration test executes sample Ansible playbook from BPMN
7. Security: SSH keys and credentials managed securely (not in process variables)
8. Ansible adapter usage documented

**Prerequisites:** Story 6.3

---

**Story 6.7: Dockets Pattern BPMN Template**

As a framework developer,
I want a "Dockets Pattern" BPMN template for common migration scenarios,
So that legacy Dockets workflows can be migrated to Flowable systematically.

**Acceptance Criteria:**
1. dockets-pattern.bpmn20.xml template created in framework/workflow/src/main/resources/processes/
2. Template includes: PRESCRIPT (Ansible), Main Task (Axon command), POSTSCRIPT (Ansible), Error Handler (compensating transaction)
3. Template documented with migration guide
4. Example migration: legacy Dockets workflow → Flowable using template
5. Template tested with sample workflow
6. Migration patterns documented in docs/how-to/migrate-dockets-workflows.md
7. Template parameterizable (replace placeholders with actual commands/playbooks)

**Prerequisites:** Story 6.6

---

**Story 6.8: Compensating Transactions for Workflow Errors**

As a framework developer,
I want compensating transaction support in BPMN workflows,
So that partial failures can be rolled back gracefully.

**Acceptance Criteria:**
1. BPMN error boundary events configured with compensation handlers
2. Compensation patterns documented: undo commands, reverse operations, cleanup tasks
3. Example: Widget creation fails → compensation deletes partial data
4. Integration test validates: trigger error → compensation executes → state restored
5. Saga pattern integration for multi-step compensations
6. Compensating transactions logged and traced
7. Compensation patterns documented in docs/reference/workflow-patterns.md

**Prerequisites:** Story 6.5

---

**Story 6.9: Workflow Dead Letter Queue and Recovery**

As a framework developer,
I want dead letter queue for failed workflow messages,
So that Flowable-Axon bridge failures can be investigated and retried.

**Acceptance Criteria:**
1. Dead letter queue table created for failed Axon commands from BPMN
2. Failed commands stored with: process instance ID, error details, retry count
3. Manual retry API: POST /workflow/dlq/:id/retry
4. Automatic retry with exponential backoff (configurable)
5. Max retry limit (default: 3) before manual intervention required
6. Integration test validates: command fails → DLQ → manual retry → success
7. DLQ monitoring metrics and alerts

**Prerequisites:** Story 6.3

---

**Story 6.10: Workflow Debugging and Monitoring Tools**

As a framework developer,
I want workflow debugging utilities,
So that I can inspect and troubleshoot running BPMN processes.

**Acceptance Criteria:**
1. Workflow inspection API: GET /workflow/instances (list active processes)
2. Process detail API: GET /workflow/instances/:id (variables, current activity, history)
3. Manual signal API: POST /workflow/instances/:id/signal (for stuck processes)
4. Workflow metrics: process_started_total, process_completed_total, process_duration, active_instances
5. Integration test validates all debugging APIs
6. Workflow debugging documented in docs/how-to/debug-workflows.md
7. Flowable UI integration guide (optional, for advanced users)

**Prerequisites:** Story 6.9

---

## Epic 7: Scaffolding CLI & Developer Tooling

**Expanded Goal:**
Epic 7 delivers the scaffolding CLI that enables rapid, pattern-compliant development by generating production-ready code from Mustache templates, eliminating 70-80% of boilerplate effort. Built with Picocli 4.7.7, the CLI provides commands for generating Spring Modulith modules, complete CQRS/ES aggregates (aggregate, commands, events, handlers, projections, tests), REST API controllers with OpenAPI specs, jOOQ projection event handlers, and shadcn-admin-kit UI components. All generated code immediately passes quality gates (ktlint, Detekt, Konsist) and includes comprehensive tests, enabling developers to scaffold new features in minutes rather than hours while maintaining absolute architectural consistency.

**Estimated Stories:** 12

---

**Story 7.1: CLI Framework with Picocli**

As a framework developer,
I want a CLI framework built with Picocli,
So that I have a modern, annotation-based foundation for scaffold commands.

**Acceptance Criteria:**
1. tools/eaf-cli module created with Picocli 4.7.7 dependency
2. EafCli.kt main class with @Command annotation
3. Subcommands structure: scaffold (parent), version, help
4. CLI executable via ./gradlew :tools:eaf-cli:run
5. Help output shows available commands
6. Version command displays EAF version
7. Unit tests validate CLI command parsing
8. CLI builds as standalone executable JAR

**Prerequisites:** Epic 6 complete

---

**Story 7.2: Mustache Template Engine Integration**

As a framework developer,
I want Mustache template engine for code generation,
So that I can create logic-less, maintainable code templates.

**Acceptance Criteria:**
1. Mustache 0.9.14 dependency added to tools/eaf-cli
2. CodeGenerator.kt utility for template processing
3. Template loading from classpath resources (templates/ directory)
4. Variable substitution tested ({{variableName}} replacement)
5. Template partials supported (reusable template components)
6. Unit tests validate template rendering with sample data
7. Template syntax documented in docs/reference/cli-templates.md

**Prerequisites:** Story 7.1

---

**Story 7.3: scaffold module Command**

As a framework developer,
I want `eaf scaffold module <name>` command to generate Spring Modulith modules,
So that I can create new framework or product modules with correct structure.

**Acceptance Criteria:**
1. ScaffoldModuleCommand.kt implements module scaffolding
2. Command parameters: module name, parent directory (framework/products)
3. Generates: build.gradle.kts, src/main/kotlin structure, module-info.java (if needed)
4. Module template includes convention plugin application
5. Generated module compiles successfully
6. Spring Modulith recognizes generated module
7. Integration test validates: scaffold module → compiles → Konsist passes
8. Command documented with examples

**Prerequisites:** Story 7.2

---

**Story 7.4: Aggregate Template (Commands, Events, Handlers)**

As a framework developer,
I want Mustache templates for complete aggregate vertical slices,
So that scaffold aggregate command can generate all required components.

**Acceptance Criteria:**
1. Templates created in tools/eaf-cli/src/main/resources/templates/aggregate/:
   - Aggregate.kt.mustache (aggregate root with @AggregateIdentifier)
   - Command.kt.mustache (command DTOs)
   - Event.kt.mustache (event DTOs)
   - AggregateTest.kt.mustache (Axon Test Fixtures)
2. Templates include: package declarations, imports, KDoc, tenant_id fields
3. Generated code follows naming patterns from architecture.md
4. Templates tested with sample data
5. Generated code passes ktlint and Detekt immediately
6. Templates documented with variable reference

**Prerequisites:** Story 7.2

---

**Story 7.5: scaffold aggregate Command**

As a framework developer,
I want `eaf scaffold aggregate <name>` command to generate complete CQRS vertical slices,
So that I can create new aggregates in minutes with all boilerplate eliminated.

**Acceptance Criteria:**
1. ScaffoldAggregateCommand.kt implements aggregate scaffolding
2. Command parameters: aggregate name, module (default: products/), commands list (optional)
3. Generates: Aggregate.kt, Commands, Events, AggregateTest.kt using templates
4. Pluralization logic for aggregate names (Widget → Widgets)
5. Generated code compiles and tests pass immediately
6. Integration test: scaffold aggregate Order → compiles → tests pass
7. Generated aggregate follows DDD base classes from framework/core
8. Command usage: `eaf scaffold aggregate Order --commands=Create,Update,Cancel`

**Prerequisites:** Story 7.4

---

**Story 7.6: scaffold api-resource Command**

As a framework developer,
I want `eaf scaffold api-resource <name>` command to generate REST controllers,
So that I can expose aggregates via HTTP API with OpenAPI documentation.

**Acceptance Criteria:**
1. ScaffoldApiResourceCommand.kt implements API scaffolding
2. Templates: Controller.kt.mustache, Request.kt.mustache, Response.kt.mustache
3. Generated controller includes: CRUD endpoints, CommandGateway/QueryGateway usage, @OpenAPI annotations
4. Request/Response DTOs with validation annotations
5. Generated code compiles and passes quality gates
6. Integration test: scaffold api-resource Widget → compiles → Swagger UI shows endpoints
7. Generated endpoints follow REST conventions (POST, GET, PUT, DELETE)
8. Command usage: `eaf scaffold api-resource Widget`

**Prerequisites:** Story 7.4

---

**Story 7.7: scaffold projection Command**

As a framework developer,
I want `eaf scaffold projection <name>` command to generate projection event handlers,
So that I can create read models from events with type-safe jOOQ queries.

**Acceptance Criteria:**
1. ScaffoldProjectionCommand.kt implements projection scaffolding
2. Templates: Projection.kt.mustache, EventHandler.kt.mustache, migration.sql.mustache
3. Generated event handler includes @EventHandler for all aggregate events
4. Generated Flyway migration creates projection table
5. jOOQ integration configured (code generation after migration)
6. Generated code compiles after running migration
7. Integration test: scaffold projection Widget → migration → jOOQ generation → compiles
8. Command usage: `eaf scaffold projection Widget`

**Prerequisites:** Story 7.4

---

**Story 7.8: scaffold ra-resource Command (shadcn-admin-kit UI)**

As a framework developer,
I want `eaf scaffold ra-resource <name>` command to generate shadcn-admin-kit UI components,
So that I can create operator portal pages for CRUD operations.

**Acceptance Criteria:**
1. ScaffoldRaResourceCommand.kt implements UI scaffolding
2. Templates in templates/ra-resource/: List.tsx.mustache, Edit.tsx.mustache, Create.tsx.mustache, Show.tsx.mustache
3. Generated components use shadcn/ui primitives and react-admin hooks
4. Generated components connect to REST API via data provider
5. TypeScript interfaces generated for API DTOs
6. Generated code passes ESLint and TypeScript checks
7. Integration test: scaffold ra-resource Widget → compiles → renders in browser
8. Command usage: `eaf scaffold ra-resource Widget`

**Prerequisites:** Story 7.6

---

**Story 7.9: Template Validation and Quality Gate Compliance**

As a framework developer,
I want all generated code to immediately pass quality gates,
So that developers can use scaffolded code without manual fixes.

**Acceptance Criteria:**
1. All templates validated: generated code passes ktlint formatting
2. Generated code passes Detekt static analysis
3. Generated code passes Konsist architecture validation
4. Generated tests compile and pass
5. Pre-generation validation catches invalid names (reserved keywords, invalid characters)
6. Post-generation validation runs quality gates automatically
7. Integration test: scaffold aggregate → run ./gradlew check → all gates pass
8. Template quality CI/CD pipeline validates templates on every change

**Prerequisites:** Story 7.5, Story 7.6, Story 7.7, Story 7.8

---

**Story 7.10: CLI Testing and Validation**

As a framework developer,
I want comprehensive CLI tests,
So that scaffold commands work reliably across different scenarios.

**Acceptance Criteria:**
1. Unit tests for all scaffold commands (module, aggregate, api-resource, projection, ra-resource)
2. Integration tests validate end-to-end generation workflow
3. Edge case tests: invalid names, existing files, missing modules
4. Test validates: generated code compiles, tests pass, quality gates pass
5. CLI error handling tested (clear error messages)
6. Test execution time <2 minutes
7. CLI test coverage >85%

**Prerequisites:** Story 7.9

---

**Story 7.11: CLI Installation and Distribution**

As a framework developer,
I want CLI installable as global command,
So that developers can use `eaf` command from anywhere.

**Acceptance Criteria:**
1. Installation script: scripts/install-cli.sh (symlinks to PATH)
2. CLI executable: ./gradlew :tools:eaf-cli:installDist creates bin/eaf
3. Shadow JAR build for standalone distribution
4. `eaf` command works from any directory
5. CLI version check: `eaf version` displays current version
6. Uninstall script: scripts/uninstall-cli.sh
7. Installation documented in docs/getting-started/01-install-cli.md
8. Multi-platform support (Linux, macOS, Windows via gradlew.bat)

**Prerequisites:** Story 7.10

---

**Story 7.12: CLI Documentation and Examples**

As a framework developer,
I want comprehensive CLI documentation with examples,
So that developers understand how to use all scaffold commands.

**Acceptance Criteria:**
1. docs/reference/cli-commands.md documents all commands with parameters
2. docs/tutorials/using-the-cli.md provides step-by-step tutorial
3. Each command includes usage examples and output samples
4. Template variable reference documented
5. Troubleshooting guide for common CLI issues
6. Generated code examples in docs/examples/
7. CLI help text comprehensive and accurate (`eaf scaffold --help`)

**Prerequisites:** Story 7.11

---

## Epic 8: Code Quality & Architectural Alignment

**Expanded Goal:**
Epic 8 performs systematic resolution of architectural deviations accumulated during rapid development of Epics 1-7, implements advanced testing layers including LitmusKt concurrency testing for race condition detection, Pitest mutation testing for test effectiveness validation, enforces Constitutional TDD compliance across all framework modules through Git hooks and CI/CD integration, and validates that all code adheres to architecture.md specifications. This epic delivers production-grade quality assurance through comprehensive architectural alignment review, implementation of 7-layer testing defense (Static → Unit → Integration → Property → Fuzz → Concurrency → Mutation), and establishment of quality enforcement mechanisms ensuring continuous compliance.

**Estimated Stories:** 10

---

**Story 8.1: Architectural Deviation Audit**

As a framework developer,
I want a systematic audit comparing implementation to architecture.md specifications,
So that I can identify and document all deviations that accumulated during development.

**Acceptance Criteria:**
1. Audit checklist created based on architecture.md decisions (89 decisions)
2. All framework modules reviewed against architectural specifications
3. Deviations documented with: location, severity (critical/high/medium/low), resolution plan
4. Naming pattern compliance validated (files, packages, database tables)
5. Module boundary violations identified via Konsist
6. Technology version consistency validated (architecture.md vs actual dependencies)
7. Audit report generated: docs/architecture-alignment-audit-{{date}}.md
8. Deviation count and severity distribution documented

**Prerequisites:** Epic 7 complete

---

**Story 8.2: Critical Deviation Resolution**

As a framework developer,
I want all critical architectural deviations resolved,
So that implementation fully conforms to architectural decisions.

**Acceptance Criteria:**
1. All CRITICAL deviations from Story 8.1 resolved
2. Code refactored to match architectural patterns
3. Naming corrected to follow conventions (FR → functional requirement pattern)
4. Module boundaries corrected (no cyclic dependencies)
5. All critical fixes tested (unit + integration tests)
6. Konsist architecture tests pass
7. Resolution validated with re-audit
8. Zero critical deviations remain

**Prerequisites:** Story 8.1

---

**Story 8.3: High-Priority Deviation Resolution**

As a framework developer,
I want all high-priority architectural deviations resolved,
So that code quality meets production standards.

**Acceptance Criteria:**
1. All HIGH deviations from Story 8.1 resolved
2. Implementation patterns corrected (error handling, logging, date/time)
3. Missing documentation added for undocumented components
4. Test coverage gaps filled (target 85%+)
5. All high-priority fixes tested
6. Re-audit validates resolution
7. Zero high-priority deviations remain

**Prerequisites:** Story 8.2

---

**Story 8.4: LitmusKt Concurrency Testing Framework**

As a framework developer,
I want LitmusKt integrated for concurrency stress testing,
So that race conditions and memory model violations are detected.

**Acceptance Criteria:**
1. LitmusKt dependency added (latest version from JetBrains Research)
2. litmusTest/ source set created in Gradle convention plugin
3. LitmusKt configuration in build system
4. Sample concurrency test validates LitmusKt setup
5. Test execution integrated into nightly CI/CD
6. LitmusKt testing guide created in docs/how-to/concurrency-testing.md
7. LitmusKt runs successfully in CI/CD environment

**Prerequisites:** Epic 4 complete (needed for TenantContext tests)

---

**Story 8.5: Concurrency Tests for Critical Components**

As a framework developer,
I want comprehensive concurrency tests for ThreadLocal and async components,
So that race conditions are prevented in production.

**Acceptance Criteria:**
1. TenantContext concurrency tests expanded (from Epic 4 Story 4.10)
2. Event Processor propagation concurrency tests
3. Connection pool context tests
4. Redis cache concurrency tests (revocation store)
5. Distributed lock tests (if implemented)
6. All tests run with 2, 4, 8, 16 thread scenarios
7. Memory model violations detected and fixed
8. All concurrency tests pass in nightly CI/CD (~20-30 minutes)

**Prerequisites:** Story 8.4

---

**Story 8.6: Pitest Mutation Testing Configuration**

As a framework developer,
I want Pitest mutation testing integrated,
So that test effectiveness is validated (tests actually catch bugs).

**Acceptance Criteria:**
1. Pitest 1.19.0 plugin added to Gradle quality gates convention
2. Mutation testing configured for all framework modules
3. Target: 60-70% mutation coverage (realistic for deprecated Kotlin plugin)
4. Property tests excluded from mutation testing (exponential time)
5. Mutation testing runs in nightly CI/CD (~20-30 minutes)
6. Mutation report generated and archived
7. Pitest configuration documented in docs/reference/mutation-testing.md

**Prerequisites:** Epic 7 complete (all modules implemented)

---

**Story 8.7: Mutation Score Improvement**

As a framework developer,
I want improved test quality to meet mutation coverage targets,
So that tests are effective at catching real bugs.

**Acceptance Criteria:**
1. Mutation testing baseline measured for all modules
2. Weak tests identified (mutations survive)
3. Tests improved to kill surviving mutations
4. Target mutation score achieved: 60-70% across framework modules
5. Critical paths (security, multi-tenancy) achieve >75% mutation score
6. Mutation score tracked in CI/CD
7. Mutation improvement strategies documented

**Prerequisites:** Story 8.6

---

**Story 8.8: Constitutional TDD Compliance Validation**

As a framework developer,
I want validation that all production code follows Constitutional TDD (test-first),
So that TDD discipline is enforced project-wide.

**Acceptance Criteria:**
1. Git history audit validates RED-GREEN-REFACTOR commit patterns
2. All production code has corresponding tests
3. Test coverage >85% validated via Kover
4. No production code committed without tests
5. TDD violation detection in code review checklist
6. Pre-commit hooks enforce test existence (fail if new code has no tests)
7. TDD compliance report generated
8. TDD compliance metrics tracked

**Prerequisites:** Epic 1-7 complete

---

**Story 8.9: Git Hooks Enhancement and Enforcement**

As a framework developer,
I want enhanced Git hooks that enforce TDD and quality gates strictly,
So that quality issues never reach the main branch.

**Acceptance Criteria:**
1. Pre-commit hook enhanced: ktlint + test existence check (<10s)
2. Pre-push hook enhanced: Detekt + unit tests + integration tests (<5min)
3. Hooks cannot be bypassed without explicit --no-verify (discouraged, logged)
4. Hook bypass requires justification in commit message
5. CI/CD validates hooks were not bypassed
6. Hook violations logged and metrics tracked
7. Enhanced hooks documented in CONTRIBUTING.md

**Prerequisites:** Story 8.8

---

**Story 8.10: Quality Metrics Dashboard and Reporting**

As a framework developer,
I want a quality metrics dashboard tracking all quality indicators,
So that quality trends and regressions are visible.

**Acceptance Criteria:**
1. Quality metrics collected: code coverage (Kover), mutation score (Pitest), violations (ktlint, Detekt), test execution time
2. Metrics published to Prometheus
3. Quality trend analysis (compare current vs previous builds)
4. Regression detection and alerts
5. Quality dashboard queries documented for Grafana (optional visualization)
6. Metrics visible at /actuator/prometheus
7. Quality report generated in CI/CD artifacts

**Prerequisites:** Story 8.6, Story 8.7

---

## Epic 9: Golden Path Documentation

**Expanded Goal:**
Epic 9 creates comprehensive developer documentation that enables self-service learning and rapid productivity for developers new to the EAF stack. This epic delivers Getting Started guides (prerequisites, first aggregate, CQRS/ES fundamentals), tiered tutorials (simple/standard/production aggregates with 15-minute to full-day progressions), How-To guides for common tasks (validation, business rules, testing, debugging), complete reference documentation (architecture decisions, API docs, configuration), and fully working code examples in docs/examples/. This documentation MUST be completed before Epic 10 to enable Majlinda's <3 day onboarding validation, serving as the primary learning resource for all future EAF developers.

**Estimated Stories:** 14

---

**Story 9.1: Getting Started - Prerequisites and Environment Setup**

As a new EAF developer,
I want clear prerequisites and environment setup instructions,
So that I can prepare my development machine for EAF development.

**Acceptance Criteria:**
1. docs/getting-started/00-prerequisites.md created
2. Required tools documented: JDK 21 LTS, Docker Desktop, Git, IDE (IntelliJ IDEA recommended)
3. Platform-specific instructions (macOS, Linux, Windows)
4. Multi-architecture considerations (amd64, arm64, ppc64le)
5. Verification steps for each prerequisite
6. Troubleshooting section for common setup issues
7. Document tested by following on clean system

**Prerequisites:** Epic 8 complete

---

**Story 9.2: Getting Started - Your First Aggregate (15-Minute Tutorial)**

As a new EAF developer,
I want a 15-minute tutorial creating my first CQRS aggregate,
So that I can quickly understand the core CQRS pattern.

**Acceptance Criteria:**
1. docs/getting-started/01-your-first-aggregate.md created
2. Tutorial steps: Run init-dev.sh → scaffold aggregate → customize logic → write tests → run tests
3. Simple example: Counter aggregate (Increment, Decrement commands)
4. Estimated time: 15 minutes for complete tutorial
5. All code snippets tested and working
6. Screenshots/diagrams for key steps
7. Tutorial tested by non-CQRS developer (validation)
8. Success criteria: Working aggregate with passing tests

**Prerequisites:** Story 9.1

---

**Story 9.3: Understanding CQRS Fundamentals**

As a new EAF developer,
I want conceptual explanation of CQRS patterns,
So that I understand the architectural foundation before building complex features.

**Acceptance Criteria:**
1. docs/getting-started/02-understanding-cqrs.md created
2. CQRS concepts explained: Commands vs Queries, Command Handlers, Query Handlers
3. Diagrams: CQRS flow (Command → Handler → Event → Projection → Query)
4. Benefits and trade-offs of CQRS documented
5. When to use CQRS vs traditional CRUD
6. Common CQRS anti-patterns and mistakes
7. References to industry resources (Martin Fowler, Axon docs)

**Prerequisites:** Story 9.2

---

**Story 9.4: Understanding Event Sourcing Fundamentals**

As a new EAF developer,
I want conceptual explanation of Event Sourcing,
So that I understand why events are persisted instead of state.

**Acceptance Criteria:**
1. docs/getting-started/03-understanding-event-sourcing.md created
2. Event Sourcing concepts: Events as source of truth, Event Store, State Reconstruction
3. Diagrams: Event Sourcing flow, Aggregate state rebuild from events
4. Benefits: Audit trail, time-travel debugging, event replay
5. Trade-offs: Complexity, eventual consistency, schema evolution
6. Common Event Sourcing patterns and anti-patterns
7. References to industry resources (Greg Young, Axon Framework docs)

**Prerequisites:** Story 9.3

---

**Story 9.5: Axon Framework Basics**

As a new EAF developer,
I want practical guide to Axon Framework usage in EAF,
So that I can effectively use Axon's APIs and patterns.

**Acceptance Criteria:**
1. docs/getting-started/04-axon-framework-basics.md created
2. Key Axon concepts: @AggregateIdentifier, @CommandHandler, @EventSourcingHandler, @EventHandler
3. Command Gateway usage examples
4. Query Gateway usage examples
5. Axon Test Fixtures tutorial
6. Event Store interaction patterns
7. Common Axon errors and solutions
8. Links to official Axon documentation

**Prerequisites:** Story 9.4

---

**Story 9.6: Tutorial - Simple Aggregate (Milestone 1)**

As a new EAF developer,
I want step-by-step tutorial for building a simple aggregate,
So that I can achieve Milestone 1 productivity (basic CQRS competency).

**Acceptance Criteria:**
1. docs/tutorials/simple-aggregate.md created
2. Tutorial builds: Task aggregate (Create, Complete, Delete commands)
3. Steps: Scaffold → Customize → Test (Axon Fixtures) → Run
4. Estimated time: 2-3 hours
5. No multi-tenancy, no projections, no API (minimal scope)
6. All code tested and working
7. Tutorial validates Milestone 1 competency
8. Success criteria: Passing Axon Test Fixtures tests

**Prerequisites:** Story 9.5

---

**Story 9.7: Tutorial - Standard Aggregate (Milestone 2)**

As a new EAF developer,
I want tutorial for building a standard production-ready aggregate,
So that I can achieve Milestone 2 productivity (full CQRS + projections).

**Acceptance Criteria:**
1. docs/tutorials/standard-aggregate.md created
2. Tutorial builds: Order aggregate with projections, queries, and API
3. Steps: Scaffold aggregate → Scaffold projection → Scaffold API → Add multi-tenancy → Integration tests
4. Estimated time: Full day (6-8 hours)
5. Includes: Multi-tenancy, jOOQ projections, REST API, Swagger
6. All code tested and working
7. Tutorial validates Milestone 2 competency
8. Success criteria: Full CRUD via API with tenant isolation

**Prerequisites:** Story 9.6

---

**Story 9.8: Tutorial - Production Aggregate (Milestone 3)**

As a new EAF developer,
I want tutorial for building production-grade aggregate with workflows,
So that I can achieve Milestone 3 productivity (ready for production deployment).

**Acceptance Criteria:**
1. docs/tutorials/production-aggregate.md created
2. Tutorial builds: Invoice aggregate with approval workflow, saga, custom metrics
3. Steps: Build aggregate → Add Flowable workflow → Implement saga → Add observability → Deploy
4. Estimated time: 2-3 days
5. Includes: BPMN workflows, compensating transactions, custom business metrics, deployment
6. All code tested and working
7. Tutorial validates Milestone 3 competency (production deployment)
8. Success criteria: Deployed to staging with full observability

**Prerequisites:** Story 9.7

---

**Story 9.9: How-To Guides Collection**

As an EAF developer,
I want practical How-To guides for common development tasks,
So that I can quickly find solutions to specific problems.

**Acceptance Criteria:**
1. docs/how-to/ directory created with guides:
   - handle-validation-errors.md (Arrow Either pattern)
   - implement-business-rules.md (domain logic patterns)
   - test-with-axon-fixtures.md (Axon Test Fixtures examples)
   - test-with-nullables.md (Nullable Pattern implementation)
   - debug-event-sourcing.md (event replay, time-travel debugging)
   - troubleshoot-common-issues.md (FAQ-style troubleshooting)
2. Each guide: Problem → Solution → Example → Common Pitfalls
3. All code examples tested and working
4. Cross-references between guides
5. Search-optimized (clear headings, keywords)

**Prerequisites:** Story 9.8

---

**Story 9.10: Reference Documentation**

As an EAF developer,
I want complete reference documentation,
So that I can look up specifications, configurations, and API details.

**Acceptance Criteria:**
1. docs/reference/ directory with comprehensive references:
   - architecture-decisions.md (links to architecture.md)
   - api-documentation.md (REST API reference, all endpoints)
   - configuration-reference.md (application.yml properties)
   - cli-commands.md (all scaffold commands)
   - testing-reference.md (7-layer testing strategy)
2. Each reference complete and accurate
3. Auto-generated content where possible (OpenAPI → API docs)
4. Version-specific references (EAF v1.0)
5. Quick reference tables and cheat sheets

**Prerequisites:** Story 9.9

---

**Story 9.11: Working Code Examples**

As an EAF developer,
I want fully working code examples for common patterns,
So that I can copy and adapt them for my use cases.

**Acceptance Criteria:**
1. docs/examples/ directory with complete, compilable examples:
   - simple-widget/ (minimal CQRS example from Getting Started)
   - multi-tenant-order/ (production example with all features)
   - saga-payment/ (complex workflow with saga and compensation)
2. Each example includes: README, source code, tests, deployment instructions
3. Examples buildable and testable (./gradlew :examples:simple-widget:test)
4. Examples documented with inline comments explaining patterns
5. Examples reference relevant documentation sections
6. All examples pass quality gates

**Prerequisites:** Story 9.8

---

**Story 9.12: Documentation Testing and Validation**

As a documentation maintainer,
I want all documentation tested for accuracy and completeness,
So that developers can trust documentation is correct and current.

**Acceptance Criteria:**
1. All code snippets extracted and tested (compile + run)
2. All links validated (no broken links)
3. All commands tested (CLI commands, scripts)
4. Screenshots updated to match current UI
5. Documentation review checklist completed
6. Broken link checker integrated into CI/CD
7. Documentation versioned with EAF releases
8. Documentation coverage report (% of features documented)

**Prerequisites:** Story 9.11

---

**Story 9.13: Documentation Search and Navigation**

As an EAF developer,
I want easy navigation and search across all documentation,
So that I can quickly find the information I need.

**Acceptance Criteria:**
1. docs/README.md serves as documentation portal with categorized links
2. Documentation structure: Getting Started → Tutorials → How-To → Reference → Examples
3. Each document includes: Table of Contents, Related Documents links, Next Steps
4. Search keywords optimized in headings
5. Quick Start path clearly marked (fastest route to productivity)
6. Navigation tested with new developers (usability validation)
7. Documentation portal tested for discoverability

**Prerequisites:** Story 9.12

---

**Story 9.14: Majlinda Onboarding Preparation**

As a documentation author,
I want validation that documentation enables <3 day onboarding,
So that Majlinda can successfully complete Epic 10 validation.

**Acceptance Criteria:**
1. Documentation completeness validated against Majlinda's user journey (from PRD)
2. All 34 journey steps have corresponding documentation
3. Missing documentation identified and created
4. Documentation tested with simulated onboarding (team member unfamiliar with CQRS)
5. Feedback incorporated from test onboarding
6. Documentation declares "Ready for Majlinda Validation"
7. Onboarding checklist created for Majlinda's Epic 10 work

**Prerequisites:** Story 9.13

---

## Epic 10: Reference Application for MVP Validation

**Expanded Goal:**
Epic 10 builds the complete multi-tenant Widget Management reference application that validates all EAF v1.0 capabilities by using ONLY framework features without custom workarounds. This comprehensive demo application demonstrates multiple domain aggregates with full CQRS command/query flows, 3-layer multi-tenancy with data isolation validation across multiple tenant contexts, enterprise authentication and authorization with role-based access, Flowable BPMN workflow integration for widget approval processes, shadcn-admin-kit operator portal for CRUD operations, and complete Constitutional TDD test coverage (unit, integration, property, fuzz, concurrency tests). This epic serves as Majlinda's onboarding validation proving <3 day aggregate development is achievable, benchmarks Nullable Pattern performance improvements, and provides the definitive reference implementation for all future EAF-based product development.

**Estimated Stories:** 12

---

**Story 10.1: Widget Management Domain Model**

As a framework developer,
I want comprehensive Widget domain model with business logic,
So that the reference app demonstrates realistic aggregate complexity.

**Acceptance Criteria:**
1. Widget aggregate enhanced from Epic 2 with: status (Draft, Published, Archived), ownership, metadata
2. Commands: CreateWidget, UpdateWidget, PublishWidget, ArchiveWidget, TransferOwnership
3. Events for all state transitions
4. Business rules: Widget can only be published if valid, archived widgets cannot be updated
5. Value objects: WidgetStatus enum, WidgetMetadata
6. Axon Test Fixtures for all commands and business rules
7. Unit tests with Nullable Pattern
8. All tests pass in <30 seconds

**Prerequisites:** Epic 9 complete

---

**Story 10.2: Widget Projection with Advanced Queries**

As a framework developer,
I want Widget projection supporting advanced queries,
So that the reference app demonstrates complex read model patterns.

**Acceptance Criteria:**
1. WidgetProjection enhanced with: full-text search, filtering by status, sorting
2. Queries: FindWidget, ListWidgets (with cursor pagination), SearchWidgets, CountWidgetsByStatus
3. jOOQ queries for all scenarios
4. Query performance optimized (<50ms for single, <200ms for paginated)
5. Integration tests for all query types
6. Multi-tenant query isolation validated
7. Query patterns documented as reference

**Prerequisites:** Story 10.1

---

**Story 10.3: Widget REST API with Full CRUD**

As a framework developer,
I want complete Widget REST API,
So that the reference app demonstrates production API patterns.

**Acceptance Criteria:**
1. WidgetController with all CRUD endpoints: POST, GET, PUT, DELETE, PATCH
2. Bulk operations: POST /widgets/bulk (create multiple)
3. Search endpoint: GET /widgets/search?q=...
4. OpenAPI documentation complete
5. Request/Response validation
6. Integration tests for all endpoints
7. API follows RFC 7807 error format
8. Swagger UI fully functional

**Prerequisites:** Story 10.2

---

**Story 10.4: Widget Approval BPMN Workflow**

As a framework developer,
I want Widget approval workflow using Flowable BPMN,
So that the reference app demonstrates workflow orchestration.

**Acceptance Criteria:**
1. widget-approval.bpmn20.xml workflow: Create Draft → Submit for Approval → Manual Review → Approve/Reject → Publish/Archive
2. BPMN service tasks dispatch: CreateWidgetCommand, PublishWidgetCommand, ArchiveWidgetCommand
3. Axon events signal workflow: WidgetSubmittedEvent → trigger approval task
4. Tenant-aware workflow (tenant_id in process variables)
5. Compensating transaction: Approval rejected → archive widget
6. Integration test executes full workflow end-to-end
7. Workflow visualized with diagram in documentation

**Prerequisites:** Story 10.3

---

**Story 10.5: Multi-Tenant Test Data and Scenarios**

As a framework developer,
I want test data for multiple tenants,
So that the reference app demonstrates and validates multi-tenancy.

**Acceptance Criteria:**
1. Test data seed script creates 3 tenants: tenant-a, tenant-b, tenant-c
2. Keycloak users created per tenant with different roles
3. Sample widgets created for each tenant (5 widgets per tenant)
4. Integration test validates: tenant A sees only their 5 widgets
5. Cross-tenant access scenarios tested (all blocked)
6. Test data documented and reproducible
7. Seed data script: ./scripts/seed-widget-demo.sh

**Prerequisites:** Story 10.1

---

**Story 10.6: shadcn-admin-kit Operator Portal for Widgets**

As a framework developer,
I want shadcn-admin-kit UI for Widget management,
So that the reference app demonstrates frontend integration.

**Acceptance Criteria:**
1. apps/admin/ enhanced with Widget resources
2. Components: WidgetList.tsx, WidgetEdit.tsx, WidgetCreate.tsx, WidgetShow.tsx
3. shadcn/ui components used (Tables, Forms, Buttons, Dialogs)
4. React-Admin data provider integration (cursor pagination)
5. Keycloak authentication flow working
6. Role-based UI (WIDGET_ADMIN sees all actions, WIDGET_VIEWER read-only)
7. UI tested in browser with all CRUD operations
8. UI passes accessibility checks (WCAG 2.1 Level A)

**Prerequisites:** Story 10.3

---

**Story 10.7: Majlinda Onboarding Validation (<3 Days)**

As Majlinda (new senior developer),
I want to build a new aggregate using only EAF documentation and CLI,
So that I can validate the <3 day productivity goal.

**Acceptance Criteria:**
1. Majlinda receives: EAF repository, documentation (Epic 9), CLI installed
2. Task: Build "Order" aggregate with full CRUD, multi-tenancy, API, tests, deployment
3. Time limit: 3 days maximum
4. Assistance allowed: Documentation and CLI only (minimal framework team help)
5. Success criteria (from PRD):
   - Fully functional aggregate with passing tests
   - Multi-tenant isolation working
   - Deployed to staging environment
   - All quality gates passing
6. Results documented: actual time, blockers encountered, documentation gaps
7. Feedback incorporated into documentation improvements

**Prerequisites:** Story 10.6, Epic 9 complete (documentation ready)

---

**Story 10.8: Nullable Pattern Performance Benchmarking**

As a framework developer,
I want performance benchmarks comparing Nullable Pattern to traditional mocking,
So that I can validate the 60%+ speed improvement claim.

**Acceptance Criteria:**
1. Benchmark tests created: Nullable vs Mockk/MockBean for Widget business logic
2. Scenarios benchmarked: simple validation, complex business rules, multiple collaborators
3. Benchmark results measured: test execution time, memory usage
4. Target validated: Nullable Pattern >60% faster than mocking frameworks
5. Results documented in docs/reference/nullable-pattern-benchmarks.md
6. Benchmarks runnable via: ./gradlew :products:widget-demo:benchmark
7. Results included in Epic 10 Reference Application summary

**Prerequisites:** Story 10.1

---

**Story 10.9: Complete Integration Test Suite**

As a framework developer,
I want comprehensive integration tests covering all Widget scenarios,
So that the reference app validates complete framework capabilities.

**Acceptance Criteria:**
1. Integration tests cover:
   - Full CRUD operations via REST API
   - Multi-tenancy isolation (3 layers)
   - Authentication and authorization (all roles)
   - Workflow orchestration (approval process)
   - Observability (logs, metrics, traces)
2. Test scenarios include edge cases and error conditions
3. All integration tests use Testcontainers (PostgreSQL, Keycloak, Redis)
4. Test execution time <10 minutes
5. Tests pass consistently (no flakiness)
6. Integration test suite documented as reference

**Prerequisites:** Story 10.4, Story 10.5, Story 10.6

---

**Story 10.10: Performance Validation Under Load**

As a framework developer,
I want load testing validating Widget app meets performance SLAs,
So that I can prove EAF meets NFR001 performance targets.

**Acceptance Criteria:**
1. Load test scenarios: 100 concurrent users, 1000 requests/second, 10K widgets per tenant
2. Performance targets validated:
   - API p95 latency <200ms
   - Event processing lag <10s
   - Query response time <50ms (single), <200ms (list)
3. Load test tool: JMeter or Gatling
4. Performance results documented
5. Bottlenecks identified and optimized
6. Load tests runnable in CI/CD
7. Performance validation report generated

**Prerequisites:** Story 10.9

---

**Story 10.11: Security Team Review and ASVS Validation**

As the security team,
I want formal security review of Widget reference app,
So that I can validate OWASP ASVS compliance (NFR002).

**Acceptance Criteria:**
1. Security review conducted covering: Authentication (10-layer JWT), Multi-tenancy (3 layers), Input validation, Error handling, Audit logging
2. OWASP ASVS 5.0 checklist completed (target: 100% L1, 50% L2)
3. Security test scenarios executed (injection, XSS, CSRF, broken auth, broken access)
4. Penetration testing performed (manual or automated)
5. Security findings documented and addressed
6. Security review document produced for customer due diligence
7. "Audit-Ready" status achieved

**Prerequisites:** Story 10.10

---

**Story 10.12: MVP Success Criteria Validation**

As product owner,
I want validation that all three MVP success criteria are met,
So that I can declare EAF v1.0 MVP complete.

**Acceptance Criteria:**
1. **Success Criterion 1 - Production Viability:** Reference app built using ONLY framework capabilities (no workarounds) ✓
2. **Success Criterion 2 - Onboarding Validation:** Majlinda completed new aggregate in <3 days (Story 10.7) ✓
3. **Success Criterion 3 - Security Validation:** Security team review confirms ASVS compliance (Story 10.11) ✓
4. All KPIs from PRD validated and documented
5. MVP completion report generated
6. Stakeholder sign-off obtained
7. EAF v1.0 declared production-ready

**Prerequisites:** Story 10.7, Story 10.11

---

## Story Guidelines Reference

**Story Format:**

```
**Story [EPIC.N]: [Story Title]**

As a [user type],
I want [goal/desire],
So that [benefit/value].

**Acceptance Criteria:**
1. [Specific testable criterion]
2. [Another specific criterion]
3. [etc.]

**Prerequisites:** [Dependencies on previous stories, if any]
```

**Story Requirements:**

- **Vertical slices** - Complete, testable functionality delivery
- **Sequential ordering** - Logical progression within epic
- **No forward dependencies** - Only depend on previous work
- **AI-agent sized** - Completable in 2-4 hour focused session
- **Value-focused** - Integrate technical enablers into value-delivering stories

---

**For implementation:** Use the `create-story` workflow to generate individual story implementation plans from this epic breakdown.
