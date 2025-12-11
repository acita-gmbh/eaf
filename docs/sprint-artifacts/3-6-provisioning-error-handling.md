# Story 3.6: Provisioning Error Handling

Status: ready-for-dev

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

- [ ] **Task 2: Implement Resilience4j Retry Policy (AC: 3.6.1)**
  - [ ] Add `resilience4j-kotlin` and `resilience4j-retry` dependencies to `dvmm-application`
  - [ ] Configure retry policy: 4 retries (5 total attempts), exponential backoff starting at 10s, multiplier 2.0, max 120s
  - [ ] Wrap `vspherePort.createVm()` calls with retry policy in `TriggerProvisioningHandler`
  - [ ] Log each retry attempt with correlation ID and attempt number

- [ ] **Task 3: Implement VmProvisioningFailed Event (AC: 3.6.2)**
  - [ ] Create `VmProvisioningFailed` domain event if not already present
  - [ ] Add `markProvisioningFailed(errorCode, errorMessage, retryCount)` method to `VmAggregate`
  - [ ] Update `JacksonVmEventDeserializer` to handle `VmProvisioningFailed`
  - [ ] Update `VmRequestProjection` to handle FAILED status

- [ ] **Task 4: Implement Saga Compensation (AC: 3.6.4)**
  - [ ] Track `partialVmId` when VM is created but subsequent steps fail
  - [ ] Implement cleanup logic in `TriggerProvisioningHandler` catch block
  - [ ] Call `vspherePort.deleteVm(partialVmId)` to clean up orphaned VM
  - [ ] Log cleanup success/failure with correlation ID
  - [ ] On cleanup failure, log with "CRITICAL" prefix for alerting

- [ ] **Task 5: Create User-Friendly Error Mapping (AC: 3.6.3)**
  - [ ] Create `ProvisioningErrorCode` enum with user messages (if not present)
  - [ ] Map VMware fault types to error codes:
    - `InsufficientResourcesFault` → `INSUFFICIENT_RESOURCES`
    - `InvalidDatastorePath` → `DATASTORE_NOT_AVAILABLE`
    - `VmConfigFault` → `VM_CONFIG_INVALID`
    - `NotAuthenticated` → `CONNECTION_FAILED`
    - `TemplateNotFound` → `TEMPLATE_NOT_FOUND`
  - [ ] Store user-friendly message in `ProvisioningErrorCode.userMessage`

- [ ] **Task 6: Implement Failure Notifications (AC: 3.6.5)**
  - [ ] Create `vm-provisioning-failed.html` email template
  - [ ] Add `VmProvisioningFailedEmailHandler` listening for `VmProvisioningFailed` event
  - [ ] Send user email with: VM name, error summary, suggested actions, contact admin link
  - [ ] Send admin email with: full error details, correlation ID, retry history, stack trace (if available)

- [ ] **Task 7: Update Timeline Projection for Failures**
  - [ ] Add timeline event for "Provisioning failed" with error summary
  - [ ] Show retry attempts in timeline if applicable
  - [ ] Include link to contact admin in timeline event

### Frontend Tasks

- [ ] **Task 8: Display Error State in Request Detail (AC: 3.6.2, 3.6.3)**
  - [ ] Update `RequestDetail.tsx` to handle FAILED status
  - [ ] Display error message from `ProvisioningProgress` component
  - [ ] Show user-friendly error message (not technical details)
  - [ ] Add "Contact Admin" button for failed requests

- [ ] **Task 9: Update Progress Component for Failure**
  - [ ] Update `ProvisioningProgress.tsx` to handle error state
  - [ ] Show failed stage with red indicator
  - [ ] Display error message and retry count
  - [ ] Add animation for failed state (shake or pulse)

### Testing Tasks

- [ ] **Task 10: Unit Tests for Retry Logic**
  - [ ] Test retry policy configuration (5 attempts, backoff timing)
  - [ ] Test transient error triggers retry
  - [ ] Test permanent error does not retry
  - [ ] Test retry exhaustion leads to FAILED status

- [ ] **Task 11: Unit Tests for Error Mapping**
  - [ ] Test all VMware fault types map to correct error codes
  - [ ] Test user messages are returned correctly
  - [ ] Test unknown errors map to UNKNOWN code

- [ ] **Task 12: Integration Tests with VCSIM**
  - [ ] Test transient error recovery (VCSIM returns 503, then succeeds)
  - [ ] Test permanent error handling (VCSIM returns 400 invalid config)
  - [ ] Test saga compensation (VM created, network fails, VM deleted)
  - [ ] Test `VmProvisioningFailed` event persisted correctly

- [ ] **Task 13: E2E Test for Error Display**
  - [ ] Test failed request shows error message
  - [ ] Test admin receives notification
  - [ ] Test timeline shows failure event

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

<!-- To be filled by Dev agent -->

### Debug Log References

<!-- To be filled by Dev agent -->

### Completion Notes List

<!-- To be filled by Dev agent -->

### File List

- Ultimate context engine analysis completed - comprehensive developer guide created on 2025-12-11.
