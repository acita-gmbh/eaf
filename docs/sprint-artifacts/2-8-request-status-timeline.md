# Story 2.8: Request Status Timeline

Status: ready-for-dev

## Story

As an **end user**,
I want to see the complete history of my request,
So that I know exactly what happened and when.

## Requirements Context Summary

- **Epic/AC source:** Story 2.8 in `docs/epics.md` - Request Status Timeline
- **FRs Satisfied:** FR21 (Request timeline with full history), FR44 (Real-time status updates without page refresh)
- **Architecture constraint:** Event-sourced timeline from domain events; SSE or polling for real-time
- **Prerequisites:** Story 2.7 (My Requests List & Cancel)
- **Risk Level:** MEDIUM (real-time updates complexity)
- **Tech Spec Reference:** `docs/sprint-artifacts/tech-spec-epic-2.md` Sections 4.2, 4.3

## Pre-Flight Setup Checklist

Before starting implementation, verify these are complete:

- [x] **Story 2.7 completed:** My Requests page exists with request cards
- [x] **VmRequestProjectionRepository exists:** Query methods available
- [x] **REQUEST_TIMELINE_EVENTS table:** Schema exists in tech spec (may need migration)
- [x] **Domain events exist:** VmRequestCreated, VmRequestApproved, VmRequestRejected, VmRequestCancelled
- [ ] **User Name Resolution:** Ensure `TimelineEventProjector` can resolve actor names (see Task 1.4 fix)

## Acceptance Criteria

1. **Request detail page accessible**
   - Given I am on the My Requests page
   - When I click on a request row/card
   - Then I navigate to `/requests/{id}`
   - And I see the request detail page with timeline

2. **Timeline shows chronological events**
   - Given I view a request detail page
   - When I look at the timeline section
   - Then I see chronological events sorted oldest to newest:
     - "Request created" - timestamp, my name
     - "Approved" / "Rejected" - timestamp, admin name (CRITICAL: Must show name, not ID), (rejection reason if rejected)
     - "Provisioning started" - timestamp (Epic 3, show if available)
     - "VM ready" - timestamp, VM details (Epic 3, show if available)
     - "Cancelled" - timestamp (if applicable)

3. **Timeline event details**
   - Given I view the timeline
   - When I look at each event
   - Then each event shows relative time ("5 minutes ago") using `de-DE` locale or standard `date-fns` format
   - And clicking an event shows full details (actor name/email, exact timestamp)

4. **Real-time updates via polling**
   - Given I am viewing a request detail page
   - When the request status changes (e.g., approved by admin)
   - Then the timeline updates without page refresh within 5 seconds
   - And a new event appears in the timeline

5. **Request header information**
   - Given I view a request detail page
   - When I look at the header section
   - Then I see: VM Name, Project, Size, Status badge, Justification
   - And I see current status prominently displayed

6. **Cancel button on detail page**
   - Given I view a PENDING request detail page
   - When I see the actions section
   - Then I see a "Cancel Request" button
   - And clicking it shows the confirmation dialog (reuse from Story 2.7)

7. **Navigation back to list**
   - Given I am on the request detail page
   - When I click the back button or breadcrumb
   - Then I return to the My Requests page

8. **Loading states**
   - Given the request is loading
   - When I navigate to the detail page
   - Then I see a skeleton loader for the request header
   - And I see skeleton loaders for timeline events

9. **Error handling**
   - Given the request ID is invalid or not mine
   - When I navigate to `/requests/{invalid-id}`
   - Then I see a "Request not found" error page
   - And I see a link back to My Requests

10. **VMware unavailable graceful degradation**
    - Given a VM request has been approved
    - And VMware vCenter is temporarily unavailable
    - When the system attempts provisioning
    - Then the timeline shows "Provisioning Queued"
    - And a timeline entry shows "Waiting for VMware - you will be notified"

## Test Plan

### Backend Unit Tests

