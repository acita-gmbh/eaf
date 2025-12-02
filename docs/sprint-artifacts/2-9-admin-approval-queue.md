# Story 2.9: Admin Approval Queue

Status: ready-for-dev

## Story

As an **admin**,
I want to see all pending requests in my tenant,
So that I can efficiently process approvals.

## Requirements Context Summary

- **Epic/AC source:** Story 2.9 in `docs/epics.md` - Admin Approval Queue (Admin dashboard mock applies)
- **FRs Satisfied:** FR25 (Admin can view all pending requests in tenant)
- **Architecture constraint:** Admin role check via SecurityConfig; tenant-scoped query; reuse `components/admin/ApprovalQueue.tsx` scaffold from tech spec structure (avoid duplicate table)
- **Prerequisites:** Story 2.7 (My Requests List & Cancel), Story 1.7 (Keycloak Integration with roles)
- **Risk Level:** MEDIUM (First admin-specific UI, role-based access)
- **Tech Spec Reference:** `docs/sprint-artifacts/tech-spec-epic-2.md` Sections 4.2, 4.3, Admin Dashboard mock
- **Out of Scope:** Bulk approval/rejection is NOT in MVP (single request actions only)

## Pre-Flight Setup Checklist

Before starting implementation, verify these are complete:

- [x] **Story 2.7 completed:** My Requests page exists with request cards
- [x] **Story 1.7 completed:** Keycloak integration with role-based access
- [x] **VmRequestProjectionRepository exists:** Query methods available
- [x] **Admin role defined:** Role check pattern established in SecurityConfig
- [x] **VM_REQUESTS table:** Schema exists with tenant_id for RLS filtering
- [ ] **Keycloak ADMIN role verified:** Confirm role name in Keycloak matches Spring Security expectation (e.g., `admin` maps to `ROLE_ADMIN`)

## Acceptance Criteria

1. **Admin sees "Open Requests" section**
   - Given I am logged in as an admin
   - When I navigate to the admin dashboard
   - Then I see an "Open Requests" section
   - And the section header shows a count badge with total pending requests

2. **Pending requests list displays required columns**
   - Given I view the "Open Requests" section
   - When I look at the list
   - Then each row shows: Requester name, VM Name, Project, Size, Age, Actions
   - And the Actions column contains "Approve" and "Reject" buttons

3. **Requests sorted by age (oldest first)**
   - Given I view the pending requests list
   - When I look at the order
   - Then requests are sorted by creation date ascending (oldest first)
   - And the age column shows relative time (e.g., "2 days ago")

4. **Requests older than 48h highlighted**
   - Given a request is older than 48 hours
   - When I view the list
   - Then that row is visually highlighted (e.g., amber/orange background)
   - And a "Waiting long" indicator is shown

5. **Filtering by project**
   - Given I view the pending requests list
   - When I select a project from the filter dropdown
   - Then only requests for that project are shown
   - And the count badge updates to reflect filtered count

6. **Tenant isolation for admin view**
   - Given I am an admin in Tenant A
   - When I view pending requests
   - Then I see ALL pending requests from ALL users in Tenant A
   - And I do NOT see requests from Tenant B (RLS enforced)

7. **Empty state for no pending requests**
   - Given there are no pending requests in my tenant
   - When I view the "Open Requests" section
   - Then I see an empty state with message "No pending requests"
   - And an illustration indicating nothing to process
   - **If filtered by project with no results:** Show "No requests for [Project Name]" instead

8. **Loading states**
   - Given the pending requests are loading
   - When the page renders
   - Then I see skeleton loaders for the list rows
   - And the count badge shows a loading indicator

9. **Navigation to request detail**
   - Given I view the pending requests list
   - When I click on a request row (not the action buttons)
   - Then I navigate to `/requests/{id}` (detail page from Story 2.8)
   - And I can review full details before approving/rejecting

10. **Approve/Reject buttons functional (preparation for Story 2.11)**
    - Given I view a request in the list
    - When I see the action buttons
    - Then "Approve" and "Reject" buttons are visible but disabled
    - And a tooltip explains "Available in Story 2.11"
    - And disabled buttons expose `aria-disabled` and accessible labels for screen readers

## Test Plan

### Backend Unit Tests

