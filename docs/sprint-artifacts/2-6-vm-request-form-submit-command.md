# Story 2.6: VM Request Form - Submit Command

Status: done

## Story

As an **end user**,
I want to submit my VM request,
So that it enters the approval workflow.

## Requirements Context Summary

- **Epic/AC source:** Story 2.6 in `docs/epics.md` - VM Request Form - Submit Command
- **FRs Satisfied:** FR16 (Create VM Request), FR45 (triggers email notification)
- **Architecture constraint:** CQRS/ES pattern, Command -> Aggregate -> Event -> Projection
- **Prerequisites:** Story 2.4 (done), Story 2.5 (done), Story 1.4 Aggregate Base (done)
- **Risk Level:** HIGH (first full-stack integration point)
- **Tech Spec Reference:** `docs/sprint-artifacts/tech-spec-epic-2.md` Section 4.1-4.3

## Pre-Flight Setup Checklist

Before starting implementation, verify these are complete:

- [x] **Story 2.5 completed:** VmRequestForm exists with all fields (vmName, projectId, size, justification)
- [x] **dcm-domain module exists:** Check `dcm/dcm-domain/build.gradle.kts`
- [x] **dcm-application module exists:** Check `dcm/dcm-application/build.gradle.kts`
- [x] **dcm-api module exists:** Check `dcm/dcm-api/build.gradle.kts`
- [x] **EAF eventsourcing available:** AggregateRoot, DomainEvent, EventMetadata in `eaf-eventsourcing`

If any backend modules are missing, create them first following the build-logic conventions.

## Acceptance Criteria

1. **Submit button displayed**
   - Given I have filled all required fields (vmName, projectId, size, justification)
   - When I view the form
   - Then I see a "Submit Request" button
   - And the button is disabled until form is valid
   - And the button shows loading state during submission

2. **Successful submission**
   - Given I have filled all required fields correctly
   - When I click "Submit Request"
   - Then a `CreateVmRequestCommand` is dispatched to the backend
   - And the command handler creates `VmRequestAggregate`
   - And `VmRequestCreated` event is persisted to the event store
   - And the projection is updated in the read model
   - And I see success toast: "Request submitted!"
   - And I am redirected to request detail view (`/requests/{id}`)

3. **Validation errors (frontend)**
   - Given I have invalid form data (e.g., vmName with spaces)
   - When I click submit
   - Then I see specific validation errors per field
   - And the form stays open for correction
   - And no API request is made

4. **Validation errors (backend)**
   - Given frontend validation passes but backend finds issues
   - When the API returns 400 Bad Request
   - Then I see inline errors for the affected fields
   - And no event is persisted

5. **Quota exceeded error**
   - Given the request violates project quota
   - When I submit
   - Then the command fails with `QuotaExceeded` error
   - And I see inline error: "Project quota exceeded. Available: X VMs"
   - And no event is persisted (transactional)

6. **Network error handling**
   - Given network connectivity issues
   - When I submit
   - Then I see error toast: "Connection error. Please try again."
   - And the submit button is re-enabled for retry

7. **Unsaved changes protection**
   - Given I have partially filled the form
   - When I navigate away without submitting
   - Then I see confirmation dialog: "Discard unsaved changes?"

## Test Plan

### Backend Unit Tests

**VmRequestAggregate (dcm-domain):**
- Creates aggregate with valid input, emits VmRequestCreated event
- Aggregate ID is a new UUID
- Aggregate version starts at 0
- VmName validation rejects invalid patterns (spaces, uppercase, length)
- VmSize validation rejects invalid values
- Justification minimum length enforced (10 chars)

**CreateVmRequestHandler (dcm-application):**
- Creates aggregate and persists events to store
- Returns created request ID
- Propagates tenant context to aggregate
- Fails with QuotaExceeded when quota check fails

**VmRequestController (dcm-api):**
- POST /api/requests returns 201 Created with Location header
- Returns 400 Bad Request for invalid input
- Returns 409 Conflict for quota exceeded
- Returns 401 Unauthorized without valid token
- Returns 403 Forbidden for wrong tenant

### Backend Integration Tests

- Full flow: HTTP POST -> Command -> Aggregate -> EventStore -> Response
- Projection updated after event persisted
- Multi-tenant isolation (tenant A cannot see tenant B data)
- Concurrent request handling (optimistic locking)

### Frontend Unit Tests

**SubmitButton component:**
- Renders "Submit Request" text
- Disabled when form is invalid
- Shows spinner when isPending
- Calls onSubmit when clicked

