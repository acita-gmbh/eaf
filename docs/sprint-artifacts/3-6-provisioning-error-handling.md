# Story 3.6: Provisioning Error Handling

Status: review

## Story

As a **system**,
I want to handle provisioning failures gracefully,
so that users understand what went wrong and the system recovers cleanly from partial failures.

## Acceptance Criteria

### AC-3.6.1: Transient Error Retry
**Given** VMware API returns an error during provisioning
**When** the error is transient (network timeout, temporary unavailability)
**Then** the system retries with exponential backoff:
- Attempt 1: immediate
- Attempt 2: after 10s
- Attempt 3: after 20s (10s * 2)
- Attempt 4: after 40s (20s * 2)
- Attempt 5: after 80s (40s * 2, capped at 120s)
**And** retry attempts are logged with correlation ID

### AC-3.6.2: Max Retries Exhausted
**Given** all retry attempts fail
**When** max retries exceeded (5 total attempts)
**Then** `VmProvisioningFailed` event is persisted with:
- errorCode
- errorMessage
- retryCount
- lastAttemptAt
**And** request status changes to "FAILED"
**And** user notification sent with error summary
**And** admin notification sent with full error details

### AC-3.6.3: Permanent Error Handling
**Given** VMware API returns a permanent error (invalid config, no capacity)
**When** error is non-retryable
**Then** provisioning fails immediately without retries
**And** error message is user-friendly:
- "InsufficientResourcesFault" → "Insufficient resources in cluster"
- "InvalidDatastorePath" → "Datastore not available"
- "VmConfigFault" → "VM configuration invalid"
- "TemplateNotFound" → "VM template not found"
- "NetworkConfigFailed" → "Network configuration failed"

### AC-3.6.4: Partial Failure Cleanup (Saga Compensation)
**Given** provisioning partially completes (VM created but subsequent step failed)
**When** cleanup is triggered
**Then** partial VM is deleted to prevent orphans
**And** cleanup logged with correlation ID
**And** if cleanup fails, warning logged with "CRITICAL" prefix for alerting

### AC-3.6.5: Failure Notifications
**Given** provisioning fails (any reason)
**When** `VmProvisioningFailed` event is persisted
**Then** email is sent to requester with user-friendly error summary
**And** email is sent to tenant admins with full technical details
**And** in-app notification badge updates

### Satisfied Functional Requirements
- FR38: System handles provisioning failures gracefully
- FR39: System retries failed provisioning with backoff
- FR77: System handles VMware API connection failures
- FR78: System implements retry logic for transient VMware errors
- FR79: System handles partial failures and reports context

## Tasks / Subtasks

### Backend Tasks

- [x] **Task 1: Enhance VsphereError Types (AC: 3.6.3)**
  - [x] Create `VsphereError` sealed class hierarchy in `dvmm-application`
  - [x] Define `TransientVsphereError` for retryable errors (connection timeout, service unavailable)
  - [x] Define `PermanentVsphereError` for non-retryable errors (invalid config, insufficient resources)
  - [x] Map VMware fault types to error classes in `VsphereClient`

- [x] **Task 2: Implement Resilience4j Retry Policy (AC: 3.6.1)**
  - [x] Add `resilience4j-kotlin` and `resilience4j-retry` dependencies to `dvmm-application`
  - [x] Configure retry policy: 4 retries (5 total attempts), exponential backoff starting at 10s, multiplier 2.0, max 120s
  - [x] Wrap `vspherePort.createVm()` calls with retry policy in `ResilientProvisioningService`
  - [x] Log each retry attempt with correlation ID and attempt number

- [x] **Task 3: Implement VmProvisioningFailed Event (AC: 3.6.2)**
  - [x] Create `VmProvisioningFailed` domain event
  - [x] Add `markProvisioningFailed(errorCode, errorMessage, retryCount)` method to `VmRequest` aggregate
  - [x] Update `JacksonVmRequestEventDeserializer` to handle `VmProvisioningFailed`
  - [x] Update `VmRequestProjection` to handle FAILED status

