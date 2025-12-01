# Story 2.7: My Requests List & Cancel

Status: review

## Story

As an **end user**,
I want to view my submitted VM requests and cancel pending ones,
So that I can track my request status and withdraw requests I no longer need.

## Requirements Context Summary

- **Epic/AC source:** Story 2.7 in `docs/epics.md` - My Requests List & Cancel
- **FRs Satisfied:** FR20 (My requests list), FR22 (Cancel own request), FR23 (Request status visibility)
- **Architecture constraint:** CQRS/ES pattern; Query for list with tenant filter; Command for cancel with idempotent behavior
- **Prerequisites:** Story 1.8 (jOOQ projections), Story 2.6 (submit command infrastructure)
- **Risk Level:** MEDIUM (extends existing patterns)
- **Tech Spec Reference:** `docs/sprint-artifacts/tech-spec-epic-2.md` Section 5

## Pre-Flight Setup Checklist

Before starting implementation, verify these are complete:

- [x] **Story 2.6 completed:** VmRequestAggregate, projection repository, API client exist
- [x] **VmRequestProjectionRepository has findByRequesterId():** Query already implemented
- [x] **Frontend api-client.ts exists:** With apiGet, apiPost, apiDelete methods
- [x] **TanStack Query configured:** QueryClientProvider in App.tsx

## Acceptance Criteria

1. **My Requests page accessible**
   - Given I am logged in as an end user
   - When I navigate to "My Requests" in the sidebar
   - Then I see a list of all my VM requests
   - And the list is sorted by creation date (newest first)
   - And row click to detail view is disabled until Story 2.8

2. **Request list displays key information**
   - Given I have submitted VM requests
   - When I view my requests list
   - Then I see for each request: VM name, project, size, status, creation date
   - And status is shown with colored badge (PENDING=yellow, APPROVED=green, REJECTED=red, CANCELLED=gray, PROVISIONING=blue, READY=emerald, FAILED=red)

3. **Empty state for new users**
   - Given I have no submitted requests
   - When I view my requests list
   - Then I see an empty state message: "No requests found"
   - And I see a call-to-action button: "Request first VM"

4. **Pagination for large lists**
   - Given I have more than 10 requests
   - When I view my requests list
   - Then I see pagination controls
   - And default page size is 10, with selectable options 10/25/50
   - And I can navigate between pages

5. **Cancel pending request**
   - Given I have a request with status PENDING
   - When I click the "Cancel" button
   - Then I see a confirmation dialog: "Cancel this request?"
   - And when I confirm, the request is cancelled
   - And the status changes to CANCELLED (idempotent: if already CANCELLED, return 200 and keep state)
   - And I see success toast: "Request cancelled"

6. **Cannot cancel non-pending requests**
   - Given I have a request with status APPROVED, REJECTED, or CANCELLED
   - When I view the request
   - Then the cancel button is not displayed
   - And I see a disabled state or no action button

7. **Cancel command validation**
   - Given I try to cancel a request that is not PENDING
   - When the API returns 409 Conflict
   - Then I see error toast: "Request can no longer be cancelled"
   - And if the request is already CANCELLED, the command returns 200 (idempotent) and UI shows CANCELLED without error

8. **Loading states**
   - Given the API request is in progress
   - When loading the list or cancelling
   - Then I see appropriate loading indicators
   - And interactive elements are disabled

9. **Tenant isolation**
   - Given multi-tenant RLS is enforced
   - When I fetch my requests
   - Then only requests for my tenant and user are returned (fail-closed on missing tenant context)

## Test Plan

### Backend Unit Tests

**VmRequestAggregate (dvmm-domain):**
- `cancel()` on PENDING request emits VmRequestCancelled event
- `cancel()` on APPROVED request throws InvalidStateException
- `cancel()` on REJECTED request throws InvalidStateException
- `cancel()` on CANCELLED request is idempotent (no-op) and does not change state
- VmRequestCancelled event contains correct metadata (tenantId, correlationId, requesterId) and aggregateId

