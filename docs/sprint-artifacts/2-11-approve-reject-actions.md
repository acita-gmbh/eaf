# Story 2.11: Approve/Reject Actions

Status: done

## Story

As an **admin**,
I want to approve or reject requests with one click,
so that I can process requests efficiently.

## Acceptance Criteria

**Scenario 1: Approve Request**
**Given** I am viewing a pending request
**When** I click "Approve"
**Then** confirmation dialog appears: "Approve request?"
**And** on confirm, `ApproveVmRequestCommand` is dispatched with the **current aggregate version**
**And** `VmRequestApproved` event is persisted with my userId, timestamp, and request details
**And** success toast: "Request approved!"
**And** I return to queue (request removed from pending)

**Scenario 2: Reject Request**
**Given** I am viewing a pending request
**When** I click "Reject"
**Then** rejection dialog appears with mandatory reason field
**And** reason must be 10-500 characters (matches `VmRequestCancelled.MAX_REASON_LENGTH`)
**And** on submit, `RejectVmRequestCommand` is dispatched with the **current aggregate version**
**And** `VmRequestRejected` event is persisted with reason, userId, timestamp, and request details
**And** success toast: "Request rejected"
**And** I return to queue

**Scenario 3: Concurrent Modification (Optimistic Locking)**
**Given** the request is already approved/rejected (concurrent admin)
**When** I try to approve/reject using the version I see
**Then** I see error: "Request already processed" (HTTP 409 Conflict)
**And** detail view refreshes showing current state

**Scenario 4: Self-Approval Prevention (Separation of Duties)**
**Given** I am an admin viewing my own pending request
**When** I try to click "Approve" or "Reject"
**Then** I see error: "Cannot approve or reject your own request"
**And** the action is blocked (buttons disabled or server rejects)

## Tasks / Subtasks

- [x] **Domain Layer (dcm-domain)**
  - [x] **Define Events:** Create `VmRequestApproved` and `VmRequestRejected` events in `events/` folder with denormalized fields for notifications (see Event Structure below).
  - [x] **Update Aggregate:** In `VmRequestAggregate.kt`:
    - Add `approve(adminId: UserId, metadata: EventMetadata)` and `reject(adminId: UserId, reason: String, metadata: EventMetadata)` methods
    - Add `is VmRequestApproved` and `is VmRequestRejected` branches to `handleEvent()`
    - Add private `apply(event: VmRequestApproved)` and `apply(event: VmRequestRejected)` methods
  - [x] **Validation:** Use existing `status.canBeActedOnByAdmin()` to ensure only `PENDING` requests can transition.
  - [x] **Self-Approval Check:** Verify `aggregate.requesterId != adminId` before allowing approve/reject.
  - [x] **Unit Tests:** Create `VmRequestAggregateApproveRejectTest.kt` (pattern: `CancelVmRequestHandlerTest.kt`) covering:
    - Approve from PENDING → APPROVED
    - Reject from PENDING → REJECTED with reason
    - Reject with reason < 10 chars throws `IllegalArgumentException`
    - Reject with reason > 500 chars throws `IllegalArgumentException`
    - Approve/Reject from non-PENDING state throws `InvalidStateException`
    - Self-approval throws error

- [x] **Application Layer (dcm-application)**
  - [x] **Create Commands:** `ApproveVmRequestCommand(tenantId, requestId, adminId, version)` and `RejectVmRequestCommand(tenantId, requestId, adminId, reason, version)`.
  - [x] **Define Error Types:** Create `ApproveVmRequestError` and `RejectVmRequestError` sealed classes following `CancelVmRequestError` pattern (NotFound, Forbidden, InvalidState, ConcurrencyConflict, PersistenceFailure).
  - [x] **Implement Handlers:** `ApproveVmRequestHandler` and `RejectVmRequestHandler` following `CancelVmRequestHandler` pattern:
    - Load events → reconstitute aggregate → validate → apply command → persist → update projections
    - **CRITICAL:** Call `timelineUpdater.addTimelineEvent()` after successful persist (see Cross-References)
    - **CRITICAL:** Call `projectionUpdater.updateStatus()` to update VM_REQUESTS projection
  - **Note:** `TimelineEventType.APPROVED` and `TimelineEventType.REJECTED` already exist in enum (no changes needed)
  - [x] **Unit Tests:** Create `ApproveVmRequestHandlerTest.kt` and `RejectVmRequestHandlerTest.kt` covering:
    - Happy path approval/rejection
    - NotFound when request doesn't exist
    - Forbidden when admin tries to approve own request
    - InvalidState when request not PENDING
    - ConcurrencyConflict when version mismatch
    - Timeline event created on success