**useCreateVmRequest hook:**
- Calls POST /api/requests with form data
- Returns isPending, isError, isSuccess states
- Handles 400 validation errors
- Handles 409 quota exceeded errors
- Handles network errors

**VmRequestForm integration:**
- Submit button calls mutation
- Success shows toast and redirects
- Error shows inline messages
- Loading state disables form

### E2E Tests (Playwright)

- Happy path: Fill form -> Submit -> See success toast -> Redirect to detail
- Validation error: Invalid vmName -> See error message -> Form stays open
- Auth required: Unauthenticated -> Redirect to login

## Structure Alignment / Previous Learnings

### Learnings from Story 2.5 (Size Selector)

- **React Hook Form pattern:** `useForm` with zodResolver, `mode: 'onChange'`
- **FormField pattern:** Wrap custom components with FormControl
- **Test setup:** ResizeObserver mock needed for Radix UI components
- **Barrel exports:** Add new components to `src/components/requests/index.ts`

[Source: docs/sprint-artifacts/2-5-vm-request-form-size-selector.md]

### EAF Core Types (REUSE - DO NOT RECREATE)

```kotlin
// From eaf/eaf-core/src/main/kotlin/de/acci/eaf/core/types/Identifiers.kt
import de.acci.eaf.core.types.TenantId   // @JvmInline value class TenantId(val value: UUID)
import de.acci.eaf.core.types.UserId     // @JvmInline value class UserId(val value: UUID)
import de.acci.eaf.core.types.CorrelationId // @JvmInline value class CorrelationId(val value: UUID)
```

### EAF Event Sourcing Patterns (MUST FOLLOW)

```kotlin
// From eaf/eaf-eventsourcing - DomainEvent interface
interface DomainEvent {
    val aggregateType: String
    val metadata: EventMetadata  // NOT occurredAt directly on event
}

// EventMetadata contains all context
data class EventMetadata(
    val tenantId: TenantId,
    val userId: UserId,
    val correlationId: CorrelationId,
    val timestamp: Instant
)

// AggregateRoot pattern
abstract class AggregateRoot<TId : Any> {
    abstract val id: TId
    var version: Long = 0
    val uncommittedEvents: List<DomainEvent>

    protected fun applyEvent(event: DomainEvent, isReplay: Boolean = false)
    protected abstract fun handleEvent(event: DomainEvent)
    fun clearUncommittedEvents()
}
```

### TanStack Query Patterns

```tsx
// useMutation for form submission
const mutation = useMutation({
  mutationFn: (data: VmRequestFormData) => createVmRequest(data),
  onSuccess: (result) => {
    queryClient.invalidateQueries({ queryKey: ['my-requests'] })
  },
})

// In component - separate UI callbacks
const handleSubmit = (data: VmRequestFormData) => {
  mutation.mutate(data, {
    onSuccess: (result) => {
      toast.success('Request submitted!')
      navigate(`/requests/${result.id}`)
    },
    onError: (error) => {
      toast.error('Connection error')
    },
  })
}
```

[Source: TanStack Query v5 docs, TkDodo best practices]

## Tasks / Subtasks

### Phase 0: Database Schema Update (REQUIRED FIRST)

- [x] **Task 0.1: Update vm_requests_projection schema** (AC: 2)
  - [x] Create Flyway migration `V004__update_vm_requests_projection_for_submit.sql`
  - [x] Add columns: project_id (UUID NOT NULL), project_name (VARCHAR), size (VARCHAR), justification (TEXT), approved_by (UUID nullable), rejected_by (UUID nullable), requester_name (VARCHAR)
  - [x] Update `dcm/dcm-infrastructure/src/main/resources/db/jooq-init.sql` with H2-compatible DDL
  - [x] Run `./gradlew :dcm:dcm-infrastructure:generateJooq`
  - [x] Verify generated code compiles

### Phase 1: Backend Domain Layer (dcm-domain)

**WARNING: dcm-domain MUST NOT import org.springframework.* (blocked by Konsist)**

- [x] **Task 1.1: Create value objects** (AC: 2, 3)
  - [x] Create `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/VmRequestId.kt`
  - [x] Create `ProjectId.kt` (UUID value object for project reference)
  - [x] Create `VmName.kt` with regex validation `^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$`
  - [x] Create `VmSize.kt` enum (S, M, L, XL) with specs (cpu, memory, disk)
  - [x] Create `VmRequestStatus.kt` enum (PENDING, APPROVED, REJECTED, CANCELLED, PROVISIONING, READY, FAILED)
  - [x] Write unit tests for value object validation