**GetPendingRequestsHandler (dvmm-application):**
- Returns pending requests for admin's tenant only
- Returns empty list when no pending requests
- Sorts by creation date ascending (oldest first)
- Filters by project when project ID provided
- Enforces pagination defaults (page size 25, max 100)
- Throws Forbidden for non-admin users
- Maps DB entities to DTOs correctly (hiding internal IDs)

**VmRequestProjectionRepository (dvmm-infrastructure):**
- `findPendingByTenantId` returns only PENDING status
- RLS filter applied automatically
- Pagination works correctly with size cap
- Project filter works with tenant filter

### Backend Integration Tests

- Full flow: Admin fetches pending requests from own tenant
- Tenant isolation: Admin A cannot see Admin B's tenant requests
- Non-admin user gets 403 Forbidden
- Project filtering returns correct subset

### Frontend Unit Tests

**usePendingRequests hook:**
- Fetches from GET /api/admin/requests/pending
- Returns loading state
- Returns error state for 403 with admin-only message
- Supports project filter parameter
- Supports refetch interval for real-time updates (30 seconds) with jitter; polling can be disabled in tests

**PendingRequestsTable component:**
- Renders all required columns
- Highlights rows older than 48 hours
- Shows "Waiting long" badge for old requests
- Sorts by age correctly
- Handles empty state
- Shows skeleton loading state
- **NO `useMemo`/`useCallback` allowed** (React Compiler rule)

**ProjectFilter component:**
- Shows all projects in dropdown
- Filters requests on selection
- Shows loading state and inline error for project fetch with retry
- Updates count badge

### E2E Tests (Playwright)

- Happy path: Admin logs in -> Sees pending requests -> Correct count displayed
- Age highlight: Seed request older than 48h -> Verify amber highlighting
- Project filter: Select project -> See filtered results
- Navigation: Click row -> Navigate to detail page
- Tenant isolation: Admin A sees only Tenant A requests (requires multi-tenant test setup)
- Contract: `/api/admin/projects` returns distinct projects for tenant (status 200, fields id/name)

## Structure Alignment / Previous Learnings

### Learnings from Story 2.8 (Request Status Timeline)

- **TanStack Query patterns:** `useQuery` with `refetchInterval: 30000` for polling
- **date-fns locale:** Use default English locale for relative time formatting
- **StatusBadge component:** Reuse for status display
- **API patterns:** GET for queries, authorization in controller
- **Component structure:** Keep components focused and composable

### Learnings from Story 2.7 (My Requests List)

- **RequestCard/Table patterns:** Established for displaying request info
- **Empty state handling:** EmptyState component with illustrations
- **Loading skeleton patterns:** Use shadcn/ui Skeleton component

### Domain Events & Projection (REUSE - Already Implemented)

```kotlin
// From dvmm-domain - events already exist
VmRequestCreated  // Contains requesterId, vmName, project, size
VmRequestApproved // Contains userId (approver)
VmRequestRejected // Contains userId (rejector), reason
VmRequestCancelled // Contains userId
```

### Projection Table (From Tech Spec)

```sql
-- VM_REQUESTS table already exists
-- Need to query with status = 'PENDING' and admin's tenant_id
SELECT * FROM "VM_REQUESTS"
WHERE "STATUS" = 'PENDING'
  AND "TENANT_ID" = :tenantId
ORDER BY "CREATED_AT" ASC;
```

### API Endpoint (From Tech Spec)

```text
GET /api/admin/requests/pending - Get pending requests for admin's tenant
    Query params: projectId (optional filter), page (default 0), size (default 25, max 100)
    Response: PagedResponse<PendingRequestResponse>
    Authorization: ADMIN role required; audit log entry (tenant, adminId, projectId)

    PagedResponse<T> structure:
    {
      content: T[],
      totalElements: number,
      totalPages: number,
      currentPage: number,
      pageSize: number
    }

GET /api/admin/projects - Distinct projects for filter
    Response: [{ id: UUID, name: String }]
    Authorization: ADMIN role required
```

## Tasks / Subtasks

### Phase 0: Pre-Implementation - Security Configuration

- [x] **Task 0.1: Enable Spring Security Method Security** (AC: 6)
  - Story 2.9 introduces the FIRST role-based authorization in the codebase
  - Create/update `SecurityConfig.kt` to add `@EnableMethodSecurity(prePostEnabled = true)`
  - Without this, `@PreAuthorize("hasRole('ADMIN')")` annotations are silently ignored
  - Add integration test proving 403 is returned for non-admin users