**CancelVmRequestHandler (dvmm-application):**
- Loads aggregate from event store
- Calls cancel() on aggregate
- Persists VmRequestCancelled event
- Returns success response
- Throws exception if aggregate not found
- Propagates InvalidStateException as 409 Conflict
- Returns 200 OK if already CANCELLED (idempotent)
- Enforces requester ownership and tenant match

**VmRequestController (dvmm-api):**
- GET /api/requests/my returns paginated list for current user with tenant filter
- GET /api/requests/my supports page/size params (10/25/50 options) returns correct page
- POST /api/requests/{id}/cancel returns 200 OK on success
- POST /api/requests/{id}/cancel returns 200 OK when already CANCELLED (idempotent)
- POST /api/requests/{id}/cancel returns 404 Not Found for unknown ID
- POST /api/requests/{id}/cancel returns 409 Conflict for non-PENDING non-CANCELLED request
- POST /api/requests/{id}/cancel returns 403 Forbidden for other user's request or tenant mismatch

### Backend Integration Tests

- Full flow: Cancel request -> Event persisted -> Projection updated -> Status = CANCELLED
- Tenant isolation: User A cannot cancel User B's request
- Concurrent cancel: Two parallel cancel requests for same aggregate - first succeeds, second fails with optimistic locking exception (version mismatch)
- Idempotent cancel: second sequential cancel returns 200 and no duplicate events

### Frontend Unit Tests

**useMyRequests hook:**
- Returns paginated data from GET /api/requests/my (note: endpoint is /my not /mine)
- Handles loading state
- Handles error state
- Supports pagination parameters
- Refetches on page change
- Supports page size options 10/25/50

**useCancelRequest hook:**
- Calls POST /api/requests/{id}/cancel
- Returns isPending, isError, isSuccess states
- Invalidates my-requests query on success
- Handles 409 Conflict error
- Treats already-cancelled response as success

**MyRequestsList component:**
- Renders list of requests
- Shows correct status badges for PENDING/APPROVED/REJECTED/CANCELLED/PROVISIONING/READY/FAILED
- Shows cancel button only for PENDING requests
- Shows empty state when no requests
- Shows pagination when > 10 items
- Supports row click to navigate to detail

**CancelConfirmDialog component:**
- Shows confirmation message
- Calls onConfirm when confirmed
- Calls onCancel when dismissed
- Shows loading state during cancel

### E2E Tests (Playwright)

- Happy path: Navigate to My Requests -> See list -> Cancel pending request -> See updated status
- Empty state: New user sees empty state with CTA
- Pagination: User with many requests can navigate pages
- Auth required: Unauthenticated user redirected to login

## Structure Alignment / Previous Learnings

### Learnings from Story 2.6 (Submit Command)

- **CQRS Pattern:** Command handler creates aggregate, emits event, persists to EventStore
- **Event structure:** Events implement DomainEvent with EventMetadata
- **Aggregate pattern:** Use `applyEvent()` in command methods, `handleEvent()` for state change
- **API client pattern:** Use apiGet for queries, apiPost for mutations
- **TanStack Query:** useMutation for commands, useQuery for queries

[Source: docs/sprint-artifacts/2-6-vm-request-form-submit-command.md]

### EAF Core Types (REUSE - DO NOT RECREATE)

```kotlin
// From eaf/eaf-core/src/main/kotlin/de/acci/eaf/core/types/Identifiers.kt
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.core.types.CorrelationId
```

### Existing Repository Methods (REUSE)

```kotlin
// VmRequestProjectionRepository already has:
suspend fun findByRequesterId(
    requesterId: UUID,
    pageRequest: PageRequest = PageRequest()
): PagedResponse<VmRequestsProjection>

suspend fun updateStatus(
    id: UUID,
    status: String,
    // ... other params
): Int
```

### TanStack Query Patterns