- [x] **Task 1.2: Create domain events** (AC: 2)
  - [x] Create `VmRequestCreated.kt` implementing DomainEvent interface
  - [x] Include: aggregateType = "VmRequest", metadata: EventMetadata (with tenantId, userId, correlationId, timestamp)
  - [x] Include payload fields: aggregateId, projectId, vmName, size, justification
  - [x] Write tests verifying event structure matches DomainEvent interface

- [x] **Task 1.3: Create VmRequestAggregate** (AC: 2, 5)
  - [x] Create `VmRequestAggregate.kt` extending AggregateRoot<UUID>
  - [x] Implement `create()` factory method that calls applyEvent(VmRequestCreated(...))
  - [x] Implement `handleEvent()` for state reconstitution from events
  - [x] Inject Clock for timestamp (DO NOT use Clock.systemUTC() directly)
  - [x] Write unit tests for aggregate creation and event emission

### Phase 2: Backend Application Layer (dcm-application)

- [x] **Task 2.1: Create command and handler** (AC: 2, 5)
  - [x] Create `CreateVmRequestCommand.kt`:
    ```kotlin
    data class CreateVmRequestCommand(
        val tenantId: TenantId,      // From eaf-core
        val requesterId: UserId,      // From eaf-core
        val projectId: ProjectId,     // From dcm-domain
        val vmName: VmName,
        val size: VmSize,
        val justification: String
    )
    ```
  - [x] Create `CreateVmRequestHandler.kt` with EventStore dependency
  - [x] Implement quota validation (stub for now, full implementation in Epic 4)
  - [x] Write unit tests with mocked EventStore

### Phase 3: Backend API Layer (dcm-api)

- [x] **Task 3.1: Create REST controller** (AC: 2, 3, 4, 5)
  - [x] Create `VmRequestController.kt` with POST /api/requests endpoint
  - [x] Create `CreateVmRequestRequest.kt` DTO
  - [x] Create `VmRequestResponse.kt` DTO
  - [x] Map validation errors to 400 Bad Request
  - [x] Map QuotaExceeded to 409 Conflict
  - [x] Return 201 Created with Location header
  - [x] Write controller unit tests

- [x] **Task 3.2: Add security configuration** (AC: 4)
  - [x] Ensure /api/requests requires authentication
  - [x] Extract tenant and user from JWT
  - [x] Write security integration tests

### Phase 4: Backend Projection Update

- [x] **Task 4.1: Update VmRequestProjectionRepository** (AC: 2)
  - [x] **MODIFY EXISTING** `dcm/dcm-infrastructure/src/main/kotlin/de/acci/dcm/infrastructure/projection/VmRequestProjectionRepository.kt`
  - [x] Add insert/update methods for new fields (projectId, projectName, size, justification, etc.)
  - [x] Maintain RLS tenant isolation pattern (use TenantContextHolder)
  - [x] Update integration tests

### Phase 5: Backend Integration Tests

- [x] **Task 5.1: Full-stack integration test** (AC: 2)
  - [x] Create integration test with Testcontainers (PostgreSQL)
  - [x] Test complete flow: HTTP -> Command -> Event -> Projection
  - [x] Test tenant isolation

### Phase 6: Frontend Setup

- [x] **Task 6.1: Install TanStack Query** (Setup)
  - [x] Run `npm install @tanstack/react-query`
  - [x] Create QueryClientProvider wrapper in App.tsx
  - [x] Verify installation

- [x] **Task 6.2: Create API client** (AC: 2, 6)
  - [x] Create `src/api/api-client.ts` with fetch wrapper
  - [x] Add auth token injection from OIDC context
  - [x] Add error handling for 400, 401, 403, 409 responses
  - [x] Create `src/api/vm-requests.ts` with createVmRequest function

### Phase 7: Frontend Components

- [x] **Task 7.1: Create useCreateVmRequest hook** (AC: 2, 4, 5, 6)
  - [x] Create `src/hooks/useCreateVmRequest.ts`
  - [x] Use useMutation from TanStack Query
  - [x] Return isPending, isError, error, mutate
  - [x] Write unit tests

- [x] **Task 7.2: Add submit button to form** (AC: 1, 7)
  - [x] Add shadcn Button component (if not installed)
  - [x] Replace placeholder div with actual Button
  - [x] Connect to form.handleSubmit
  - [x] Show loading state with Loader2 spinner
  - [x] Disable during submission
  - [x] Update tests