### Phase 1: Backend - Admin Query Handler

- [x] **Task 1.1: Create GetPendingRequestsQuery & Handler** (AC: 1, 2, 3, 6)
  - Create `GetPendingRequestsQuery.kt` with optional `projectId` filter and pagination (page,size) capped at 100
  - Create `GetPendingRequestsHandler.kt` in `dvmm-application`
  - Inject `VmRequestProjectionRepository`
  - Filter by tenant (from security context) and status PENDING
  - Sort by `createdAt` ascending

- [x] **Task 1.2a: Add findPendingByTenantId Repository Method** (AC: 2, 3, 6)
  - Add `findPendingByTenantId(tenantId, projectId?, page, size)` to `VmRequestProjectionRepository`
  - Ensure RLS is applied (tenant_id filter)
  - Return `Page<VmRequestProjection>`

- [x] **Task 1.2b: Add findDistinctProjects Repository Method** (AC: 5)
  - Add `findDistinctProjects(tenantId)` for dynamic filtering
  - Query: `SELECT DISTINCT project_id, project_name FROM VM_REQUESTS WHERE tenant_id = ?`
  - Return `List<ProjectInfo>`
  - **Note:** Projects come from existing VM requests, NOT Epic 4 (Projects & Quota). If tenant has no VM requests, the filter dropdown will be empty.

- [x] **Task 1.3: Create Admin DTO** (AC: 2)
  - Create `PendingRequestResponse.kt` (or reuse/extend `VmRequestResponse` if fields overlap >90%)
  - Fields: `id`, `requesterName`, `vmName`, `projectName`, `size`, `createdAt`
  - Map from projection entity
  - **Requester name resolution:** `REQUESTER_NAME` is already stored in `VM_REQUESTS_PROJECTION` table
  - If name not in projection, use `UserLookupService` pattern from Story 2.8

### Phase 2: Backend - API Endpoint

- [x] **Task 2.1: Add Admin Controller Endpoint** (AC: 1, 2, 5, 6)
  - Create `AdminRequestController.kt` or add to existing controller
  - Endpoint: `GET /api/admin/requests/pending`
  - Endpoint: `GET /api/admin/projects` (for filter dropdown)
  - Add `@PreAuthorize("hasRole('ADMIN')")` for role check
  - Accept optional `projectId` query parameter plus pagination (page,size default 0,25; max size 100)
  - Add audit log for admin fetch (tenant, adminId, project filter)

- [x] **Task 2.2: Add Backend Tests** (AC: All)
  - Unit tests for `GetPendingRequestsHandler`
  - Integration tests for tenant isolation
  - Integration tests for pagination defaults/max size
  - **CRITICAL:** Integration test verifying `ROLE_ADMIN` maps correctly from Keycloak role to Spring Security context
  - Integration tests for role-based access (403 for non-admin)

### Phase 3: Frontend - API & Hooks

- [x] **Task 3.1: Add API Function** (AC: 1, 2)
  - Create `src/api/admin-requests.ts`
  - Add `getPendingRequests(projectId?: string, page?: number, size?: number)` function
  - Add `getDistinctProjects()` function
  - Define `PendingRequest` TypeScript type

- [x] **Task 3.2: Create usePendingRequests Hook** (AC: 1, 2, 3, 8)
  - Create `src/hooks/usePendingRequests.ts`
  - Use `useQuery` with `refetchInterval: 30000` and jitter; allow polling disable for tests
  - Set `staleTime: 10000` to prevent thrashing
  - Accept optional `projectId` filter plus pagination inputs

- [x] **Task 3.3: Create useProjects Hook (Dynamic)** (AC: 5)
  - Create `src/hooks/useProjects.ts` if not exists
  - Fetch distinct projects from backend `GET /api/admin/projects` (no mocks!)
  - Surface loading + error states to dropdown UI
  - Used for filter dropdown

### Phase 4: Frontend - Components

- [x] **Task 4.1: Create PendingRequestsPage** (AC: 1, 7, 8, 9)
  - Create `src/pages/admin/PendingRequests.tsx`
  - Render "Open Requests" header with count badge
  - Handle loading and empty states
  - **Reuse existing `AdminQueueEmptyState.tsx` from `components/empty-states/`** (already created)
  - **Reuse existing `components/admin/ApprovalQueue.tsx` scaffold if present**
  - **NO `useMemo`/`useCallback` allowed**