- [x] **Task 4: Implement Saga Compensation (AC: 3.6.4)**
  - [x] Track `partialVmId` when VM is created but subsequent steps fail
  - [x] Implement cleanup logic in `VcsimAdapter` saga compensation pattern
  - [x] Call `vspherePort.deleteVm(partialVmId)` to clean up orphaned VM
  - [x] Log cleanup success/failure with correlation ID
  - [x] On cleanup failure, log with "CRITICAL" prefix for alerting

- [x] **Task 5: Create User-Friendly Error Mapping (AC: 3.6.3)**
  - [x] Create `VsphereError` sealed class hierarchy with user messages
  - [x] Map VMware fault types to error classes:
    - `InsufficientResourcesFault` → `ResourceExhausted`
    - `InvalidDatastorePath` → `ResourceNotFound`
    - `VmConfigFault` → `InvalidConfiguration`
    - `NotAuthenticated` → `AuthenticationFailed`
    - `TemplateNotFound` → `ResourceNotFound`
  - [x] Store user-friendly message in `VsphereError.toUserMessage()` extension

- [x] **Task 6: Implement Failure Notifications (AC: 3.6.5)**
  - [x] Create `vm-provisioning-failed-user.html` email template
  - [x] Create `vm-provisioning-failed-admin.html` email template
  - [x] Add notification handlers in `NotificationService` for failure events
  - [x] Send user email with: VM name, error summary, suggested actions
  - [x] Send admin email with: full error details, correlation ID, retry history

- [x] **Task 7: Update Timeline Projection for Failures**
  - [x] Add timeline event for "Provisioning Failed" with error summary
  - [x] Error details stored in timeline event details field
  - [x] Timeline displays error message in red styling

### Frontend Tasks

- [x] **Task 8: Display Error State in Request Detail (AC: 3.6.2, 3.6.3)**
  - [x] Update `RequestDetail.tsx` to handle FAILED status
  - [x] Added error alert card with red styling for failed requests
  - [x] Show user-friendly error message extracted from PROVISIONING_FAILED timeline event
  - [x] Added `getProvisioningErrorMessage()` helper function

- [x] **Task 9: Update Progress Component for Failure**
  - [x] Verified: ProvisioningProgress component doesn't need changes
  - [x] Design correctly hides progress when status is FAILED
  - [x] Error alert card shown instead of progress component
  - [x] Note: VmProvisioningStage intentionally excludes FAILED (status vs progress separation)

### Testing Tasks

- [x] **Task 10: Unit Tests for Retry Logic**
  - [x] Test retry policy configuration (5 attempts, backoff timing) in `ResilientProvisioningServiceTest`
  - [x] Test transient error triggers retry
  - [x] Test permanent error does not retry
  - [x] Test retry exhaustion leads to FAILED status

- [x] **Task 11: Unit Tests for Error Mapping**
  - [x] Test VsphereError types map to correct error classes
  - [x] Test user messages are returned correctly via `toUserMessage()`
  - [x] Test unknown errors handled gracefully

- [x] **Task 12: Integration Tests with VCSIM**
  - [x] Tests in `VcsimAdapterSagaCompensationTest.kt`
  - [x] Test saga compensation (VM created, subsequent step fails, VM deleted)
  - [x] Test cleanup failure logged with CRITICAL prefix

- [x] **Task 13: E2E/Unit Test for Error Display**
  - [x] Added Timeline.test.tsx tests for PROVISIONING_FAILED event display
  - [x] Added RequestDetail.test.tsx tests for failed status behavior (5 tests)
  - [x] Test timeline shows failure event with red styling

## Dev Notes

### Epic 3 Context: VMware Integration
**Objective:** Enable full lifecycle management of VMs on VMware vSphere 8.0, including provisioning, status tracking, and error handling.
**Business Value:** Automating VM provisioning reduces IT support costs and provisioning time from days to minutes.
**Cross-Story Dependencies:**
- **Story 3.5 (Completed):** Established `VsphereClient` basic structure and progress tracking. We MUST reuse the `VsphereClient` and `VspherePort` interfaces.
- **Story 3.7 (Future):** Network configuration. Error handling added here MUST be extensible to network failures.

### Critical Architecture Patterns (Aligned with ADR-004a)
1.  **Hexagonal Architecture:**
    -   **Domain:** `VmAggregate` handles failures via domain events (`VmProvisioningFailed`). NO framework dependencies.
    -   **Application:** `TriggerProvisioningHandler` manages the saga/workflow. `VspherePort` defines the contract.
    -   **Infrastructure:** `VsphereClient` implements `VspherePort`. `VcsimAdapter` is the test implementation.