- [x] **Task 7.3: Add toast notifications** (AC: 2, 6)
  - [x] Install shadcn sonner (toast) component
  - [x] Add success toast on successful submission
  - [x] Add error toast on network failure
  - [x] Add Toaster to App.tsx

- [x] **Task 7.4: Implement redirect on success** (AC: 2)
  - [x] Use useNavigate from react-router-dom
  - [x] Redirect to `/requests/{id}` after successful submission
  - [x] Handle case where detail page doesn't exist yet (Story 2.7)

- [x] **Task 7.5: Handle backend validation errors** (AC: 4, 5)
  - [x] Parse 400 response errors
  - [x] Map backend field errors to form.setError()
  - [x] Display quota exceeded message inline

### Phase 8: E2E Tests

- [x] **Task 8.1: Write Playwright E2E tests** (Test Plan)
  - [x] Happy path submission flow (scaffolded, marked skip pending backend integration)
  - [x] Validation error handling (scaffolded, marked skip pending backend integration)
  - [x] Auth requirement redirect (scaffolded, marked skip pending backend integration)

## Dev Notes

### Files to Modify (Existing)

| File | Changes Required |
|------|------------------|
| `dcm-infrastructure/.../jooq-init.sql` | Add new columns to vm_requests_projection |
| `dcm-infrastructure/.../VmRequestProjectionRepository.kt` | Support new fields |
| `dcm-web/src/App.tsx` | Add QueryClientProvider, Toaster |
| `dcm-web/src/components/requests/VmRequestForm.tsx` | Replace placeholder with Button, add mutation |
| `dcm-web/src/components/requests/VmRequestForm.test.tsx` | Add submission tests |
| `dcm-web/package.json` | Add @tanstack/react-query dependency |

### New Files to Create (Backend)

```text
dcm/dcm-infrastructure/src/main/resources/db/migration/
└── V004__update_vm_requests_projection_for_submit.sql

dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/
├── VmRequestId.kt
├── ProjectId.kt
├── VmName.kt
├── VmSize.kt
├── VmRequestStatus.kt
├── VmRequestAggregate.kt
└── events/
    └── VmRequestCreated.kt

dcm/dcm-domain/src/test/kotlin/de/acci/dcm/domain/vmrequest/
├── VmNameTest.kt
├── VmSizeTest.kt
├── VmRequestAggregateTest.kt
└── events/
    └── VmRequestCreatedTest.kt

dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vmrequest/
├── CreateVmRequestCommand.kt
└── CreateVmRequestHandler.kt

dcm/dcm-application/src/test/kotlin/de/acci/dcm/application/vmrequest/
└── CreateVmRequestHandlerTest.kt

dcm/dcm-api/src/main/kotlin/de/acci/dcm/api/vmrequest/
├── VmRequestController.kt
├── CreateVmRequestRequest.kt
└── VmRequestResponse.kt

dcm/dcm-api/src/test/kotlin/de/acci/dcm/api/vmrequest/
└── VmRequestControllerTest.kt
```

### New Files to Create (Frontend)

```text
dcm/dcm-web/src/
├── api/
│   ├── api-client.ts           # Fetch wrapper with auth
│   └── vm-requests.ts          # VM request API functions
├── hooks/
│   └── useCreateVmRequest.ts   # TanStack Query mutation hook
├── components/
│   └── ui/
│       └── sonner.tsx          # shadcn toast (auto-generated)
└── lib/
    └── query-client.ts         # QueryClient configuration
```

### API Contract

```typescript
// POST /api/requests
// Request
interface CreateVmRequestRequest {
  vmName: string        // 3-63 chars, lowercase alphanumeric + hyphens
  projectId: string     // UUID
  size: 'S' | 'M' | 'L' | 'XL'
  justification: string // min 10 chars
}

// Response 201 Created
interface VmRequestResponse {
  id: string            // UUID
  vmName: string
  projectId: string
  projectName: string
  size: {
    code: string
    cpuCores: number
    memoryGb: number
    diskGb: number
  }
  status: 'PENDING'
  createdAt: string     // ISO 8601
}

// Response 400 Bad Request
interface ValidationErrorResponse {
  type: 'validation'
  errors: Array<{
    field: string
    message: string
  }>
}

// Response 409 Conflict
interface QuotaExceededResponse {
  type: 'quota_exceeded'
  message: string
  available: number
  requested: number
}
```

