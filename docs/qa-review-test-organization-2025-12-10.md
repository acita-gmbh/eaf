# QA Review: Test Organization

**Date:** 2025-12-10
**Reviewer:** QA Testing Officer
**Branch:** `claude/review-test-organization-016gZnu4W2zhf4V62WiVBVAb`

## Executive Summary

Overall test infrastructure is **well-organized and mature**. Three issues identified and fixed in this review:
1. Backend test package naming violation
2. Frontend test location inconsistency (now enforced via vitest config)

## Issues Fixed

### ✅ VsphereClientIntegrationTest Package Location (FIXED)

**Problem:** Test was in non-standard package `integration.vmware` instead of `de.acci.dvmm.infrastructure.vmware`

**Fix Applied:** Moved file to correct location:
- From: `dvmm-infrastructure/src/test/kotlin/integration/vmware/VsphereClientIntegrationTest.kt`
- To: `dvmm-infrastructure/src/test/kotlin/de/acci/dvmm/infrastructure/vmware/VsphereClientIntegrationTest.kt`

## Outstanding Issues

### 🔴 Critical: E2E Tests Almost Entirely Skipped

**Location:** `dvmm/dvmm-web/e2e/*.spec.ts`

**Scope:** ~52 test cases across 6 spec files are marked `test.skip`:

| Spec File | Skipped Tests | Working Tests |
|-----------|---------------|---------------|
| `admin-approval-queue.spec.ts` | 19 | 1 (unauthenticated redirect) |
| `request-detail.spec.ts` | 16 | 1 (unauthenticated redirect) |
| `vm-request-form.spec.ts` | 8 | 1 (unauthenticated redirect) |
| `my-requests.spec.ts` | 11 | 1 (unauthenticated redirect) |
| `login.spec.ts` | 2 | 3 (unauthenticated flows) |

**Root Cause Analysis:**

Tests are skipped because they require a full authentication and backend stack:

1. **Keycloak Authentication Required** (`@requires-auth`)
   - Tests need authenticated sessions with specific roles (admin/user)
   - Sessions are created by `e2e/auth.setup.ts` using real Keycloak login
   - Sessions stored in `playwright/.auth/{admin,user}.json`

2. **Backend API Required** (`@requires-backend`)
   - Tests hit actual `/api/*` endpoints
   - Requires PostgreSQL database with schema
   - Requires event store and projections running

3. **Test Data Not Seeded**
   - Tests reference IDs like `test-request-id`, `pending-request-id`
   - No automated test data fixtures exist

**Current Mitigation:**

Each spec file includes a "Unauthenticated" test block that CAN run without auth:
```typescript
test.describe('... - Unauthenticated', () => {
  test('redirects to login when accessing without auth', async ({ page }) => {
    // Verifies redirect to Keycloak login
  })
})
```

These 5-6 tests DO run and provide basic coverage for auth redirect behavior.

**Local Execution (Manual):**

Developers can run authenticated tests locally:
```bash
# 1. Start backend
./gradlew :dvmm:dvmm-app:bootRun

# 2. Create auth sessions
npm run test:e2e -- --project=setup

# 3. Run authenticated tests
npm run test:e2e -- --project=chromium-admin
npm run test:e2e -- --project=chromium-user
```

**CI Enablement Requirements:**

| Requirement | Effort | Notes |
|-------------|--------|-------|
| Keycloak Testcontainers in CI | Medium | Need to start container, configure env vars |
| Backend in CI | Medium | Already have Gradle build; need bootRun step |
| Test data fixtures | Medium | Create seed data via API or SQL |
| Session creation | Low | Run setup project before test projects |
| CI workflow updates | Low | Add steps to existing GitHub Actions |

**Recommended Implementation Plan:**

1. **Phase 1 - Infrastructure** (1-2 days)
   - Add Keycloak Testcontainer to CI workflow
   - Add backend startup step with test profile
   - Set environment variables for Playwright

2. **Phase 2 - Test Data** (1 day)
   - Create `e2e/fixtures/seed-data.ts` with test request creation
   - Use API calls or direct DB seeding
   - Run before test suite

3. **Phase 3 - Enable Tests Incrementally** (1-2 days)
   - Remove `test.skip` from `login.spec.ts` first (simplest)
   - Then `vm-request-form.spec.ts` (form submission)
   - Then `my-requests.spec.ts` (list + cancel flows)
   - Then `request-detail.spec.ts` (detail views)
   - Finally `admin-approval-queue.spec.ts` (admin role tests)

**Impact:** No E2E test coverage for authenticated user flows. Regressions in login, request submission, cancellation, and admin approval will not be caught automatically.

### 🟠 Medium: Missing Unit Tests

| Class | Location | Reason |
|-------|----------|--------|

| `VmProvisioningProgressProjectionRepositoryAdapter` | `dvmm-infrastructure/projection/` | Database interaction |
| `TimelineEventProjectionUpdaterAdapter` | `dvmm-infrastructure/projection/` | Write-side projection logic |

### ✅ Frontend Test Location Inconsistency (FIXED)

**Problem:** Three patterns were used inconsistently (colocated, `__tests__` subdirectory, top-level `__tests__`).

**Fix Applied:**
- Moved 8 test files from `__tests__/` directories to colocated positions
- Updated `vitest.config.ts` to exclude `**/__tests__/**` from test discovery
- This enforces the colocated pattern going forward

**Convention:** Tests MUST be colocated with source files (e.g., `Button.test.tsx` next to `Button.tsx`)

### 🟡 Low: Disabled Tests Without Tracking

Three `@Disabled` tests without GitHub issue references:

| Test | File | Reason |
|------|------|--------|
| `should create and list VM` | `VsphereClientIntegrationTest.kt` | VCSIM doesn't support VMware Tools IP detection |
| `should delete VM` | `VsphereClientIntegrationTest.kt` | VCSIM doesn't support VMware Tools IP detection |
| `VCF SDK 9_0 port limitation` | `VcenterAdapterVcsimIntegrationTest.kt` | VCF SDK 9.0 does not support custom ports |

**Recommended Action:** Create GitHub issues and update annotations to reference them.

## Strengths Observed

| Aspect | Assessment |
|--------|------------|
| Test Naming | Excellent - BDD style with backticks |
| Given-When-Then | Consistently applied with comments |
| Test Isolation | Good - `@IsolatedEventStore`, proper cleanup |
| Mocking | Consistent MockK usage |
| Integration Tests | Proper Testcontainers setup |
| Multi-Tenant Testing | Well-implemented with RLS enforcement |
| Architecture Tests | Konsist rules enforcing boundaries |
| Coverage Thresholds | Backend 70%, Frontend 80%/75% |

## Test Statistics

| Category | Count |
|----------|-------|
| Backend Test Files | 97 |
| Frontend Unit Test Files | 56 |
| E2E Spec Files | 6 |
| Integration Test Files | 24 |
| Disabled Backend Tests | 3 |
| Skipped E2E Tests | 56 |
| Working E2E Tests | 7 (unauthenticated redirects only) |

## Next Steps

1. **Immediate:** Review this document with team
2. **Short-term:**
   - Add missing unit tests for `VmProvisioningProgressQueryService`
   - Create GitHub issues for 3 disabled backend tests
3. **Medium-term:** Enable E2E tests in CI:
   - Phase 1: Add Keycloak + backend to CI workflow
   - Phase 2: Create test data seeding fixtures
   - Phase 3: Incrementally remove `test.skip` from spec files
