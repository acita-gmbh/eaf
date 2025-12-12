# Story 1.5: tenant-context-module

Status: done

## Story

As a **developer**,  
I want tenant context automatically propagated through coroutines,  
so that all operations are tenant-scoped and fail closed when context is missing.

## Requirements Context Summary

- **Epic/AC source:** Story 1.5 in `docs/epics.md` — must provide `TenantContext.current()` that returns the tenant from JWT claim `tenant_id`; missing context triggers 403 (`TenantContextMissingException`); implementation uses coroutine context (no ThreadLocal).  
- **Testability Concern TC-001 (critical):** tenant context must survive dispatcher switches, async boundaries, and 100-parallel-coroutines stress without cross-contamination (A/B tenants).  
- **Architecture constraints:** follow Architecture tenant context pattern (coroutine-safe), fail-closed semantics, no Spring imports inside framework modules; keep module boundary (EAF framework only).  
- **Security:** align with Security Architecture (RLS + tenant claim).  
- **Prerequisites:** Story 1.2 (core types) already done; Story 1.4 learnings available for reuse patterns (value classes, testing scaffolds).

## Acceptance Criteria (Story 1.5)

1. Tenant context retrieval  
   - Given a request with a valid JWT containing `tenant_id`, when processing the request, then `TenantContext.current()` returns that tenant ID.
2. Coroutine propagation  
   - `TenantContextElement` implements `CoroutineContext.Element` and carries tenant through coroutine boundaries (no ThreadLocal).
3. Web filter extraction  
   - `TenantContextWebFilter` extracts `tenant_id` from JWT on every request and installs it into coroutine context.
4. Fail-closed semantics  
   - Missing tenant context results in HTTP 403 and throws `TenantContextMissingException`.
5. TC-001 resilience (critical)  
   - Context survives dispatcher switches (e.g., `Dispatchers.IO`).  
   - Context survives async boundaries (`async {}` / child coroutines).  
   - Under 100 parallel coroutines (50 tenant A, 50 tenant B), no cross-contamination occurs.

## Test Plan

- Unit: TenantContextPropagationTest covering dispatcher switch, async child, and concurrent isolation (100 coroutines).
- Unit: TenantContextWebFilterTest (extracts tenant_id from JWT, returns 403 when missing/invalid).
- Unit/Integration: TenantContext.current() throws TenantContextMissingException when not set.
- Optional integration (if wiring exists): WebFlux filter chain applying context and rejecting missing tenant with 403.

## Structure Alignment / Previous Learnings

- Reuse patterns from Story 1-4 (Aggregate base) for test structure and assertions; keep pure Kotlin in EAF modules (no Spring in framework code).
- RLS and tenant_id already used in Event Store (Story 1-3); ensure consistency of `tenant_id` value class and metadata propagation.
- Testing utilities available from Story 1-9: `TestTenantFixture`, `TestUserFixture`, `RlsEnforcingDataSource`, `@IsolatedEventStore` — leverage for isolation/stress cases as needed.
- No unified-project-structure doc present; follow existing `docs/architecture.md` module boundaries and constraints.

## Tasks / Subtasks

- [x] Implement `TenantContextElement` (CoroutineContext.Element) in `eaf-tenant` with value class `TenantId`. (AC: 2)
- [x] Implement `TenantContext.current()` and `currentOrNull()` helpers (suspend, coroutine-context based). (AC: 1,4)
- [x] Define `TenantContextMissingException` with fail-closed semantics (HTTP 403 message). (AC: 4)
- [x] Implement `TenantContextWebFilter` (WebFlux/CoWebFilter) to extract `tenant_id` from JWT and install context; reject missing/invalid with 403. (AC: 1,3,4)
- [x] JWT helper: extract claim `tenant_id` (UUID), validate format; unit tests for claim parsing edge cases. (AC: 1,3)
- [x] Tests: `TenantContextPropagationTest` covering dispatcher switch, async child, 100-parallel isolation (TC-001). (AC: 2,5)
- [x] Tests: `TenantContextWebFilterTest` covering happy path and missing/invalid tenant -> 403. (AC: 1,3,4)
- [x] Tests: `TenantContext.current()` throws when not set. (AC: 4)
- [x] Ensure no Spring dependencies leak into core context classes; keep filter in adapter layer if needed. (AC: 2,3)
- [x] Documentation updates: add references/citations to Dev Notes (architecture, security), update sprint-status when drafted. (meta)

## Dev Notes

- Relevant architecture patterns and constraints: see docs/architecture.md (Tenant Context Pattern), docs/security-architecture.md (RLS, fail-closed), docs/prd.md (FR64-67 multi-tenancy expectations), docs/test-design-system.md#TC-001 (TC-001 stress requirements).
- Source tree components to touch: eaf/eaf-tenant (new TenantContext*, WebFilter adapter), potential reuse of JWT utilities in dcm-api; keep framework code Spring-free.
- Testing standards summary: Use kotlinx-coroutines-test for propagation; WebFlux filter tests with mock ServerWebExchange; achieve ≥80% coverage and ≥70% mutation where applicable.

### Project Structure Notes

- No unified-project-structure doc; follow module boundaries from docs/architecture.md. Keep core context classes in eaf-tenant; adapters (WebFilter) in appropriate layer without leaking into core.

### References