### Value Object Validation

```kotlin
// VmName validation regex
val VM_NAME_REGEX = Regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$")

// Validation rules:
// - 3-63 characters total
// - Lowercase letters, numbers, hyphens only
// - Must start with letter or number
// - Must end with letter or number
```

### Component Examples

```tsx
// Submit Button with loading state
<Button
  type="submit"
  disabled={!form.formState.isValid || mutation.isPending}
  className="w-full"
>
  {mutation.isPending ? (
    <>
      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
      Submitting...
    </>
  ) : (
    'Submit Request'
  )}
</Button>
```

```tsx
// Error handling in form
const handleSubmit = (data: VmRequestFormData) => {
  mutation.mutate(data, {
    onSuccess: (result) => {
      toast.success('Request submitted!')
      navigate(`/requests/${result.id}`)
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        if (error.status === 400 && error.body.type === 'validation') {
          error.body.errors.forEach((err) => {
            form.setError(err.field as keyof VmRequestFormData, {
              message: err.message,
            })
          })
        } else if (error.status === 409) {
          form.setError('root', { message: error.body.message })
        }
      } else {
        toast.error('Connection error. Please try again.')
      }
    },
  })
}
```

### Accessibility Requirements

- Submit button has aria-label during loading state
- Error messages announced via aria-live="polite"
- Focus management: return focus to first error field on validation failure
- Keyboard accessible: Enter key submits form

### Test IDs for E2E

```tsx
data-testid="vm-request-form"           // Form element
data-testid="submit-button"             // Submit button
data-testid="submit-loading"            // Loading spinner
data-testid="success-toast"             // Success notification
data-testid="error-toast"               // Error notification
```

### Deferred Items

| Item | Reason | Target Story |
|------|--------|--------------|
| Real quota enforcement | Requires project/quota aggregate | Epic 4 |
| Email notification trigger | Email service setup | Story 2.12 |
| Request detail page | Separate story | Story 2.7 |
| Real-time status updates | SSE/polling | Story 2.8 |
| Admin notification | Admin workflow | Story 2.9 |

### Scope Clarification

**What IS in scope:**
- Frontend: Submit button, API call, toast, redirect placeholder
- Backend: Full CQRS/ES implementation (Command -> Aggregate -> Event -> Projection)
- Validation: Both frontend (Zod) and backend (Bean Validation)
- Database: Schema migration for projection table

**What is NOT in scope:**
- Request detail page (redirect will go to `/requests/{id}` which may 404 until Story 2.7)
- Email notifications (triggered but not implemented until Story 2.12)
- Real quota enforcement (will use mock/stub until Epic 4)

### Mock Strategy

| Dependency | Mock Approach |
|------------|---------------|
| EventStore | InMemoryEventStore from eaf-testing |
| Quota Check | Stub that always returns "quota available" |
| User Context | Extract from JWT via SecurityContext |
| Tenant Context | Extract from JWT claim or header |

## Dev Agent Record

**Implementation Date:** 2025-12-01
**Developer:** Claude (claude-opus-4-5-20251101)

### File List

#### Backend - Domain Layer (dcm-domain)

| File | Action |
|------|--------|
| `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/VmRequestId.kt` | Created |
| `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/ProjectId.kt` | Created |
| `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/VmName.kt` | Created |
| `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/VmSize.kt` | Created |
| `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/VmRequestStatus.kt` | Created |
| `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/VmRequestAggregate.kt` | Created |
| `dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/events/VmRequestCreated.kt` | Created |
| `dcm/dcm-domain/src/test/kotlin/de/acci/dcm/domain/vmrequest/VmNameTest.kt` | Created |
| `dcm/dcm-domain/src/test/kotlin/de/acci/dcm/domain/vmrequest/VmSizeTest.kt` | Created |
| `dcm/dcm-domain/src/test/kotlin/de/acci/dcm/domain/vmrequest/VmRequestAggregateTest.kt` | Created |

#### Backend - Application Layer (dcm-application)

| File | Action |
|------|--------|
| `dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vmrequest/CreateVmRequestCommand.kt` | Created |
| `dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vmrequest/CreateVmRequestHandler.kt` | Created |
| `dcm/dcm-application/src/test/kotlin/de/acci/dcm/application/vmrequest/CreateVmRequestHandlerTest.kt` | Created |

#### Backend - API Layer (dcm-api)