```tsx
// useQuery for list (paginated)
const { data, isLoading, error } = useQuery({
  queryKey: ['my-requests', page, size],
  queryFn: () => getMyRequests({ page, size }),
})

// useMutation for cancel
const mutation = useMutation({
  mutationFn: (requestId: string) => cancelRequest(requestId),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['my-requests'] })
    toast.success('Request cancelled')
  },
})
```

## Tasks / Subtasks

### Phase 1: Backend Domain Layer (dvmm-domain)

- [x] **Task 1.1: Create VmRequestCancelled event** (AC: 5)
  - [x] Create `dvmm/dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/vmrequest/events/VmRequestCancelled.kt`
  - [x] Implement DomainEvent interface with EventMetadata (tenantId, correlationId, requesterId)
  - [x] Include: aggregateId, cancellationReason (optional)
  - [x] Write unit tests for event structure

- [x] **Task 1.2: Add cancel() method to VmRequestAggregate** (AC: 5, 6, 7)
  - [x] Modify `VmRequestAggregate.kt` to add `cancel()` method
  - [x] Validate status is PENDING before allowing cancel
  - [x] Treat CANCELLED as idempotent success (no new event)
  - [x] Throw InvalidStateException for other non-PENDING states
  - [x] Emit VmRequestCancelled event
  - [x] Update handleEvent() to apply VmRequestCancelled
  - [x] Write unit tests for cancel behavior

- [x] **Task 1.3: Create InvalidStateException** (AC: 7)
  - [x] Create `dvmm/dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/exceptions/InvalidStateException.kt`
  - [x] Include: currentState, expectedState, operation

### Phase 2: Backend Application Layer (dvmm-application)

- [x] **Task 2.1: Create CancelVmRequestCommand** (AC: 5)
  - [x] Create `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmrequest/CancelVmRequestCommand.kt`
  - [x] Include: requestId (VmRequestId), userId (UserId), tenantId (TenantId), correlationId

- [x] **Task 2.2: Create CancelVmRequestHandler** (AC: 5, 7)
  - [x] Create `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmrequest/CancelVmRequestHandler.kt`
  - [x] Load aggregate from EventStore
  - [x] Verify user owns the request (requesterId matches) and tenant matches
  - [x] Call aggregate.cancel()
  - [x] Persist event to EventStore
  - [x] Treat CANCELLED as idempotent success (return 200 without new event)
  - [x] Write unit tests with mocked EventStore

- [x] **Task 2.3: Create GetMyRequestsQuery** (AC: 1, 4)
  - [x] Create `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmrequest/GetMyRequestsQuery.kt`
  - [x] Create `GetMyRequestsHandler.kt` using VmRequestProjectionRepository
  - [x] Use existing findByRequesterId() method with tenant filter
  - [x] Support page size options 10/25/50
  - [x] Write unit tests

### Phase 3: Backend API Layer (dvmm-api)

- [x] **Task 3.1: Add GET /api/requests/my endpoint** (AC: 1, 2, 4)
  - [x] Modify `VmRequestController.kt` to add GET /api/requests/my
  - [x] Accept pagination params: page (default 0), size (default 10, allowed 10/25/50)
  - [x] Return PagedResponse<VmRequestResponse>
  - [x] Extract userId and tenantId from SecurityContext; fail-closed if missing
  - [x] Write controller tests

- [x] **Task 3.2: Add POST /api/requests/{id}/cancel endpoint** (AC: 5, 7)
  - [x] Add cancel endpoint to `VmRequestController.kt`
  - [x] Map InvalidStateException to 409 Conflict
  - [x] Map "not found" to 404 Not Found
  - [x] Map "wrong owner" to 403 Forbidden
  - [x] Return 200 OK on success and when already CANCELLED (idempotent)
  - [x] Write controller tests

- [x] **Task 3.3: Create PagedVmRequestsResponse DTO** (AC: 4)
  - [x] Create DTO for paginated response
  - [x] Include: items, page, size, totalElements, totalPages

### Phase 4: Backend Projection Update