2.  **Event Sourcing:** State changes MUST be recorded as events (`VmProvisioningFailed`). Projections (Timeline, Status) are updated by listening to these events.
3.  **Resilience4j Integration**: Use `resilience4j-kotlin` coroutine support.
4.  **Error Classification Pattern (ADR-004a Compliance)**:
    We MUST structure `VsphereError` to mirror the future `HypervisorError` from ADR-004a. This minimizes refactoring in Epic 6.

    ```kotlin
    // dvmm-application/.../vmware/VsphereError.kt
    sealed class VsphereError {
        abstract val message: String
        abstract val retriable: Boolean
        abstract val cause: Throwable?

        // Corresponds to HypervisorError.ResourceExhausted
        data class ResourceExhausted(
            override val message: String,
            val resourceType: String, // "CPU", "MEMORY", "STORAGE"
            val requested: Int,
            val available: Int,
            override val cause: Throwable? = null
        ) : VsphereError() {
            override val retriable = true // Might free up later
        }

        // Corresponds to HypervisorError.ConnectionFailed
        data class ConnectionFailed(
            override val message: String,
            override val cause: Throwable? = null
        ) : VsphereError() {
            override val retriable = true
        }

        // Corresponds to HypervisorError.OperationFailed (Generic retryable)
        data class OperationFailed(
            val operation: String,
            override val message: String,
            override val cause: Throwable? = null
        ) : VsphereError() {
            override val retriable = true
        }

        // Corresponds to HypervisorError.InvalidConfiguration (Non-retryable)
        data class InvalidConfiguration(
            override val message: String,
            val field: String,
            override val cause: Throwable? = null
        ) : VsphereError() {
            override val retriable = false
        }
        
        // Corresponds to HypervisorError.ResourceNotFound
        data class ResourceNotFound(
             val resourceType: String,
             val resourceId: String,
             override val cause: Throwable? = null
        ) : VsphereError() {
             override val message = "$resourceType not found: $resourceId"
             override val retriable = false
        }
    }
    ```

5.  **Saga Compensation Pattern**: Clean up partial state on failure.
    ```kotlin
    // In TriggerProvisioningHandler
    suspend fun handle(command: TriggerProvisioningCommand): Result<Unit, ProvisioningError> {
        var partialVmId: VmwareVmId? = null

        return try {
            // Step 1: Clone VM
            val vmId = vspherePort.createVm(tenantId, spec) { stage ->
                // Progress callback
            }.getOrThrow()
            partialVmId = vmId  // Track for cleanup

            // Step 2: Wait for ready (can fail after VM created)
            vspherePort.waitForReady(tenantId, vmId)
                .getOrThrow()

            // Success path
            aggregate.markProvisioned(vmId, ...)
            Result.success(Unit)

        } catch (e: Exception) {
            // Saga compensation: cleanup partial VM
            partialVmId?.let { vmId ->
                try {
                    vspherePort.deleteVm(tenantId, vmId)
                    logger.info { "Cleaned up partial VM $vmId for correlation ${command.correlationId}" }
                } catch (cleanupError: Exception) {
                    logger.error(cleanupError) {
                        "CRITICAL: Failed to cleanup partial VM $vmId. " +
                        "Manual cleanup required. Correlation: ${command.correlationId}"
                    }
                    // Future: Trigger manual intervention task
                }
            }
            // ... map error and emit failure event
        }
    }
    ```

### Reinvention Prevention & Technical Research
-   **Resilience4j:** Do NOT implement custom retry loops. Use `resilience4j-kotlin`.
-   **Email Templates:** Do NOT hardcode HTML. Use Thymeleaf templates.
-   **VMware Faults:** Use standard `com.vmware.vim25.*Fault` classes.

### Regression Prevention Scenarios
-   **Scenario 1: Infinite Retry Loops:** Ensure `RetryConfig` has a strict `maxAttempts` (5).
-   **Scenario 2: Silent Failures:** Ensure `catch` blocks ALWAYS re-throw or emit `VmProvisioningFailed`.
-   **Scenario 3: Orphaned VMs:** Test the saga compensation specifically when `waitForReady` fails.
-   **Scenario 4: Flapping Circuit Breakers:** Verify `Resilience4j` behavior under high load/failure rates (Chaos Monkey test).