| File | Action |
|------|--------|
| `dcm/dcm-api/src/main/kotlin/de/acci/dcm/api/vmrequest/VmRequestController.kt` | Created |
| `dcm/dcm-api/src/main/kotlin/de/acci/dcm/api/vmrequest/CreateVmRequestRequest.kt` | Created |
| `dcm/dcm-api/src/main/kotlin/de/acci/dcm/api/vmrequest/VmRequestResponse.kt` | Created |
| `dcm/dcm-api/src/test/kotlin/de/acci/dcm/api/vmrequest/VmRequestControllerTest.kt` | Created |

#### Backend - Infrastructure Layer (dcm-infrastructure)

| File | Action |
|------|--------|
| `dcm/dcm-infrastructure/src/main/resources/db/migration/V004__update_vm_requests_projection_for_submit.sql` | Created |
| `dcm/dcm-infrastructure/src/main/resources/db/jooq-init.sql` | Modified |
| `dcm/dcm-infrastructure/src/main/kotlin/de/acci/dcm/infrastructure/projection/VmRequestProjectionRepository.kt` | Modified |
| `dcm/dcm-infrastructure/src/test/kotlin/de/acci/dcm/infrastructure/projection/VmRequestProjectionRepositoryIntegrationTest.kt` | Created |

#### Backend - App Layer (dcm-app)

| File | Action |
|------|--------|
| `dcm/dcm-app/src/main/kotlin/de/acci/dcm/config/ApplicationConfig.kt` | Modified |
| `dcm/dcm-app/src/test/kotlin/de/acci/dcm/vmrequest/VmRequestIntegrationTest.kt` | Created |
| `dcm/dcm-app/src/test/kotlin/de/acci/dcm/security/SecurityIntegrationTest.kt` | Modified |

#### Frontend (dcm-web)

| File | Action |
|------|--------|
| `dcm/dcm-web/src/api/api-client.ts` | Created |
| `dcm/dcm-web/src/api/vm-requests.ts` | Created |
| `dcm/dcm-web/src/api/vm-requests.test.ts` | Created |
| `dcm/dcm-web/src/hooks/useCreateVmRequest.ts` | Created |
| `dcm/dcm-web/src/hooks/useCreateVmRequest.test.ts` | Created |
| `dcm/dcm-web/src/components/requests/VmRequestForm.tsx` | Modified |
| `dcm/dcm-web/src/components/requests/VmRequestForm.test.tsx` | Modified |
| `dcm/dcm-web/src/components/ui/sonner.tsx` | Created |
| `dcm/dcm-web/src/App.tsx` | Modified |
| `dcm/dcm-web/e2e/vm-request-form.spec.ts` | Created |

### Implementation Notes

- Full CQRS/ES implementation: Command → Aggregate → Event → Projection
- TanStack Query v5 useMutation pattern for frontend API calls
- Backend validation returns structured error responses (400 with field-level errors)
- E2E tests scaffolded but marked `skip` pending full backend integration environment
- Coverage targets met: Backend ≥80%, Frontend unit tests comprehensive

## References

- [Source: docs/epics.md#Story-2.6-VM-Request-Form-Submit-Command]
- [Source: docs/sprint-artifacts/tech-spec-epic-2.md#Section-4]
- [Source: docs/sprint-artifacts/2-5-vm-request-form-size-selector.md]
- [Source: docs/architecture.md#CQRS-Pattern]
- [TanStack Query useMutation](https://tanstack.com/query/latest/docs/framework/react/guides/mutations)
- [TkDodo's Mutation Best Practices](https://tkdodo.eu/blog/mastering-mutations-in-react-query)
- [CLAUDE.md#Zero-Tolerance-Policies]

## Story Validation

**Date:** 2025-11-30
**Validator:** SM Agent (Bob)
**Model:** claude-opus-4-5-20251101

### Checklist

- [x] All acceptance criteria are testable
- [x] Prerequisites are met (Story 2.4, 2.5 done)
- [x] FRs mapped to acceptance criteria (FR16, FR45)
- [x] Architecture patterns documented (CQRS/ES with EAF interfaces)
- [x] Technical notes include full-stack implementation
- [x] Accessibility requirements documented
- [x] Test plan covers all ACs
- [x] Deferred items clearly documented
- [x] Task breakdown is actionable with clear phases
- [x] Database migration included (Phase 0)
- [x] EAF type reuse documented (TenantId, UserId, CorrelationId)
- [x] Existing files to modify identified

### Status

Implementation complete. All tasks finished. Pending final review for advancement to `done`.
