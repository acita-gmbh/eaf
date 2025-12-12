# Story 2-9a: E2E Auth Setup & Keycloak Role Mapping Tests

**Epic:** Epic 2 - Core Workflow
**Status:** Done
**Created:** 2025-12-03
**Completed:** 2025-12-03

## Story

As a **developer**,
I want E2E authentication setup and Keycloak role mapping integration tests,
So that I can verify authentication works correctly in E2E and backend tests.

## Background

This story was created during the correct-course workflow after Story 2.9 (Admin Approval Queue) code review identified test coverage gaps:
- E2E tests lacked Keycloak authentication setup using Playwright's `storageState` pattern
- Backend integration tests didn't verify Keycloak JWT role mapping works with `@PreAuthorize`

**GitHub Issue:** #58
**GitHub PR:** #59

## Acceptance Criteria

### AC-1: E2E Authentication Setup with storageState
**Given** a Playwright E2E test suite
**When** the setup project runs
**Then** it authenticates users via Keycloak and saves session to `playwright/.auth/*.json`
**And** other tests can reuse authenticated state via `storageState` configuration

### AC-2: Keycloak JWT Role Mapping Integration Test
**Given** a Keycloak-issued JWT with `realm_access.roles` claim
**When** Spring Security processes the token
**Then** roles are mapped to authorities with `ROLE_` prefix (e.g., `admin` → `ROLE_admin`)

### AC-3: hasRole() Authorization Test
**Given** an endpoint protected with `@PreAuthorize("hasRole('admin')")`
**When** an admin user accesses the endpoint
**Then** access is granted (HTTP 200)
**When** a regular user accesses the endpoint
**Then** access is denied (HTTP 403)

### AC-4: Tenant User Isolation Test
**Given** users from different tenants (tenant1-user, tenant2-user)
**When** both users authenticate
**Then** they have different subject IDs in their JWTs
**And** tenant isolation is maintained

## Implementation Details

### E2E Auth Setup (`dcm-web/e2e/auth.setup.ts`)
- `authenticateUser()` helper function extracts common authentication logic
- Supports admin and regular user authentication
- Saves `storageState` to `playwright/.auth/admin.json` and `playwright/.auth/user.json`
- Uses `@seontechnologies/playwright-utils/log` for structured logging

### Keycloak Role Mapping Test (`dcm-api/.../KeycloakRoleMappingIntegrationTest.kt`)
- Integration test using real Keycloak Testcontainer (not mocked JWTs)
- Test endpoint `/api/auth/roles` returns authentication details
- Test endpoint `/api/auth/admin-only` protected with `@PreAuthorize("hasRole('admin')")`
- Validates:
  - `realm_access.roles` claim structure
  - `ROLE_` prefix mapping to Spring Security authorities
  - `hasRole('admin')` authorization works correctly
  - Different tenant users have different subject IDs

## Prerequisites

- Story 2.1: Keycloak Login Flow (provides frontend auth infrastructure)
- Story 2.9: Admin Approval Queue (triggered discovery of test gaps)

## Test Files

| File | Purpose |
|------|---------|
| `dcm-web/e2e/auth.setup.ts` | E2E authentication setup with storageState |
| `dcm-web/e2e/README.md` | Documentation for E2E auth setup |
| `dcm-api/.../KeycloakRoleMappingIntegrationTest.kt` | Keycloak JWT role mapping tests |

## Definition of Done

- [x] E2E auth setup authenticates via Keycloak and saves storageState
- [x] Integration test verifies realm_access.roles → Spring Security authorities
- [x] hasRole('admin') test verifies authorization (admin=200, user=403)
- [x] Tenant isolation test verifies different subject IDs
- [x] Documentation updated (e2e/README.md)
- [x] Code review passed
- [x] PR merged

## Code Review (2025-12-03)

**Review Findings:** 0 High, 2 Medium, 3 Low

### Fixes Applied

| Issue | Severity | Fix |
|-------|----------|-----|
| Bug: auth.setup.ts looked for non-existent heading "My Virtual Machines" | MEDIUM | Added `data-testid="dashboard-authenticated"` to Dashboard.tsx, updated selectors |
| Tenant isolation test comment was unclear about AC-4 requirements | MEDIUM | Clarified that subject ID isolation satisfies AC-4, tenant_id is optional |
| `@Suppress("UNCHECKED_CAST")` code smell | LOW | Replaced with `filterIsInstance<String>()` pattern |
| No global timeout in playwright.config.ts | LOW | Added 60s timeout for auth tests |
| login.spec.ts used same outdated heading selector | LOW | Updated to use data-testid |

### Additional Files Changed

| File | Change |
|------|--------|
| `dcm-web/src/pages/Dashboard.tsx` | Added `data-testid="dashboard-authenticated"` |
| `dcm-web/e2e/login.spec.ts` | Updated selector to use data-testid |
| `dcm-web/playwright.config.ts` | Added global 60s timeout |

## Related Stories

- Story 2.1: Keycloak Login Flow
- Story 2.9: Admin Approval Queue