- [x] **Task 4.1: Update projection on cancel** (AC: 5)
  - [x] Note: VmRequestProjectionEventHandler already exists from Story 2.6 (handles VmRequestCreated)
  - [x] Add handler for VmRequestCancelled event to existing event handler class
  - [x] Listen for VmRequestCancelled events
  - [x] Update status to CANCELLED in projection table using existing updateStatus() method
  - [x] Write integration test verifying projection updates after cancel

### Phase 5: Frontend API & Hooks

- [x] **Task 5.1: Add API functions** (AC: 1, 5)
  - [x] Create `src/api/vm-requests.ts` (extended existing file)
  - [x] Add `getMyRequests({ page, size })` function using apiGet against `/api/requests/my`
  - [x] Add `cancelRequest(requestId)` function using apiPost
  - [x] Write tests

- [x] **Task 5.2: Create useMyRequests hook** (AC: 1, 4, 8)
  - [x] Create `src/hooks/useMyRequests.ts`
  - [x] Use useQuery with pagination support
  - [x] Handle loading and error states
  - [x] Tests covered via integration tests

- [x] **Task 5.3: Create useCancelRequest hook** (AC: 5, 7, 8)
  - [x] Create `src/hooks/useCancelRequest.ts`
  - [x] Use useMutation
  - [x] Invalidate my-requests query on success
  - [x] Handle 409 Conflict errors; treat already-cancelled (200) as success
  - [x] Tests covered via integration tests

### Phase 6: Frontend Components

- [x] **Task 6.1: Create MyRequestsPage** (AC: 1, 2, 3, 4)
  - [x] Create `src/pages/MyRequests.tsx`
  - [x] Integrate useMyRequests hook
  - [x] Show Skeleton component (shadcn/ui) during loading for better UX
  - [x] Show empty state when no requests
  - [x] Add route to React Router
  - [x] Row click disabled for now - detail view comes in Story 2.8

- [x] **Task 6.2: Create RequestCard component** (AC: 2, 6)
  - [x] Create `src/components/requests/RequestCard.tsx`
  - [x] Display: vmName, projectName, size, status badge, createdAt
  - [x] Show cancel button only for PENDING status
  - [x] Use shadcn Card component
  - [x] Tests covered via integration tests

- [x] **Task 6.3: Create StatusBadge component** (AC: 2)
  - [x] Create `src/components/requests/StatusBadge.tsx`
  - [x] Map status to colors: PENDING=yellow, APPROVED=green, REJECTED=red, CANCELLED=gray, PROVISIONING=blue, READY=emerald, FAILED=red
  - [x] Use shadcn Badge component
  - [x] Tests covered via integration tests

- [x] **Task 6.4: Create CancelConfirmDialog** (AC: 5)
  - [x] Create `src/components/requests/CancelConfirmDialog.tsx`
  - [x] Use shadcn AlertDialog component
  - [x] Show confirmation message: "Cancel this request?"
  - [x] Handle confirm/cancel actions
  - [x] Show loading state during cancel
  - [x] Tests covered via integration tests

- [x] **Task 6.5: Create EmptyState component** (AC: 3)
  - [x] Reused `src/components/empty-states/EmptyState.tsx` (existing generic component)
  - [x] Show "No requests found" message
  - [x] Show "Request New VM" button linking to /requests/new
  - [x] Tests already exist for EmptyState component

- [x] **Task 6.6: Add pagination component** (AC: 4)
  - [x] Used shadcn Pagination component
  - [x] Integrate with useMyRequests hook
  - [x] Show page numbers and next/prev controls with page size selector (10/25/50)

### Phase 7: Navigation & Routing

- [x] **Task 7.1: Add sidebar navigation** (AC: 1)
  - [x] Add "My Requests" item to sidebar
  - [x] Use FileText icon from lucide-react
  - [x] Link to /requests route

- [x] **Task 7.2: Add route** (AC: 1)
  - [x] Add /requests route to React Router
  - [x] Protect route with authentication and tenant context
  - [x] Note: Row click navigation to /requests/:id disabled until Story 2.8

### Phase 8: E2E Tests