### Key Files to Modify

| File | Changes |
|------|---------|
| `dvmm-application/build.gradle.kts` | Add resilience4j dependencies |
| `TriggerProvisioningHandler.kt` | Add retry logic, saga compensation |
| `VsphereError.kt` | NEW: Define sealed class hierarchy (ADR-004a aligned) |
| `VsphereClient.kt` | Map `vim25` faults to `VsphereError` |
| `VcsimAdapter.kt` | Support simulating specific `VsphereError` types |
| `VmAggregate.kt` | Ensure `markProvisioningFailed` method exists |
| `JacksonVmEventDeserializer.kt` | Handle `VmProvisioningFailed` |
| `VmProvisioningFailedEmailHandler.kt` | NEW: Send failure notification |
| `vm-provisioning-failed-user.html` | NEW: User-friendly failure email |
| `vm-provisioning-failed-admin.html` | NEW: Tech-heavy failure email |
| `TimelineEventProjectionUpdater.kt` | Add failure timeline event |

### Previous Story Learnings (3.5)
- Progress tracking uses callback pattern in `VspherePort.createVm()`
- `VcsimAdapter` simulates delays between stages
- Event deserializer updates are critical for new events

### Error Code to VMware Fault Mapping (Exhaustive)

| VMware Fault | VsphereError Type | Error Code | User Message (Actionable) |
|--------------|-------------------|------------|---------------------------|
| `InsufficientResourcesFault` | `ResourceExhausted` | `INSUFFICIENT_RESOURCES` | "Cluster capacity reached. Please try a smaller size or contact support." |
| `InvalidDatastorePath` | `ResourceNotFound` | `DATASTORE_NOT_AVAILABLE` | "Storage unavailable. Please contact support." |
| `VmConfigFault` | `InvalidConfiguration` | `VM_CONFIG_INVALID` | "Invalid configuration. Please check your request parameters." |
| `NotAuthenticated` | `AuthenticationFailed` | `CONNECTION_FAILED` | "System authentication failed. IT has been notified." |
| `TemplateNotFound` | `ResourceNotFound` | `TEMPLATE_NOT_FOUND` | "VM template missing. IT has been notified." |
| `NetworkConfigFailed` | `OperationFailed` | `NETWORK_CONFIG_FAILED` | "Network setup failed. IT has been notified." |
| `GuestToolsTimeout` | `OperationTimeout` | `VMWARE_TOOLS_TIMEOUT` | "VM started but tools didn't respond. Please restart the VM." |
| `SocketTimeoutException` | `OperationTimeout` | `CONNECTION_TIMEOUT` | "Temporary connection issue. We will retry automatically." |
| Other/Unknown | `UnknownError` | `UNKNOWN` | "Unexpected error. IT has been notified." |

### Testing with VCSIM

The `VcsimAdapter` needs to support error simulation:

```kotlin
// Add to VcsimAdapter
private var simulatedError: VsphereError? = null
private var errorOnAttempt: Int = 0
private var currentAttempt: Int = 0

fun simulateTransientError(failUntilAttempt: Int) {
    simulatedError = VsphereError.OperationFailed(
        operation = "createVm",
        message = "Simulated VCSIM unavailable"
    )
    errorOnAttempt = failUntilAttempt
    currentAttempt = 0
}

fun simulatePermanentError(error: VsphereError) {
    simulatedError = error
    errorOnAttempt = Int.MAX_VALUE  // Always fail
}
```

### Project Structure Notes

- Alignment with unified project structure: All new code follows hexagonal architecture
- Error types in `dvmm-application` (port layer), implementations in `dvmm-infrastructure`
- Email templates in `dvmm-infrastructure/src/main/resources/templates/email/`

### References

