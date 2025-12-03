# Story 2.10: Request Detail View (Admin)

Status: review

## Story

As an **admin**,
I want to see complete request details before deciding,
so that I can make informed approval decisions.

## Requirements Context Summary

- **Epic/AC source:** Story 2.10 in `docs/epics.md` - Request Detail View (Admin)
- **FRs Satisfied:** FR26 (Admins can view full request details before deciding)
- **Architecture constraint:** Admin role check via SecurityConfig; tenant-scoped query; reuse Timeline components from Story 2.8
- **Prerequisites:** Story 2.9 (Admin Approval Queue)
- **Risk Level:** LOW (Builds on established patterns from Story 2.8/2.9)
- **Tech Spec Reference:** `docs/sprint-artifacts/tech-spec-epic-2.md` Sections 4.2, 4.3

## Pre-Flight Setup Checklist

Before starting implementation, verify these are complete:

- [x] **Story 2.9 completed:** Admin Approval Queue with pending requests list
- [x] **Story 2.8 completed:** Request Status Timeline with timeline components
- [x] **VmRequestProjectionRepository exists:** Query methods available
- [x] **Admin role defined:** Role check pattern established with `@PreAuthorize`
- [x] **REQUEST_TIMELINE_EVENTS table:** Schema exists from Story 2.8
- [x] **Timeline components exist:** `Timeline.tsx`, `TimelineEvent.tsx` from Story 2.8 (import from Story 2.8 outputs)

## Acceptance Criteria

1. **Admin sees request detail page**
   - Given I click on a pending request in the approval queue
   - When the detail view opens
   - Then I navigate to `/admin/requests/{id}`
   - And I see the complete request detail page

2. **Requester information displayed**
   - Given I view the request detail page
   - When I look at the requester section
   - Then I see: Name, Email, Role

3. **Request details displayed**
   - Given I view the request detail page
   - When I look at the request section
   - Then I see: VM Name, Size (with CPU/RAM specs), Project, Justification
   - And the justification is fully visible (not truncated)

4. **Project context displayed (MVP: Placeholder)**
   - Given I view the request detail page
   - When I look at the project context section
   - Then I see placeholder: "Quota information available in Epic 4"
   - **Note:** Full quota display requires Epic 4 (Story 4.8)

5. **Timeline displayed**
   - Given I view the request detail page
   - When I look at the timeline section
   - Then I see chronological events: Created, any approvals/rejections
   - And each event shows relative time and actor name
   - **Reuse:** `Timeline.tsx`, `TimelineEvent.tsx` from Story 2.8

6. **Requester's recent requests displayed**
   - Given I view the request detail page
   - When I look at the requester context section
   - Then I see up to 5 recent requests from this requester (excluding current)
   - And each shows: VM Name, Status, Date

7. **Approve/Reject buttons visible (MVP: Disabled)**
   - Given I view the request detail page
   - When I see the action buttons
   - Then "Approve" and "Reject" buttons are visible but disabled
   - And a tooltip explains "Available in Story 2.11"
   - **Note:** FR26 requires actionable buttons; Story 2.11 activates them

8. **Back button navigation**
   - Given I am on the request detail page
   - When I click the back button
   - Then I return to the Admin Approval Queue

9. **Loading states**
   - Given the request is loading
   - When I navigate to the detail page
   - Then I see a centered loading spinner

10. **Error handling**
    - Given the request ID is invalid or from another tenant
    - When I navigate to `/admin/requests/{invalid-id}`
    - Then I see a "Request not found" error page
    - And I see a link back to Admin Queue

## Test Plan

### Backend Unit Tests

**GetVmRequestDetailHandler (dvmm-application):**
- Returns full request details for valid ID in admin's tenant
- Returns `NotFound` error for non-existent request ID
- Returns `Forbidden` error for request from different tenant
- Includes requester's recent requests (max 5, excluding current)
- Includes timeline events sorted chronologically
- Maps DB entities to DTOs correctly (hiding internal IDs)