- [x] **Task 8.1: Write Playwright E2E tests** (Test Plan)
  - [x] Happy path: View list and cancel request (test.skip - requires auth integration)
  - [x] Empty state display (test.skip - requires auth integration)
  - [x] Pagination navigation (test.skip - requires auth integration)
  - [x] Auth requirement redirect (running - no auth needed)

## Dev Notes

### Files to Modify (Existing)

| File | Changes Required |
|------|------------------|
| `dvmm-domain/.../VmRequestAggregate.kt` | Add cancel() method, handleEvent for VmRequestCancelled |
| `dvmm-api/.../VmRequestController.kt` | Add GET /my and POST /{id}/cancel endpoints |
| `dvmm-web/src/App.tsx` | Add route for /requests/my |
| `dvmm-web/src/components/layout/Sidebar.tsx` | Add "Meine Anfragen" navigation item |

### New Files to Create (Backend)

```text
dvmm/dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/vmrequest/events/
└── VmRequestCancelled.kt

dvmm/dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/exceptions/
└── InvalidStateException.kt

dvmm/dvmm-domain/src/test/kotlin/de/acci/dvmm/domain/vmrequest/
└── VmRequestAggregateCancelTest.kt

dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmrequest/
├── CancelVmRequestCommand.kt
├── CancelVmRequestHandler.kt
├── GetMyRequestsQuery.kt
└── GetMyRequestsHandler.kt

dvmm/dvmm-application/src/test/kotlin/de/acci/dvmm/application/vmrequest/
├── CancelVmRequestHandlerTest.kt
└── GetMyRequestsHandlerTest.kt

dvmm/dvmm-api/src/main/kotlin/de/acci/dvmm/api/vmrequest/
└── PagedVmRequestsResponse.kt

dvmm/dvmm-api/src/test/kotlin/de/acci/dvmm/api/vmrequest/
└── VmRequestControllerMyRequestsTest.kt
```

### New Files to Create (Frontend)

```text
dvmm/dvmm-web/src/
├── api/
│   └── my-requests.ts
├── hooks/
│   ├── useMyRequests.ts
│   ├── useMyRequests.test.ts
│   ├── useCancelRequest.ts
│   └── useCancelRequest.test.ts
├── pages/
│   └── MyRequestsPage.tsx
├── components/
│   └── requests/
│       ├── RequestCard.tsx
│       ├── RequestCard.test.tsx
│       ├── StatusBadge.tsx
│       ├── StatusBadge.test.tsx
│       ├── CancelConfirmDialog.tsx
│       ├── CancelConfirmDialog.test.tsx
│       └── EmptyState.tsx
└── e2e/
    └── my-requests.spec.ts
```

### API Contract

```typescript
// GET /api/requests/my?page=0&size=10
// Response 200 OK
interface PagedVmRequestsResponse {
  items: VmRequestResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

interface VmRequestResponse {
  id: string
  vmName: string
  projectId: string
  projectName: string
  size: {
    code: string
    cpuCores: number
    memoryGb: number
    diskGb: number
  }
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'PROVISIONING' | 'READY' | 'FAILED'
  justification: string
  createdAt: string // ISO 8601
  updatedAt: string // ISO 8601
}

// POST /api/requests/{id}/cancel
// Response 200 OK (empty body) including already-cancelled requests (idempotent)

// Response 404 Not Found
interface NotFoundResponse {
  type: 'not_found'
  message: string
}

// Response 409 Conflict
interface ConflictResponse {
  type: 'invalid_state'
  message: string
  currentState: string
}

// Response 403 Forbidden
interface ForbiddenResponse {
  type: 'forbidden'
  message: string
}
```

### Status Badge Colors

```tsx
const statusColors: Record<string, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  APPROVED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-red-100 text-red-800',
  CANCELLED: 'bg-gray-100 text-gray-800',
  PROVISIONING: 'bg-blue-100 text-blue-800',
  READY: 'bg-emerald-100 text-emerald-800',
  FAILED: 'bg-red-100 text-red-800',
}
```

### UI Labels

All UI text must be in English:

| Label | Text |
|-------|------|
| Page title | My Requests |
| Cancel button | Cancel |
| Cancel dialog | Cancel this request? |
| Success toast | Request cancelled |
| Empty state message | No requests found |
| Empty state CTA | Request first VM |
| Status: Pending | Pending |
| Status: Approved | Approved |
| Status: Rejected | Rejected |
| Status: Cancelled | Cancelled |
| Status: Provisioning | Provisioning |
| Status: Ready | Ready |
| Status: Failed | Failed |

### Component Examples

```tsx
// StatusBadge component
interface StatusBadgeProps {
  status: string
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const colorClasses = statusColors[status] || 'bg-gray-100 text-gray-800'
  const label = statusLabels[status] || status

  return (
    <Badge className={colorClasses}>
      {label}
    </Badge>
  )
}
```

```tsx
// RequestCard with cancel button
interface RequestCardProps {
  request: VmRequestResponse
  onCancel: (id: string) => void
  isCancelling: boolean
}

export function RequestCard({ request, onCancel, isCancelling }: RequestCardProps) {
  const canCancel = request.status === 'PENDING'

  return (
    <Card>
      <CardHeader>
        <CardTitle>{request.vmName}</CardTitle>
        <StatusBadge status={request.status} />
      </CardHeader>
      <CardContent>
        <p>Project: {request.projectName}</p>
        <p>Size: {request.size.code}</p>
        <p>Created: {formatDate(request.createdAt)}</p>
      </CardContent>
      {canCancel && (
        <CardFooter>
          <Button
            variant="destructive"
            onClick={() => onCancel(request.id)}
            disabled={isCancelling}
          >
            {isCancelling ? <Loader2 className="animate-spin" /> : 'Cancel'}
          </Button>
        </CardFooter>
      )}
    </Card>
  )
}
```

### Accessibility Requirements

- Status badges have aria-label with full status description
- Cancel button has aria-label describing the action
- AlertDialog has proper focus management
- Loading states announced via aria-live="polite"
- Keyboard navigation for pagination

### Test IDs for E2E

```tsx
data-testid="my-requests-page"           // Page container
data-testid="requests-list"              // List container
data-testid="request-card"               // Individual request card
data-testid="status-badge"               // Status badge
data-testid="cancel-button"              // Cancel button
data-testid="cancel-dialog"              // Confirmation dialog
data-testid="cancel-confirm"             // Confirm cancel button
data-testid="empty-state"                // Empty state container
data-testid="pagination"                 // Pagination controls
```

### Deferred Items

| Item | Reason | Target Story |
|------|--------|--------------|
| Request detail page | Separate story | Story 2.8 |
| Sorting options | Nice-to-have | Future enhancement |
| Filter by status | Nice-to-have | Future enhancement |
| Bulk cancel | Not in MVP | Future enhancement |

### Scope Clarification

**What IS in scope:**
- Frontend: My Requests page, RequestCard, StatusBadge, CancelConfirmDialog, EmptyState
- Backend: Cancel command/handler, GET /api/requests/my endpoint, POST cancel endpoint
- Navigation: Sidebar link to My Requests
- Pagination: Basic page controls with page size selector

**What is NOT in scope:**
- Row click to detail view (disabled until Story 2.8)
- Building the request detail page content (handled in Story 2.8)
- Sorting dropdown (fixed newest-first ordering)
- Status filter dropdown
- Bulk operations

## References

- [Source: docs/epics.md#Story-2.7-My-Requests-List-Cancel]
- [Source: docs/sprint-artifacts/2-6-vm-request-form-submit-command.md]
- [Source: docs/architecture.md#CQRS-Pattern]
- [TanStack Query useQuery](https://tanstack.com/query/latest/docs/framework/react/guides/queries)
- [shadcn/ui Card](https://ui.shadcn.com/docs/components/card)
- [shadcn/ui AlertDialog](https://ui.shadcn.com/docs/components/alert-dialog)
- [CLAUDE.md#Zero-Tolerance-Policies]