- [Source: docs/sprint-artifacts/tech-spec-epic-3.md#4.3 Saga Pattern]
- [Source: docs/sprint-artifacts/tech-spec-epic-3.md#5.3 Key Acceptance Criteria]
- [Source: docs/epics.md#Story 3.6: Provisioning Error Handling]
- [Source: docs/sprint-artifacts/3-5-provisioning-progress-tracking.md - Previous Story Learnings]
- [resilience4j Kotlin Documentation](https://resilience4j.readme.io/docs/getting-started-with-resilience4j-kotlin)

## Dev Agent Record

### Context Reference

- **Epic 3 Tech Spec:** docs/sprint-artifacts/tech-spec-epic-3.md
- **Previous Story:** docs/sprint-artifacts/3-5-provisioning-progress-tracking.md
- **Project Context:** docs/project-context.md

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- PR #94: feature/3-6-provisioning-error-handling branch

### Completion Notes List

**Backend Implementation (Tasks 1-7):**
1. Created `VsphereError` sealed class hierarchy with ADR-004a alignment (ConnectionFailed, OperationFailed, ResourceExhausted, ResourceNotFound, InvalidConfiguration, AuthenticationFailed)
2. Implemented Resilience4j retry policy in `ResilientProvisioningService` with exponential backoff (10s base, 2x multiplier, 120s max, 5 total attempts)
3. Added `VmProvisioningFailed` domain event and `markProvisioningFailed()` method to VmRequest aggregate
4. Implemented saga compensation pattern in `VcsimAdapter` with CRITICAL logging for cleanup failures
5. Created `VsphereError.toUserMessage()` extension for user-friendly error mapping
6. Added failure notification handlers via `NotificationService` (user + admin emails)
7. Updated `TimelineEventProjectionUpdater` to emit PROVISIONING_FAILED timeline events with error details

**Frontend Implementation (Tasks 8-9):**
1. Added `PROVISIONING_FAILED` to TypeScript `TimelineEventType` union
2. Updated `Timeline.tsx` with AlertCircle icon, red styling, and plain-text error parsing
3. Added `getProvisioningErrorMessage()` helper in `RequestDetail.tsx`
4. Created error alert card for FAILED status with red border styling
5. Verified ProvisioningProgress component doesn't need changes (design separation of progress vs status)

**Testing (Tasks 10-13):**
1. `ResilientProvisioningServiceTest.kt` - comprehensive retry logic tests
2. `VcsimAdapterSagaCompensationTest.kt` - saga compensation tests
3. `Timeline.test.tsx` - added PROVISIONING_FAILED display tests
4. `RequestDetail.test.tsx` - added 5 tests for FAILED status behavior

**Key Architectural Decisions:**
- Separated `VmProvisioningStage` (progress) from `VmRequestStatus` (overall state) - FAILED is a status, not a stage
- Plain text error messages in timeline details for user-friendliness (vs JSON for rejection reasons)
- PROVISIONING_FAILED event stores user-friendly message, admin email receives full technical details

### File List

**Backend Files Modified/Created:**
- `dvmm-application/src/main/kotlin/de/acci/dvmm/application/vm/VsphereError.kt`
- `dvmm-application/src/main/kotlin/de/acci/dvmm/application/vm/ResilientProvisioningService.kt`
- `dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/request/events/VmProvisioningFailed.kt`
- `dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/request/VmRequest.kt`
- `dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/vmware/VcsimAdapter.kt`
- `dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/persistence/JacksonVmRequestEventDeserializer.kt`
- `dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/notification/NotificationService.kt`
- `dvmm-infrastructure/src/main/resources/templates/email/vm-provisioning-failed-user.html`
- `dvmm-infrastructure/src/main/resources/templates/email/vm-provisioning-failed-admin.html`

**Frontend Files Modified:**
- `dvmm-web/src/api/vm-requests.ts`
- `dvmm-web/src/components/requests/Timeline.tsx`
- `dvmm-web/src/pages/RequestDetail.tsx`
- `dvmm-web/src/components/requests/Timeline.test.tsx`
- `dvmm-web/src/pages/RequestDetail.test.tsx`

**Test Files:**
- `dvmm-application/src/test/kotlin/de/acci/dvmm/application/vm/ResilientProvisioningServiceTest.kt`
- `dvmm-infrastructure/src/test/kotlin/de/acci/dvmm/infrastructure/vmware/VcsimAdapterSagaCompensationTest.kt`

- Ultimate context engine analysis completed - comprehensive developer guide created on 2025-12-11.