- [x] **Task 4.2: Create PendingRequestsTable** (AC: 2, 3, 4, 9, 10)
  - Create `src/components/admin/PendingRequestsTable.tsx`
  - Columns: Requester, VM Name, Project, Size, Age, Actions (6 columns total)
  - **Reuse `StatusBadge` component from Story 2.7**
  - Highlight rows > 48h with amber background
  - Show "Waiting long" badge
  - Clickable rows navigate to detail page
  - Action buttons disabled with tooltip and `aria-label` for accessibility
  - Skeleton loader must match 6-column table structure

- [x] **Task 4.3: Create ProjectFilter** (AC: 5)
  - Create `src/components/admin/ProjectFilter.tsx`
  - Dropdown with project list
  - Trigger filter on selection
  - Show "All Projects" as default option
  - Show spinner/skeleton on load and inline error with retry on failure

- [x] **Task 4.4: Add Route & Navigation** (AC: 1, 9)
  - Add route `/admin/requests` to `App.tsx`
  - Add admin navigation item to Sidebar (if not exists)
  - Ensure route protected for admin role and uses Admin Dashboard layout container

### Phase 5: E2E Tests

- [x] **Task 5.1: Playwright Tests** (AC: 1-9)
  - Test admin login and sees pending requests
  - Test age highlighting for old requests
  - Test project filtering
  - Test navigation to request detail
  - Test empty state display

## Dev Notes

### Admin Role Check Pattern

```kotlin
// In Controller - use Spring Security annotation
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/api/admin/requests/pending")
suspend fun getPendingRequests(
    @RequestParam projectId: UUID? = null
): ResponseEntity<List<PendingRequestResponse>>

// Add audit log on each admin fetch (tenant, adminId, projectId, page/size)
```

### Error Handling Pattern

Follow the `Result<T, E>` sealed class pattern from `GetMyRequestsHandler`:

```kotlin
sealed class GetPendingRequestsResult {
    data class Success(val requests: Page<PendingRequestResponse>) : GetPendingRequestsResult()
    data object Forbidden : GetPendingRequestsResult()  // Non-admin user
}
```

### Age Calculation and Highlighting

```tsx
import { formatDistanceToNow, differenceInHours } from 'date-fns'

// Single source of truth for waiting threshold
const WAITING_THRESHOLD_HOURS = 48

const age = formatDistanceToNow(new Date(createdAt), { addSuffix: true })
const isOld = differenceInHours(new Date(), new Date(createdAt)) > WAITING_THRESHOLD_HOURS

// In JSX
<tr className={isOld ? 'bg-amber-50' : ''}>
  <td>{age}</td>
  {isOld && <Badge variant="warning">Waiting long</Badge>}
</tr>
```

### Count Badge Pattern

```tsx
<h2 className="flex items-center gap-2">
  Open Requests
  <Badge variant="secondary">{pendingRequests.length}</Badge>
</h2>
```

### Pagination & Backpressure

- Default page size 25; allow size up to 100 with server-side cap.
- Include `X-Total-Count` header for UI badge when paginated.
- Polling (30s) should include small random jitter; allow disable flag for tests to avoid thundering herd.

### Disabled Action Buttons (Preparation for Story 2.11)

```tsx
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

<Tooltip>
  <TooltipTrigger asChild>
    <Button disabled variant="outline" size="sm">Approve</Button>
  </TooltipTrigger>
  <TooltipContent>Available in Story 2.11</TooltipContent>
</Tooltip>
```

### UI Text

| Key | Text |
|-----|------|
| Section header | Open Requests |
| Approve button | Approve |
| Reject button | Reject |
| Waiting long | Waiting long |
| Empty state | No pending requests |
| Empty state (filtered) | No requests for [Project Name] |
| All projects | All Projects |
| Tooltip | Available in Story 2.11 |

### Files to Modify (Existing)

| File | Changes Required |
|------|------------------|
| `App.tsx` | Add route for `/admin/requests` |
| `Sidebar.tsx` | Add admin navigation item |
| `api/index.ts` | Export admin API functions |

### New Files to Create (Backend)