- [Source: docs/epics.md#Story-1.5-Tenant-Context-Module]  
- [Source: docs/architecture.md#Tenant-Context-Pattern]  
- [Source: docs/security-architecture.md#Multi-Tenant-Isolation]  
- [Source: docs/prd.md#Multi-Tenancy]  
- [Source: docs/sprint-artifacts/tech-spec-epic-1.md#Tenant-Context]  
- [Source: docs/test-design-system.md#TC-001]

## Dev Agent Record

### Learnings from Previous Story

**From Story 1-4-aggregate-base-pattern (Status: done)**

- New core assets to reuse: `AggregateRoot`, `AggregateSnapshot`, `SnapshotStore`, `PostgresSnapshotStore` (paths: eaf/eaf-eventsourcing/...).
- Established testing patterns: unit tests for event application/reconstitution; integration tests for snapshot store; follow these styles for TenantContext propagation tests.
- Architectural consistency: versioning and tenant metadata already enforced in event store; align TenantContext to feed the same tenant_id into event metadata.
- Technical debt: none open; but reuse existing fixtures (`TestAggregate`, `TestTenantFixture`, `RlsEnforcingDataSource`) to avoid duplication.
- Warnings: ensure no ThreadLocal usage—stick to CoroutineContext as in architecture guidance.

### Change Log

- 2025-11-26: Implemented tenant context module (CoroutineContext element, helpers, JWT extractor, WebFlux filter) plus propagation and WebFilter tests; sprint status updated to review.

### Context Reference

- docs/sprint-artifacts/1-5-tenant-context-module.context.xml

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

- Plan: (a) Add coroutine-based TenantContextElement/current helpers + exception in eaf-tenant; (b) JWT claim extractor + CoWebFilter to install context/fail-closed 403; (c) Tests first: propagation (dispatcher, async, 100 concurrent), missing context throws, WebFilter happy/missing/invalid JWT; (d) Wire dependencies (WebFlux, coroutines-reactor, reactor-kotlin-extensions, Jackson) and update sprint-status -> in-progress.

### Completion Notes

- Implemented coroutine TenantContextElement/current/currentOrNull with Reactor fallback and fail-closed TenantContextMissingException (403).
- Added JWT tenant_id extractor, WebFlux TenantContextWebFilter installing tenant into Reactor context; rejects missing/invalid claims.
- Tests cover dispatcher/async/100-concurrent propagation, missing context failure, WebFilter happy/invalid/missing cases (all green).

### File List

- MOD docs/sprint-artifacts/sprint-status.yaml
- MOD docs/sprint-artifacts/1-5-tenant-context-module.md
- MOD eaf/eaf-tenant/build.gradle.kts
- ADD eaf/eaf-tenant/src/main/kotlin/de/acci/eaf/tenant/TenantContextElement.kt
- ADD eaf/eaf-tenant/src/main/kotlin/de/acci/eaf/tenant/TenantContext.kt
- ADD eaf/eaf-tenant/src/main/kotlin/de/acci/eaf/tenant/TenantContextMissingException.kt
- ADD eaf/eaf-tenant/src/main/kotlin/de/acci/eaf/tenant/JwtTenantClaimExtractor.kt
- ADD eaf/eaf-tenant/src/main/kotlin/de/acci/eaf/tenant/TenantContextWebFilter.kt
- ADD eaf/eaf-tenant/src/test/kotlin/de/acci/eaf/tenant/TenantContextPropagationTest.kt
- ADD eaf/eaf-tenant/src/test/kotlin/de/acci/eaf/tenant/TenantContextWebFilterTest.kt

### Completion Notes List

### File List

---

## Code Review

**Reviewer:** Senior Developer (Claude Code)
**Date:** 2025-11-26
**Outcome:** ✅ **APPROVED**

### Acceptance Criteria Validation

| AC | Description | Status | Evidence |
|----|-------------|--------|----------|
| AC1 | Tenant context retrieval | ✅ | `TenantContext.kt:13-14`, `TenantContextWebFilter.kt:18-22` |
| AC2 | Coroutine propagation | ✅ | `TenantContextElement.kt:8-11` extends `AbstractCoroutineContextElement` |
| AC3 | Web filter extraction | ✅ | `TenantContextWebFilter.kt:25-30` extracts from JWT |
| AC4 | Fail-closed semantics | ✅ | `TenantContextMissingException.kt:7-9` - HTTP 403 |
| AC5 | TC-001 resilience | ✅ | `TenantContextPropagationTest.kt:18-68` - dispatcher, async, 100 concurrent |

### Task Verification

All 10 tasks verified complete with file:line evidence.

### Code Quality Assessment

**Strengths:**
- Clean separation: pure Kotlin context classes vs Spring adapter
- Proper fail-closed semantics with HTTP 403
- Case-insensitive Bearer check
- Reactor context fallback for WebFlux integration
- Consolidated `REACTOR_TENANT_KEY` constant
- Clear documentation about JWT signature verification deferral to Story 1.7

**Test Coverage:**
- 7 tests, 100% passing
- Covers TC-001 requirements (dispatcher switches, async boundaries, 100 concurrent)

**Architecture Compliance:**
- No EAF → DCM dependencies
- Spring imports only in adapter layer
- Uses `eaf-core` types

### Issues Found

None blocking. JWT signature verification intentionally deferred to Story 1.7.

### Recommendation

Story ready to proceed to `done` status.