**VmRequestProjectionRepository (dvmm-infrastructure):**
- `findByIdWithDetails` returns full request with joins
- `findRecentRequestsByRequesterId` returns max 5 recent requests
- RLS filter applied automatically
- Excludes current request from requester history

### Backend Integration Tests

- Full flow: Admin fetches request detail from own tenant
- Tenant isolation: Admin A cannot see request from Tenant B (404 - prevents enumeration)
- Non-existent request returns 404
- Requester history correctly excludes current request
- Timeline events included in response

### Frontend Unit Tests

**useAdminRequestDetail hook:**
- Fetches from GET /api/admin/requests/{id}
- Returns loading state
- Returns error state for 404
- Includes requesterHistory and timeline in response

**AdminRequestDetail component:**
- Renders requester section with name, email, role
- Renders request section with VM details
- Renders project context placeholder
- Renders timeline (reuses Story 2.8 components)
- Renders requester history card
- Shows loading spinner
- Shows error for 404 with back link
- Back button navigates to queue
- **NO `useMemo`/`useCallback` allowed** (React Compiler rule)

### E2E Tests (Playwright)

- Happy path: Admin clicks request in queue -> sees full details
- Back button: Navigate to detail -> click back -> return to queue
- Timeline displays: Seed request with events -> verify timeline renders
- Error handling: Navigate to invalid ID -> see error page
- Requester history: User with multiple requests -> verify history shows

## Structure Alignment / Previous Learnings

### Learnings from Story 2.9 (Admin Approval Queue)

- **API patterns:** `@PreAuthorize("hasRole('ADMIN')")` for endpoints
- **TanStack Query:** `useQuery` with error handling for 403/404
- **Error handling:** `Result<T, E>` pattern with sealed classes
- **Component structure:** Admin-specific components in `components/admin/`

### Reusable Components from Story 2.8

- `Timeline.tsx` - Timeline container component
- `TimelineEvent.tsx` - Individual event display with icons/colors
- `TimelineSkeleton.tsx` - Loading state
- `useRequestTimeline.ts` - Data fetching hook pattern
- `date-fns` - Relative time formatting

### Domain Events (Already Implemented)

```kotlin
// From dvmm-domain - events already exist
VmRequestCreated
VmRequestApproved
VmRequestRejected
VmRequestCancelled
```

### API Endpoint

```text
GET /api/admin/requests/{id} - Get request detail for admin
    Path param: id (UUID)
    Response: VmRequestDetailResponse
    Authorization: ADMIN role required
    Errors: 404 (both NotFound and Forbidden - security pattern)
```

## Tasks / Subtasks

### Phase 1: Backend - Query Handler

- [x] **Task 1.1: Create GetVmRequestDetailQuery & Handler** (AC: 1, 2, 3, 5, 6, 10)
  - Create `GetVmRequestDetailQuery.kt` with `requestId` and `tenantId`
  - Create `GetVmRequestDetailHandler.kt` in `dvmm-application`
  - Return `Result<VmRequestDetailResponse, GetVmRequestDetailError>`
  - **Error types (CRITICAL - per CLAUDE.md architecture rules):**
    ```kotlin
    sealed class GetVmRequestDetailError {
        data object NotFound : GetVmRequestDetailError()
        data object Forbidden : GetVmRequestDetailError()
    }
    ```

- [x] **Task 1.2: Extend Repository with findByIdWithDetails** (AC: 2, 3, 5)
  - Add `findByIdWithDetails(requestId, tenantId)` to `VmRequestReadRepository`
  - jOOQ query joining VM_REQUESTS with timeline events
  - Ensure RLS is applied (tenant_id filter)

- [x] **Task 1.3: Add findRecentRequestsByRequesterId** (AC: 6)
  - Add `findRecentRequestsByRequesterId(tenantId, requesterId, excludeId, limit=5)` to repository
  - Return recent requests excluding current request
  - Order by `createdAt` descending