```text
dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmrequest/
├── GetPendingRequestsQuery.kt
└── GetPendingRequestsHandler.kt

dvmm/dvmm-api/src/main/kotlin/de/acci/dvmm/api/admin/
├── AdminRequestController.kt
└── PendingRequestResponse.kt
```

### New Files to Create (Frontend)

```text
dvmm/dvmm-web/src/
├── api/
│   └── admin-requests.ts
├── hooks/
│   ├── usePendingRequests.ts
│   └── useProjects.ts
├── pages/
│   └── admin/
│       └── PendingRequests.tsx
├── components/
│   └── admin/
│       ├── PendingRequestsTable.tsx
│       ├── PendingRequestRow.tsx
│       ├── ProjectFilter.tsx
│       └── PendingRequestsSkeleton.tsx
```

## Dev Agent Record

### Implementation Log

**Task 0.1: Enable Spring Security Method Security (2025-12-02)**
- Added `@EnableReactiveMethodSecurity` annotation to `SecurityConfig.kt`
- Created `MethodSecurityIntegrationTest.kt` with 4 tests validating role-based access:
  - Admin user can access admin-only endpoint (200 OK)
  - Non-admin user gets 403 Forbidden
  - Any authenticated user can access user endpoint
  - Unauthenticated request returns 401
- Note: WebFlux requires `@EnableReactiveMethodSecurity` (not `@EnableMethodSecurity`)
- Note: Methods with `@PreAuthorize` should return reactive types (Mono/Flux)

**Task 1.1: Create GetPendingRequestsQuery & Handler (2025-12-02)**
- Created `GetPendingRequestsQuery.kt` with:
  - tenantId for RLS filtering
  - Optional projectId filter for AC 5
  - PageRequest with max size capped at 100
- Created `GetPendingRequestsHandler.kt`:
  - Delegates to `VmRequestReadRepository.findPendingByTenantId()`
  - Returns `Result<PagedResponse<VmRequestSummary>, GetPendingRequestsError>`
  - Error types: `Forbidden` and `QueryFailure`
- Added `findPendingByTenantId()` method to `VmRequestReadRepository` interface
- Created `GetPendingRequestsHandlerTest.kt` with 8 unit tests - all pass

### Completion Notes

**Story 2.9 Complete (2025-12-02)**

All acceptance criteria implemented:
- AC 1: "Open Requests" section with count badge
- AC 2: Table with columns: Requester, VM Name, Project, Size, Age, Actions
- AC 3: Sorted by creation date ascending (backend)
- AC 4: Amber highlighting for requests > 48h with "Waiting long" badge
- AC 5: Project filter dropdown fetches from backend
- AC 6: Tenant isolation via RLS (backend enforced)
- AC 7: Empty states (general + filtered)
- AC 8: Loading skeletons for table and count badge
- AC 9: Click row navigates to request detail
- AC 10: Approve/Reject buttons disabled with tooltips

Test coverage:
- Backend: 16 unit tests, 9 integration tests
- Frontend: 381 unit tests pass
- E2E: 19 Playwright tests (most require auth setup)

### Debug Log

- Fixed test failures by adding `vi.hoisted()` mock for `useAuth` in Sidebar and MobileNav tests
- Added `date-fns` dependency for age calculation
- Fixed VmSizeSpec import to use `type` import for verbatimModuleSyntax

## File List

### New Files (Backend)
- `dvmm/dvmm-api/src/test/kotlin/de/acci/dvmm/api/security/MethodSecurityIntegrationTest.kt`
- `dvmm/dvmm-api/src/main/kotlin/de/acci/dvmm/api/admin/PendingRequestResponse.kt`
- `dvmm/dvmm-api/src/main/kotlin/de/acci/dvmm/api/admin/AdminRequestController.kt`
- `dvmm/dvmm-api/src/test/kotlin/de/acci/dvmm/api/admin/AdminRequestControllerTest.kt`
- `dvmm/dvmm-api/src/test/kotlin/de/acci/dvmm/api/admin/AdminRequestControllerIntegrationTest.kt`
- `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmrequest/GetPendingRequestsQuery.kt`
- `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmrequest/GetPendingRequestsHandler.kt`
- `dvmm/dvmm-application/src/test/kotlin/de/acci/dvmm/application/vmrequest/GetPendingRequestsHandlerTest.kt`