- [x] **API Layer (dcm-api)**
  - [x] **Add Endpoints:** `POST /api/admin/requests/{id}/approve` and `POST /api/admin/requests/{id}/reject` in `AdminRequestController`.
  - [x] **DTOs:** Create request body classes with validation (see DTO Structure below).
  - [x] **Error Handling:** Map handler errors to HTTP responses (see Controller Error Mapping below).
  - [x] **Security:** Endpoints already in admin controller with `@PreAuthorize("hasRole('ADMIN')")`.

- [x] **Frontend (dcm-web)**
  - [x] **API Client:** Add `approveRequest(id, version)` and `rejectRequest(id, version, reason)` to `api/admin.ts`.
  - [x] **Enable Buttons:** Remove `disabled` state and tooltip from Approve/Reject buttons (currently showing "Available in Story 2.11").
  - [x] **UI Components:** Update `AdminRequestDetail.tsx`:
    - Place "Approve" (Green) and "Reject" (Red) buttons in **Page Header Actions** or **Sticky Footer**.
    - Implement `ApproveConfirmDialog.tsx` (AlertDialog pattern).
    - Implement `RejectFormDialog.tsx` (reason field with 10-500 char validation).
    - Pass `data.version` (from `useAdminRequestDetail` - added in Story 2.10) to API calls.
  - [x] **Mutations:** Create `useApproveRequest` and `useRejectRequest` hooks (see Mutation Pattern below).

- [x] **Testing (Backend)**
  - [x] **Integration Test:** Add approve/reject tests to `AdminRequestControllerIntegrationTest`:
    - POST /approve returns 200 and updates status to APPROVED
    - POST /reject returns 200 with valid reason
    - POST /reject returns 422 if reason < 10 chars
    - Returns 404 for non-existent request
    - Returns 404 when admin tries to approve own request (Forbidden mapped to 404)
  - [x] **Concurrency Test:** Simulate two concurrent coroutines trying to approve the same request; verify one succeeds and the other gets 409 Conflict.

- [x] **Testing (E2E - Playwright)**
  - [x] **Approve Flow:** Admin navigates to pending request → clicks Approve → confirms dialog → sees success toast → redirected to queue → request no longer in pending list
  - [x] **Reject Flow:** Admin clicks Reject → enters reason (≥10 chars) → submits → sees success toast → redirected to queue
  - [x] **Validation:** Reject dialog prevents submission with < 10 char reason
  - [x] **Conflict Handling:** (Optional) Simulate stale version scenario

## Dev Notes

### Valid Status Transitions (from `VmRequestStatus.kt`)

```text
PENDING → APPROVED (via approve)
PENDING → REJECTED (via reject)
PENDING → CANCELLED (via cancel - requester only)
APPROVED → PROVISIONING (future story)
PROVISIONING → READY | FAILED (future story)
```

**Helper method:** `status.canBeActedOnByAdmin()` returns `true` only for `PENDING`.

### Event Structure (Domain Events)

Events must include denormalized fields for Story 2.12 notifications:

```kotlin
// VmRequestApproved.kt - follows VmRequestCancelled pattern
public data class VmRequestApproved(
    val aggregateId: VmRequestId,
    val vmName: VmName,           // Denormalized for notifications
    val projectId: ProjectId,     // Denormalized for notifications
    val requesterId: UserId,      // Denormalized for notifications
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = "VmRequest"
}

// VmRequestRejected.kt
public data class VmRequestRejected(
    val aggregateId: VmRequestId,
    val reason: String,           // Required, 10-500 chars
    val vmName: VmName,           // Denormalized for notifications
    val projectId: ProjectId,     // Denormalized for notifications
    val requesterId: UserId,      // Denormalized for notifications
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = "VmRequest"

    public companion object {
        public const val MIN_REASON_LENGTH: Int = 10
        public const val MAX_REASON_LENGTH: Int = 500  // Same as VmRequestCancelled
    }
}
```

### DTO Structure (API Layer)

```kotlin
// Request bodies with validation
public data class ApproveRequestBody(
    val version: Long
)

public data class RejectRequestBody(
    val version: Long,
    @field:Size(min = 10, max = 500, message = "Reason must be 10-500 characters")
    val reason: String
)
```

### Controller Error Mapping Pattern