**GetRequestTimelineHandler (dvmm-application):**
- Returns timeline events for valid request ID
- Returns events in chronological order (oldest first)
- Filters by tenant (RLS enforced)
- Throws NotFound for unknown request ID
- Throws Forbidden for other user's request
- Maps DB entities to DTOs correctly (hiding internal IDs)

**TimelineEventProjector (dvmm-infrastructure):**
- Projects VmRequestCreated to timeline event
- Projects VmRequestApproved to timeline event (RESOLVES admin name)
- Projects VmRequestRejected to timeline event (RESOLVES admin name)
- Projects VmRequestCancelled to timeline event
- Handles duplicate events idempotently (INSERT ON CONFLICT DO NOTHING)

### Backend Integration Tests

- Full flow: Create request -> Events appear in timeline
- Approve request -> New event added to timeline with correct actor name
- Tenant isolation: User A cannot see User B's timeline
- Timeline projection updates after event store persistence

### Frontend Unit Tests

**useRequestDetail hook:**
- Fetches request by ID from GET /api/requests/{id}
- Returns loading state
- Returns error state for 404
- Supports refetch interval for polling (5 seconds)

**useRequestTimeline hook:**
- Fetches timeline from GET /api/requests/{id}/timeline
- Returns loading state
- Returns events in array
- Supports refetch interval for real-time updates

**RequestDetailPage component:**
- Renders request header with all fields
- Renders timeline with events
- Shows cancel button for PENDING status
- Shows back navigation
- Handles loading state with skeletons
- Handles error state with message
- NO `useMemo`/`useCallback` allowed (React Compiler rule)

### E2E Tests (Playwright)

- Happy path: Navigate from list -> Click request -> See timeline
- Timeline updates: Mock SSE/polling -> See new event appear
- Cancel from detail: Click cancel -> See timeline update
- Error handling: Navigate to invalid ID -> See error page

## Structure Alignment / Previous Learnings

### Learnings from Story 2.7 (My Requests List & Cancel)

- **RequestCard component:** Established pattern for displaying request info
- **StatusBadge component:** Reuse for status display in header
- **CancelConfirmDialog:** Reuse for cancel action on detail page
- **API patterns:** GET for queries, POST for mutations
- **TanStack Query:** useQuery for fetching, useMutation for actions, queryClient.invalidateQueries for refresh

### Domain Events (REUSE - Already Implemented)

```kotlin
// From dvmm-domain - events already exist
VmRequestCreated
VmRequestApproved
VmRequestRejected
VmRequestCancelled
```

### Projection Table (From Tech Spec)

```sql
-- REQUEST_TIMELINE_EVENTS table schema from tech-spec-epic-2.md
-- May need Flyway migration if not already created
CREATE TABLE "REQUEST_TIMELINE_EVENTS" (
    "ID" UUID PRIMARY KEY,
    "REQUEST_ID" UUID NOT NULL REFERENCES "VM_REQUESTS"("ID"),
    "TENANT_ID" UUID NOT NULL,
    "EVENT_TYPE" VARCHAR(50) NOT NULL,
    "ACTOR_ID" UUID,
    "ACTOR_NAME" VARCHAR(255),
    "DETAILS" JSONB,
    "OCCURRED_AT" TIMESTAMP WITH TIME ZONE NOT NULL
);
```

### API Endpoints (From Tech Spec)

```text
GET /api/requests/{id}          - Get request details
GET /api/requests/{id}/timeline - Get request timeline events
```

## Tasks / Subtasks

### Phase 1: Backend - Timeline Projection

- [ ] **Task 1.1: Create Flyway migration for REQUEST_TIMELINE_EVENTS** (AC: 2)
  - Create migration file `V00X__create_request_timeline_events.sql`
  - Use H2-compatible DDL (quoted UPPERCASE) in `jooq-init.sql`
  - Run `./gradlew :dvmm:dvmm-infrastructure:generateJooq`