### New Files (Frontend)
- `dvmm/dvmm-web/src/api/admin.ts`
- `dvmm/dvmm-web/src/hooks/usePendingRequests.ts`
- `dvmm/dvmm-web/src/hooks/usePendingRequests.test.ts`
- `dvmm/dvmm-web/src/hooks/useProjects.ts`
- `dvmm/dvmm-web/src/hooks/useProjects.test.ts`
- `dvmm/dvmm-web/src/hooks/useIsAdmin.ts`
- `dvmm/dvmm-web/src/hooks/useIsAdmin.test.ts`
- `dvmm/dvmm-web/src/pages/admin/PendingRequests.tsx`
- `dvmm/dvmm-web/src/components/admin/PendingRequestsTable.tsx`
- `dvmm/dvmm-web/src/components/admin/__tests__/PendingRequestsTable.test.tsx`
- `dvmm/dvmm-web/src/components/admin/ProjectFilter.tsx`
- `dvmm/dvmm-web/src/components/auth/AdminProtectedRoute.tsx`
- `dvmm/dvmm-web/src/components/auth/__tests__/AdminProtectedRoute.test.tsx`
- `dvmm/dvmm-web/src/components/ui/table.tsx` (shadcn)
- `dvmm/dvmm-web/src/components/ui/tooltip.tsx` (shadcn)
- `dvmm/dvmm-web/src/components/ui/skeleton.tsx` (shadcn)
- `dvmm/dvmm-web/e2e/admin-approval-queue.spec.ts`

### Modified Files
- `dvmm/dvmm-api/src/main/kotlin/de/acci/dvmm/api/security/SecurityConfig.kt`
- `dvmm/dvmm-app/src/main/kotlin/de/acci/dvmm/config/ApplicationConfig.kt` (bean registration)
- `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmrequest/VmRequestReadRepository.kt`
- `dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/projection/VmRequestProjectionRepository.kt`
- `dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/projection/VmRequestReadRepositoryAdapter.kt`
- `dvmm/dvmm-infrastructure/src/test/kotlin/de/acci/dvmm/infrastructure/projection/VmRequestProjectionRepositoryIntegrationTest.kt`
- `dvmm/dvmm-web/src/App.tsx`
- `dvmm/dvmm-web/src/components/layout/Sidebar.tsx`
- `dvmm/dvmm-web/src/components/auth/index.ts`
- `dvmm/dvmm-web/src/components/layout/__tests__/Sidebar.test.tsx`
- `dvmm/dvmm-web/src/components/layout/__tests__/MobileNav.test.tsx`
- `docs/sprint-artifacts/sprint-status.yaml`

## Change Log

| Date | Change |
|------|--------|
| 2025-12-02 | Task 0.1: Added @EnableReactiveMethodSecurity for role-based access control |
| 2025-12-02 | Task 1.1: Created GetPendingRequestsQuery/Handler with unit tests |
| 2025-12-02 | Task 1.2a: Added findPendingByTenantId to repository with integration tests |
| 2025-12-02 | Task 1.2b: Added findDistinctProjects for project filter dropdown |
| 2025-12-02 | Task 1.3: Created admin DTOs (PendingRequestResponse, ProjectResponse) |
| 2025-12-02 | Task 2.1: Created AdminRequestController with /pending and /projects endpoints |
| 2025-12-02 | Task 2.2: Added backend unit tests (16) and integration tests (9) |
| 2025-12-02 | Task 3.1-3.3: Created admin.ts API, usePendingRequests, useProjects hooks |
| 2025-12-02 | Task 4.1-4.4: Created PendingRequestsPage, PendingRequestsTable, ProjectFilter, AdminProtectedRoute |
| 2025-12-02 | Task 5.1: Created 19 Playwright E2E tests for admin approval queue |

## References

- [Source: docs/epics.md#Story-2.9-Admin-Approval-Queue]
- [Source: docs/sprint-artifacts/tech-spec-epic-2.md#Section-4.2-API-Design]
- [Source: docs/sprint-artifacts/2-7-my-requests-list-cancel.md]
- [Source: docs/sprint-artifacts/2-8-request-status-timeline.md]
- [date-fns differenceInHours](https://date-fns.org/v3.6.0/docs/differenceInHours)
- [shadcn/ui Badge](https://ui.shadcn.com/docs/components/badge)
- [shadcn/ui Tooltip](https://ui.shadcn.com/docs/components/tooltip)
- [CLAUDE.md#Zero-Tolerance-Policies]