```kotlin
// In AdminRequestController - follow security pattern (Forbidden → 404)
return when (val error = result.error) {
    is ApproveVmRequestError.NotFound,
    is ApproveVmRequestError.Forbidden -> ResponseEntity.notFound().build()
    is ApproveVmRequestError.InvalidState -> ResponseEntity.unprocessableEntity()
        .body(ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            error.message
        ))
    is ApproveVmRequestError.ConcurrencyConflict -> ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Request was modified by another admin"
        ).apply { type = URI("/errors/conflict") })
    is ApproveVmRequestError.PersistenceFailure -> {
        logger.error { "Persistence failure: ${error.message}" }
        ResponseEntity.internalServerError().build()
    }
}
```

### Mutation Pattern (Frontend)

```typescript
// useApproveRequest.ts
export function useApproveRequest(id: string) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  return useMutation({
    mutationFn: (version: number) => approveRequest(id, version),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'requests', 'pending'] })
      void queryClient.invalidateQueries({ queryKey: ['admin', 'request', id] })
      toast.success('Request approved!')
      void navigate('/admin/requests')
    },
    onError: (error: ApiError) => {
      if (error.status === 409) {
        toast.error('Request was modified. Refreshing...')
        void queryClient.invalidateQueries({ queryKey: ['admin', 'request', id] })
      } else if (error.status === 422) {
        toast.error(error.message || 'Request cannot be approved in current state')
      } else {
        toast.error('Failed to approve request')
      }
    }
  })
}
```

### Timeline Projection Pattern

Follow `CancelVmRequestHandler.kt:221-238` - handlers MUST update timeline after successful persist:

```kotlin
// For APPROVED
timelineUpdater.addTimelineEvent(
    NewTimelineEvent(
        id = UUID.nameUUIDFromBytes("APPROVED:${correlationId.value}".toByteArray()),
        requestId = command.requestId,
        tenantId = command.tenantId,
        eventType = TimelineEventType.APPROVED,
        actorId = command.adminId,
        actorName = null,  // Resolved at query time
        details = null,
        occurredAt = metadata.timestamp
    )
)

// For REJECTED - include reason in details JSON
timelineUpdater.addTimelineEvent(
    NewTimelineEvent(
        id = UUID.nameUUIDFromBytes("REJECTED:${correlationId.value}".toByteArray()),
        requestId = command.requestId,
        tenantId = command.tenantId,
        eventType = TimelineEventType.REJECTED,
        actorId = command.adminId,
        actorName = null,
        details = objectMapper.writeValueAsString(mapOf("reason" to command.reason)),
        occurredAt = metadata.timestamp
    )
)
```

### Key Implementation Notes

- **Optimistic Locking:** Frontend passes `version` from `AdminRequestDetailResponse` (added in Story 2.10).
- **Self-Approval Prevention:** `aggregate.requesterId != adminId` check in domain layer.
- **UX Pattern:** Place critical action buttons in page header or sticky footer.
- **Error Mapping:** Map `Forbidden` to 404 to prevent tenant enumeration.

### Cross-References from Previous Stories

**From Story 2.10 (Request Detail View):**
- `AdminRequestDetailResponse` includes `version: Long` field - frontend passes this to approve/reject API
- Buttons are currently disabled with tooltip "Available in Story 2.11" - **enable them in this story**

**From Story 2.9 (Admin Approval Queue):**
- `@PreAuthorize("hasRole('ADMIN')")` already on `AdminRequestController`
- Audit logging: `logger.info { "Admin action: tenant=$tenantId, admin=$adminId, action=APPROVE/REJECT, requestId=$requestId" }`

**From Story 2.8 (Request Status Timeline):**
- `TimelineEventType.APPROVED` and `TimelineEventType.REJECTED` ✅ already exist
- Timeline insert uses `ON CONFLICT DO NOTHING` for idempotency
- See "Timeline Projection Pattern" above for implementation details

### Project Structure Notes

- **Domain:** `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/`
- **Domain Events:** `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/events/`
- **Application:** `dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vmrequest/`
- **API:** `dcm/dcm-api/src/main/kotlin/de/acci/dcm/api/admin/`
- **Frontend Pages:** `dcm/dcm-web/src/pages/admin/`
- **Frontend Components:** `dcm/dcm-web/src/components/admin/`
- **Frontend Hooks:** `dcm/dcm-web/src/hooks/`
- **Frontend API:** `dcm/dcm-web/src/api/`
- **Backend Tests:** `dcm/dcm-api/src/test/kotlin/de/acci/dcm/api/admin/`
- **Domain Tests:** `dcm/dcm-domain/src/test/kotlin/de/acci/dcm/domain/vmrequest/`
- **Application Tests:** `dcm/dcm-application/src/test/kotlin/de/acci/dcm/application/vmrequest/`