- [x] **Task 1.4: Define DTOs** (AC: 2, 3, 5, 6)
  - Create `VmRequestDetailResponse.kt`:
    ```kotlin
    data class VmRequestDetailResponse(
        val id: UUID,
        val vmName: String,
        val size: VmSizeDto,
        val projectName: String,
        val justification: String,
        val status: String,
        val createdAt: Instant,
        val requester: RequesterDto,
        val timeline: List<TimelineEventDto>,
        val requesterHistory: List<VmRequestSummaryDto>
    )
    ```
  - Create `TimelineEventDto.kt` (matching Story 2.8):
    ```kotlin
    data class TimelineEventDto(
        val id: UUID,
        val eventType: String,  // CREATED, APPROVED, REJECTED, CANCELLED
        val actorName: String,  // Resolved name, not ID
        val details: Map<String, Any>? = null,
        val occurredAt: Instant
    )
    ```
  - Create `RequesterDto.kt`:
    ```kotlin
    data class RequesterDto(
        val id: UUID,
        val name: String,
        val email: String,
        val role: String
    )
    ```

- [x] **Task 1.5: Write Backend Tests** (AC: All)
  - **Unit tests for GetVmRequestDetailHandler:**
    - Returns full details for valid ID in tenant
    - Returns `NotFound` for non-existent ID
    - Returns `Forbidden` for request from different tenant
    - Includes requester history (max 5)
    - Excludes current request from history
    - Includes timeline events sorted by date
  - **Integration tests:**
    - Tenant isolation verified
    - 404 response for missing request
    - Requester history correctly populated
    - Timeline events from projection

### Phase 2: Backend - API Endpoint

- [x] **Task 2.1: Add Controller Endpoint** (AC: 1, 10)
  - Add `GET /api/admin/requests/{id}` to `AdminRequestController`
  - `@PreAuthorize("hasRole('ADMIN')")`
  - Map handler errors to HTTP: **Both NotFound and Forbidden -> 404** (see Dev Notes: Security)

### Phase 3: Frontend - API & Hooks

- [x] **Task 3.1: Add API Function** (AC: 1, 2)
  - Extend `src/api/admin.ts` with `getVmRequestDetail(id: string)`
  - Define `VmRequestDetail` TypeScript type matching backend DTO

- [x] **Task 3.2: Create useAdminRequestDetail Hook** (AC: 1, 9)
  - Create `src/hooks/useAdminRequestDetail.ts`
  - Use `useQuery` with `queryKey: ['admin', 'request', id]`
  - Handle loading and error states

### Phase 4: Frontend - Components

- [x] **Task 4.1: Create AdminRequestDetail Page** (AC: 1-10)
  - Create `src/pages/admin/RequestDetail.tsx`
  - Sections: Requester, Request, Project Context, Timeline, History, Actions
  - **Reuse from Story 2.8:** `Timeline`, `TimelineEvent` components
  - Handle loading with centered spinner
  - Handle error with back link
  - **NO `useMemo`/`useCallback` allowed**

- [x] **Task 4.2: Create RequestDetailHeader** (AC: 2, 3)
  - Create `src/components/admin/RequestDetailHeader.tsx`
  - Display requester info and request details
  - Justification fully visible (no truncation)

- [x] **Task 4.3: Create RequesterHistoryCard** (AC: 6)
  - Create `src/components/admin/RequesterHistoryCard.tsx`
  - Display up to 5 recent requests
  - Each shows: VM Name, Status badge, Date

- [x] **Task 4.4: Add Route & Back Navigation** (AC: 1, 8)
  - Add route `/admin/requests/:id` to `App.tsx`
  - Protected by `AdminProtectedRoute`
  - Back button navigates to `/admin/requests`

### Phase 5: E2E Tests