- [ ] **Task 1.2: Create TimelineEvent domain type & Repository** (AC: 2)
  - Create `TimelineEvent.kt` mapping to jOOQ record
  - Create `TimelineEventRepository` with `findByRequestId`
  - Ensure RLS tenant filter is applied automatically

- [ ] **Task 1.3: Create TimelineEventProjector with Actor Resolution** (AC: 2)
  - **CRITICAL FIX:** `VmRequestApproved` events usually only contain `userId`.
  - **Option A (Preferred):** If `UserLookupService` exists, inject it to fetch `actorName`.
  - **Option B (Fallback):** If event payload was updated to include name, use it.
  - **Option C (MVP):** Store `actorId` and `actorName="Unknown"` initially if lookup too complex, but flag as tech debt.
  - Implement `INSERT ... ON CONFLICT DO NOTHING` for idempotency.

### Phase 2: Backend - API Endpoints

- [ ] **Task 2.1: Create GetRequestDetail & GetRequestTimeline Queries** (AC: 1, 2, 3, 5)
  - Create Query classes and Handlers in `dvmm-application`
  - Implement `GetRequestDetailHandler` returning full details
  - Implement `GetRequestTimelineHandler` returning event list sorted by date
  - Enforce ownership/admin checks (403 if forbidden)

- [ ] **Task 2.2: Add Controller Endpoints & Update OpenAPI** (AC: 1, 2, 3, 5)
  - Add endpoints to `VmRequestController`:
    - `GET /api/requests/{id}`
    - `GET /api/requests/{id}/timeline`
  - **CRITICAL:** Update `openapi/v1/paths/vm-requests.yaml` to reflect these new endpoints.

- [ ] **Task 2.3: Create DTOs** (AC: 2, 3, 5)
  - Create `VmRequestDetailResponse`
  - Create `TimelineEventResponse` (ensure `actorName` is included)

### Phase 3: Frontend - API & Hooks

- [ ] **Task 3.1: Add API functions** (AC: 1, 2)
  - Extend `src/api/vm-requests.ts` with `getRequestDetail(id)` and `getRequestTimeline(id)`

- [ ] **Task 3.2: Create React Hooks** (AC: 1, 2, 4, 5, 8)
  - Create `useRequestDetail.ts` and `useRequestTimeline.ts`
  - Use `useQuery` with `refetchInterval: 5000` for polling

### Phase 4: Frontend - Components

- [ ] **Task 4.1: Create RequestDetailPage** (AC: 1, 5, 7, 8, 9)
  - Create `src/pages/RequestDetail.tsx`
  - Implement header, timeline, cancel button (reusing `CancelConfirmDialog`)
  - **NO `useMemo`/`useCallback` allowed**

- [ ] **Task 4.2: Create Timeline Component** (AC: 2, 3)
  - Create `src/components/requests/Timeline.tsx` and `TimelineEvent.tsx`
  - Use `date-fns` (or `Intl.DateTimeFormat` with `de-DE`) for consistent relative time
  - Different icons/colors for event types

- [ ] **Task 4.3: Navigation Updates** (AC: 1, 7)
  - Add route `/requests/:id` to `App.tsx`
  - Update `RequestCard` to navigate on click
  - Add breadcrumb/back navigation

### Phase 5: E2E Tests

- [ ] **Task 5.1: Playwright Tests** (AC: 1-9)
  - Verify navigation flow
  - Verify timeline updates (mock polling)
  - Verify cancel action from detail page

## Dev Notes

### Relative Time Standardization

Ensure consistency across the app. If `de-DE` is the target locale, use:

```typescript
import { formatDistanceToNow } from 'date-fns'
import { de } from 'date-fns/locale'

const relativeTime = formatDistanceToNow(new Date(occurredAt), { addSuffix: true, locale: de })
```

### Timeline Event Icons & Colors