### References

- [Epic 2: Core Workflow](docs/epics.md#epic-2-core-workflow)
- [PRD: Approval Workflow](docs/prd.md#approval-workflow)
- [Architecture: Command Handlers](docs/architecture.md#decision-3-async-projections--read-your-own-writes)

## Dev Agent Record

### Context Reference

**Previous Stories:**
- `docs/sprint-artifacts/2-10-request-detail-view-admin.md` (version field, disabled buttons)
- `docs/sprint-artifacts/2-9-admin-approval-queue.md` (security, audit logging)
- `docs/sprint-artifacts/2-8-request-status-timeline.md` (timeline projection)

**Pattern References (Implementation):**
- `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/VmRequestAggregate.kt`
- `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/events/VmRequestCancelled.kt` (event structure)
- `dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vmrequest/CancelVmRequestHandler.kt` (handler pattern)
- `dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vmrequest/CancelVmRequestCommand.kt` (command pattern)

**Pattern References (Tests):**
- `dcm/dcm-application/src/test/kotlin/de/acci/dcm/application/vmrequest/CancelVmRequestHandlerTest.kt` (handler test pattern)

### Agent Model Used

Gemini 2.0 Flash

### Completion Notes List

- [x] Backend implementation complete with concurrency protection
- [x] Frontend implementation complete with cache invalidation
- [x] Integration tests passing including 409 Conflict scenario
- [x] E2E tests passing

### File List

**Domain (modify existing + new events):**
- `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/VmRequestAggregate.kt` (update - add approve/reject methods + handleEvent branches)
- `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/events/VmRequestApproved.kt` (new)
- `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/events/VmRequestRejected.kt` (new)

**Application (new commands + handlers):**
- `dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vmrequest/ApproveVmRequestCommand.kt` (new)
- `dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vmrequest/ApproveVmRequestHandler.kt` (new)
- `dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vmrequest/RejectVmRequestCommand.kt` (new)
- `dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vmrequest/RejectVmRequestHandler.kt` (new)

**Infrastructure (deserializer implementation):**
- `dcm/dcm-infrastructure/src/main/kotlin/de/acci/dcm/infrastructure/vmrequest/JacksonVmRequestEventDeserializer.kt` (update - add VmRequestApproved/Rejected cases)

**API (modify existing controller + new DTOs):**
- `dcm/dcm-api/src/main/kotlin/de/acci/dcm/api/admin/AdminRequestController.kt` (update - add approve/reject endpoints)
- `dcm/dcm-api/src/main/kotlin/de/acci/dcm/api/admin/AdminRequestBodies.kt` (new - ApproveRequestBody, RejectRequestBody)

**Frontend (actual paths - not features/):**
- `dcm/dcm-web/src/pages/admin/RequestDetail.tsx` (update - enable buttons, add dialogs)
- `dcm/dcm-web/src/api/admin.ts` (update - add approveRequest/rejectRequest functions)
- `dcm/dcm-web/src/hooks/useApproveRequest.ts` (new)
- `dcm/dcm-web/src/hooks/useRejectRequest.ts` (new)
- `dcm/dcm-web/src/components/admin/ApproveConfirmDialog.tsx` (new)
- `dcm/dcm-web/src/components/admin/RejectFormDialog.tsx` (new)

**Tests (Backend):**
- `dcm/dcm-domain/src/test/kotlin/de/acci/dcm/domain/vmrequest/VmRequestAggregateApproveRejectTest.kt` (new)
- `dcm/dcm-application/src/test/kotlin/de/acci/dcm/application/vmrequest/ApproveVmRequestHandlerTest.kt` (new)
- `dcm/dcm-application/src/test/kotlin/de/acci/dcm/application/vmrequest/RejectVmRequestHandlerTest.kt` (new)
- `dcm/dcm-api/src/test/kotlin/de/acci/dcm/api/admin/AdminRequestControllerIntegrationTest.kt` (update)

**Tests (Frontend):**
- `dcm/dcm-web/src/pages/admin/RequestDetail.test.tsx` (update - approve/reject flow tests)
- `dcm/dcm-web/src/api/admin.test.ts` (update - approve/reject API function tests)
- `dcm/dcm-web/e2e/admin-request-detail.spec.ts` (update - approve/reject E2E scenarios)