- [x] **Task 5.1: Playwright Tests** (AC: 1-10)
  - Test admin navigates from queue to detail
  - Test back button returns to queue
  - Test timeline displays with events
  - Test requester history displays
  - Test error handling for invalid ID
  - Test loading states

## Dev Notes

### Architecture Compliance

This story follows CLAUDE.md architecture rules. Key constraints:
- CQRS read-side query (no command handling)
- RLS tenant isolation via TenantContext
- `@PreAuthorize("hasRole('ADMIN')")` for endpoint
- Detail handlers MUST have `Forbidden` error type
- NO React manual memoization

### Project Quota Context (Deferred to Epic 4)

AC mentions "Project context: Current usage, Quota remaining" but this requires Epic 4 (Story 4.8).

**For MVP:** Display placeholder text "Quota information available in Epic 4" in the project context section. The UI scaffold will be in place for Epic 4 to fill in.

### Error Handling Pattern

```kotlin
sealed class GetVmRequestDetailError {
    data object NotFound : GetVmRequestDetailError()
    data object Forbidden : GetVmRequestDetailError()
}

// In controller - SECURITY: Both errors return 404 to prevent tenant enumeration
when (result) {
    is Success -> ResponseEntity.ok(result.value)
    is Failure -> {
        // Log actual error for audit trail
        logger.warn { "Request detail access error: ${result.error} for requestId=$requestId" }
        // Return 404 for BOTH to prevent information leakage (see CLAUDE.md Security Patterns)
        ResponseEntity.notFound().build()
    }
}
```

**Security Note:** Per CLAUDE.md "Security Patterns (Multi-Tenant)", both `NotFound` and `Forbidden` errors return HTTP 404. This prevents attackers from discovering that a resource exists in another tenant via 403 responses.

### Requester History Query

```kotlin
// Repository method
suspend fun findRecentRequestsByRequesterId(
    tenantId: TenantId,
    requesterId: UUID,
    excludeRequestId: UUID,
    limit: Int = 5
): List<VmRequestSummary>

// SQL (jOOQ)
SELECT id, vm_name, status, created_at
FROM vm_requests
WHERE tenant_id = :tenantId
  AND requester_id = :requesterId
  AND id != :excludeRequestId
ORDER BY created_at DESC
LIMIT :limit
```

### UI Text

| Key | Text |
|-----|------|
| Page title | Request Details |
| Back button | Back to Pending Requests |
| Requester section | Requester Information |
| Request section | Request Details |
| Project section | Project Context |
| Timeline section | Request Timeline |
| History section | Recent Requests |
| Empty history | No previous requests from this user. |
| Quota placeholder | Quota information available in Epic 4 |
| Approve button | Approve |
| Reject button | Reject |
| Button tooltip | Available in Story 2.11 |

### Story-Specific Libraries

| Library | Version | Usage |
|---------|---------|-------|
| React | 19.2 | React Compiler (NO manual memoization) |
| TanStack Query | 5.x | `useQuery` for data fetching |
| date-fns | 3.6+ | Relative time formatting (reuse Story 2.8) |
| shadcn/ui | - | Card, Separator, Skeleton components |

## File List

### New Files to Create (Backend)

```text
dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmrequest/
├── GetVmRequestDetailQuery.kt
└── GetVmRequestDetailHandler.kt

dvmm/dvmm-application/src/test/kotlin/de/acci/dvmm/application/vmrequest/
└── GetVmRequestDetailHandlerTest.kt

dvmm/dvmm-api/src/main/kotlin/de/acci/dvmm/api/admin/
├── VmRequestDetailResponse.kt
├── RequesterDto.kt
└── TimelineEventDto.kt

dvmm/dvmm-api/src/test/kotlin/de/acci/dvmm/api/admin/
└── AdminRequestDetailIntegrationTest.kt
```

### New Files to Create (Frontend)

