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

**Scope:** ~50+ test cases across 6 spec files are marked `test.skip`:
- `admin-approval-queue.spec.ts` - 19 skipped
- `request-detail.spec.ts` - 16 skipped
- `vm-request-form.spec.ts` - 8 skipped
- `my-requests.spec.ts` - 7+ skipped
- `login.spec.ts` - 2 skipped

**Impact:** No E2E test coverage for user flows. Regressions will not be caught.

**Recommended Action:**
1. Set up Keycloak Testcontainers integration in CI
2. Create test data fixtures for authenticated scenarios
3. Prioritize enabling tests incrementally

### 🟠 Medium: Missing Unit Tests

| Class | Location | Reason |
|-------|----------|--------|
| `VmProvisioningProgressQueryService` | `dvmm-application/vm/` | Contains error handling and tenant validation logic |
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
| Skipped E2E Tests | ~50+ |

## Next Steps

1. **Immediate:** Review this document with team
2. **Short-term:** Add missing unit tests for `VmProvisioningProgressQueryService`
3. **Medium-term:** Enable E2E tests with proper CI authentication setup