```tsx
const eventIcons = {
  CREATED: CirclePlus,
  APPROVED: CheckCircle,
  REJECTED: XCircle,
  CANCELLED: Ban,
  PROVISIONING_STARTED: Clock,
  VM_READY: Server,
}

const eventColors = {
  CREATED: 'text-blue-500',
  APPROVED: 'text-green-500',
  REJECTED: 'text-red-500',
  CANCELLED: 'text-gray-500',
  PROVISIONING_STARTED: 'text-blue-500',
  VM_READY: 'text-emerald-500',
}
```

### Idempotency Strategy

For `TimelineEventProjector`:

```kotlin
// Handle potential duplicate events during replay
fun project(event: VmRequestEvent) {
    try {
        repository.save(event.toTimelineEvent())
    } catch (e: DuplicateKeyException) {
        logger.info("Skipping duplicate timeline event: ${event.id}")
    }
}
```
Or use `ON CONFLICT DO NOTHING` in the repository query.

### Tech Debt: OpenAPI Specification

**Status:** N/A for this story - infrastructure doesn't exist yet.

Task 2.2 references updating `openapi/v1/paths/vm-requests.yaml`, but the OpenAPI directory structure has not been scaffolded. The architecture docs (docs/architecture.md) describe the planned structure, and PRD lists OpenAPI 3.0+ as MVP requirement (NFR-COMPAT-7), but no dedicated story exists to create this infrastructure.

**Recommendation:** Track as a separate chore or add to Epic 1 backlog:
- Create `openapi/v1/` directory structure per architecture spec
- Add base `openapi.yaml` with paths for all existing endpoints
- Set up OpenAPI code generation plugin (already in version catalog)

### Files to Modify (Existing)

| File | Changes Required |
|------|------------------|
| `VmRequestController.kt` | Add GET /{id} and GET /{id}/timeline endpoints |
| `openapi/v1/paths/vm-requests.yaml` | **NEW:** Add API specs for new endpoints |
| `App.tsx` | Add route for /requests/:id |
| `RequestCard.tsx` | Add onClick navigation to detail page |
| `api/vm-requests.ts` | Add API client functions |

### New Files to Create (Backend)

```text
dvmm/dvmm-infrastructure/src/main/resources/db/migration/
└── V00X__create_request_timeline_events.sql

dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/vmrequest/projection/
├── TimelineEvent.kt
├── TimelineEventRepository.kt
└── TimelineEventProjector.kt

dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmrequest/
├── GetRequestDetailQuery.kt
├── GetRequestDetailHandler.kt
├── GetRequestTimelineQuery.kt
└── GetRequestTimelineHandler.kt

dvmm/dvmm-api/src/main/kotlin/de/acci/dvmm/api/vmrequest/
├── VmRequestDetailResponse.kt
└── TimelineEventResponse.kt
```

### New Files to Create (Frontend)

```text
dvmm/dvmm-web/src/
├── hooks/
│   ├── useRequestDetail.ts
│   └── useRequestTimeline.ts
├── pages/
│   └── RequestDetail.tsx
├── components/
│   ├── requests/
│   │   ├── RequestHeader.tsx
│   │   ├── Timeline.tsx
│   │   ├── TimelineEvent.tsx
│   │   └── TimelineSkeleton.tsx
│   └── errors/
│       └── NotFoundError.tsx
```

## References

- [Source: docs/epics.md#Story-2.8-Request-Status-Timeline]
- [Source: docs/sprint-artifacts/tech-spec-epic-2.md#Section-4.2-API-Design]
- [Source: docs/sprint-artifacts/tech-spec-epic-2.md#Section-4.3-Database-Schema]
- [Source: docs/sprint-artifacts/2-7-my-requests-list-cancel.md]
- [date-fns formatDistanceToNow](https://date-fns.org/v3.6.0/docs/formatDistanceToNow)
- [shadcn/ui Skeleton](https://ui.shadcn.com/docs/components/skeleton)
- [CLAUDE.md#Zero-Tolerance-Policies]