```text
dvmm/dvmm-web/
├── src/
│   ├── hooks/
│   │   ├── useAdminRequestDetail.ts
│   │   └── useAdminRequestDetail.test.tsx
│   └── pages/
│       └── admin/
│           ├── RequestDetail.tsx
│           └── RequestDetail.test.tsx
└── e2e/
    └── admin-request-detail.spec.ts
```

### Files to Modify (Existing)

| File | Changes Required |
|------|------------------|
| `AdminRequestController.kt` | Add GET /{id} endpoint |
| `VmRequestReadRepository.kt` | Add findByIdWithDetails, findRecentRequestsByRequesterId |
| `VmRequestReadRepositoryAdapter.kt` | Implement new repository methods |
| `App.tsx` | Add route for `/admin/requests/:id` |
| `api/admin.ts` | Add getVmRequestDetail function |

## References

- [Source: docs/epics.md#Story-2.10-Request-Detail-View-Admin]
- [Source: docs/sprint-artifacts/2-9-admin-approval-queue.md]
- [Source: docs/sprint-artifacts/2-8-request-status-timeline.md]
- [CLAUDE.md#Zero-Tolerance-Policies]
- [date-fns formatDistanceToNow](https://date-fns.org/v3.6.0/docs/formatDistanceToNow)

## Dev Agent Record

### Context Reference

<!-- Path(s) to story context XML will be added by SM during dev-story workflow -->

### Agent Model Used

Claude Opus 4.5 (validation), Story draft by previous session

### Debug Log References

<!-- Debug logs will be added during implementation -->

### Completion Notes List

- Story validated and enhanced by Scrum Master (2025-12-03)
- **Backlog Suggestion (Epic 5):** Consider breadcrumb navigation for admin detail pages (e.g., "Admin > Pending Requests > Request Details") to improve UX when navigating via direct links or notifications
- Added error types per CLAUDE.md architecture rules
- Added requester history implementation (Task 1.3)
- Added comprehensive test scenarios
- Added File List section
- Condensed architecture/library sections for LLM efficiency
- Added Story 2.8 component reuse references

## Change Log

| Date | Change |
|------|--------|
| 2025-12-03 | Initial draft created |
| 2025-12-03 | Validation: Added error types (NotFound, Forbidden) per CLAUDE.md |
| 2025-12-03 | Validation: Added Task 1.3 for requester history query |
| 2025-12-03 | Validation: Added comprehensive test scenarios to Tasks 1.5, 5.1 |
| 2025-12-03 | Validation: Added File List section with all new files |
| 2025-12-03 | Validation: Added Project Quota Context deferral note |
| 2025-12-03 | Validation: Added Story 2.8 component reuse references |
| 2025-12-03 | Validation: Condensed Architecture/Library sections (~300 tokens saved) |
| 2025-12-03 | Fix: Aligned all error mappings to 404 per security pattern |
| 2025-12-03 | Fix: Updated integration test expectation (404 not 403) |
| 2025-12-03 | Fix: Added library version table with React 19, TanStack Query 5 |
| 2025-12-03 | Fix: Checked Timeline components in preflight (Story 2.8 dependency) |
| 2025-12-03 | Fix: Clarified Approve/Reject disabled state references FR26/Story 2.11 |
| 2025-12-03 | Fix: Corrected polling comment from AC-4 to FR44/NFR-PERF-8 |
| 2025-12-03 | Fix: Added TypeScript type guard comment for data null check |
| 2025-12-03 | Doc: Added breadcrumb navigation as Epic 5 backlog suggestion |
| 2025-12-03 | Doc: Updated hook name to useAdminRequestDetail (matching implementation) |
| 2025-12-03 | Doc: Updated component name to AdminRequestDetail (matching implementation) |
| 2025-12-03 | Doc: Updated loading state from skeleton to spinner (matching implementation) |
| 2025-12-03 | Doc: Updated file list to match actual created files |
