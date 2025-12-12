# Story 3.8: VM Ready Notification

Status: done

## Story

As an **end user**,
I want to be notified when my VM is ready,
so that I can start using it immediately without constantly checking the portal.

## Acceptance Criteria

### AC-3.8.1: Success Email Notification
**Given** my VM provisioning completes successfully
**When** `VmProvisioned` event is persisted
**Then** an email is sent to me containing:
- **Subject:** "[DVMM] VM ready: {vmName}"
- **Body:**
  - VM Name, Project name
  - IP Address, Hostname
  - Connection instructions (SSH for Linux, RDP for Windows)
  - Link to VM details in portal
  - Provisioning duration: "VM created in X minutes"

### AC-3.8.2: In-App Status Refresh
**Given** my VM status changes to "Ready"
**When** I am viewing the dashboard
**Then** the existing polling mechanism picks up the change within 30 seconds
**And** "My Requests" list shows updated status

**Note:** Failure notifications are already implemented in Story 3.6 via `TriggerProvisioningHandler.sendFailureNotifications()`. This story focuses solely on SUCCESS notifications.

### Satisfied Functional Requirements
- **FR47:** System sends email notification when VM is ready

## Tasks / Subtasks

### Backend Tasks

- [x] **Task 1: Create VM Ready Email Template (AC: 3.8.1)**
  - [x] Create `vm-ready.html` Thymeleaf template in `dvmm-infrastructure/src/main/resources/templates/email/`
  - [x] Include: vmName, projectName, ipAddress, hostname, connectionCommand, portalLink, provisioningDuration
  - [x] OS-specific connection instructions: SSH command for Linux, RDP (`mstsc /v:`) for Windows
  - [x] Follow existing template styling from `vm-provisioning-failed-user.html`

- [x] **Task 2: Extend VmRequestNotificationSender Interface (AC: 3.8.1)**
  - [x] Add `VmReadyNotification` data class to `VmRequestNotificationSender.kt`:
    ```kotlin
    public data class VmReadyNotification(
        val requestId: VmRequestId,
        val tenantId: TenantId,
        val requesterEmail: EmailAddress,
        val vmName: String,
        val projectName: String,
        val ipAddress: String?,
        val hostname: String,
        // val guestOs: String?, // Removed (dead code)
        val provisioningDurationMinutes: Long,
        val portalLink: String
    )
    ```
  - [x] Add `sendVmReadyNotification()` method to interface
  - [x] Add implementation to `NoOpVmRequestNotificationSender`

- [x] **Task 3: Implement Adapter Method (AC: 3.8.1)**
  - [x] Add `sendVmReadyNotification()` to `VmRequestNotificationSenderAdapter`
  - [x] Use template name `vm-ready`
  - [x] Map notification fields to template context:
    - `vmName`, `projectName`, `ipAddress`, `hostname`
    - `connectionCommand` (SSH and RDP)
    - `portalLink`, `provisioningDuration`

- [x] **Task 4: Add Success Notification to TriggerProvisioningHandler (AC: 3.8.1)**
  - [x] Add `sendSuccessNotification()` private method following `sendFailureNotifications()` pattern
  - [x] Call from `emitSuccess()` after Step 3 (timeline event)
  - [x] Reuse `requestDetails` (already fetched earlier for project prefix) for requester email
  - [x] Calculate provisioning duration: `Duration.between(event.metadata.timestamp, Instant.now())`
  - [x] Build portal link: `${baseUrl}/requests/${requestId}`
  - [x] Provide both SSH and RDP commands (OS agnostic)
  - [x] Log success/failure but do NOT fail the handler on email errors

- [x] **Task 5: Unit & Integration Tests (AC: 3.8.1)**
  - [x] `TriggerProvisioningHandlerTest` - verify `sendVmReadyNotification()` called on success
  - [x] `VmRequestNotificationSenderAdapterTest` - verify template rendering with new method
  - [x] Verify connection command logic: SSH for "Linux*", RDP for "Windows*" (Updated to provide both)

### Frontend Tasks

- [~] **Task 6: Dashboard Status Indicator (AC: 3.8.2) - SKIPPED (Optional Enhancement)**
  - [x] Existing TanStack Query polling already handles status refresh (no changes needed)
  - [~] Optional toast notification deferred to future story (not required for AC-3.8.2)

## Dev Notes

### Architecture Pattern: Inline Notifications

Notifications are sent **inline** within `TriggerProvisioningHandler`, NOT via separate event listeners. This matches the established pattern from Story 3.6:

```kotlin
// In TriggerProvisioningHandler.emitSuccess()
private suspend fun emitSuccess(..., requestDetails: VmRequestSummary) {
    // Step 1: VmProvisioned event
    // Step 2: VmRequestReady event
    // Step 3: Timeline event
    // Step 4: Send success notification (NEW)
    sendSuccessNotification(
        event = event,
        provisioningResult = provisioningResult,
        provisionedHostname = provisionedHostname,
        requestDetails = requestDetails
    )
}
```

**Why inline?** The notification is a side-effect of successful provisioning. Separating it into an event listener would require publishing Spring ApplicationEvents (not just domain events), add unnecessary indirection, and deviate from the failure notification pattern.

### Connection Command Logic

The email displays both SSH and RDP commands, letting users choose based on their VM's OS:

```kotlin
// In VmRequestNotificationSenderAdapter.sendVmReadyNotification()
val ip = notification.ipAddress ?: "pending"
val context = mapOf(
    // ...
    "sshCommand" to "ssh <username>@$ip",
    "rdpCommand" to "mstsc /v:$ip",
    // ...
)
```

Note: The `<username>` placeholder reminds users that the SSH username depends on their VM template (e.g., ubuntu, admin, root).

### Source Tree Locations

**New Files:**
- `dvmm/dvmm-infrastructure/src/main/resources/templates/email/vm-ready.html`

**Modified Files:**
- `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmrequest/VmRequestNotificationSender.kt` (add data class + method)
- `dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/notification/VmRequestNotificationSenderAdapter.kt` (add implementation)
- `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vm/TriggerProvisioningHandler.kt` (add sendSuccessNotification)

### Testing Standards

- **Unit Test:** Mock `VmRequestNotificationSender`, verify handler calls `sendVmReadyNotification()` with expected data
- **Integration Test:** Verify template renders correctly with all placeholders
- **E2E Test:** Full flow - approve request -> provisioning -> VmProvisioned -> email sent

### Previous Story Learnings

**From Story 3.6 (Error Handling):**
- Notification failures are logged but do NOT fail the parent handler
- Use `logNotificationError()` helper for consistent error formatting
- Query `vmRequestReadRepository` for requester email and project details

**From Story 3.7 (VM Details):**
- `guestOs` field is available in projection for connection command logic
- `createdAt` timestamp available for duration calculation

### What's Already Implemented (Do NOT Duplicate)

| Feature | Status | Location |
|---------|--------|----------|
| Failure email to user | Done (3.6) | `TriggerProvisioningHandler.sendFailureNotifications()` |
| Failure email to admin | Done (3.6) | `TriggerProvisioningHandler.sendFailureNotifications()` |
| `vm-provisioning-failed-user.html` | Done (3.6) | `templates/email/` |
| `vm-provisioning-failed-admin.html` | Done (3.6) | `templates/email/` |
| Polling for status changes | Done (2.x) | TanStack Query `refetchInterval` |
| Error code to user message mapping | Done (3.6) | `VsphereError` sealed class |

## Project Context Reference

- **Architecture:** docs/architecture.md (Event-Driven Notifications, Async Handlers)
- **PRD:** docs/prd.md (FR47: VM Ready Notification)
- **Previous Story:** docs/sprint-artifacts/3-7-provisioned-vm-details.md
- **Pattern Reference:** Story 3.6 `sendFailureNotifications()` implementation

## Dev Agent Record

### Context Reference
- Story context loaded from epics.md, architecture.md, project-context.md
- Previous story analysis: 3-7-provisioned-vm-details.md, 3-6-provisioning-error-handling.md
- Codebase analysis: TriggerProvisioningHandler.kt, VmRequestNotificationSender.kt

### Agent Model Used
Claude Opus 4.5 (via Claude Code)

### Validation Notes
- Validated against existing codebase patterns
- Confirmed failure notifications already implemented in Story 3.6
- Architecture pattern aligned with inline notification approach

### File List

**New Files:**
- `dvmm/dvmm-infrastructure/src/main/resources/templates/email/vm-ready.html` - VM Ready email template

**Modified Files:**
- `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmrequest/VmRequestNotificationSender.kt` - Added `VmReadyNotification` data class and interface method
- `dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/notification/VmRequestNotificationSenderAdapter.kt` - Added `sendVmReadyNotification()` implementation with connection command logic
- `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vm/TriggerProvisioningHandler.kt` - Added `sendSuccessNotification()` called from `emitSuccess()`

**Test Files:**
- `dvmm/dvmm-application/src/test/kotlin/de/acci/dvmm/application/vm/TriggerProvisioningHandlerTest.kt` - Added success notification tests
- `dvmm/dvmm-infrastructure/src/test/kotlin/de/acci/dvmm/infrastructure/notification/VmRequestNotificationSenderAdapterTest.kt` - Added VM ready notification tests

### Code Review Fixes (Claude Opus 4.5)

**Issues Addressed:**

1. **HIGH: Portal link fallback** - Template now conditionally hides portal link section when URL is "#"
2. **MEDIUM: Wrong connection command** - Email now shows BOTH SSH and RDP commands instead of guessing OS
3. **MEDIUM: Notification failure resilience** - Added test verifying handler continues when notification fails
4. **LOW: Hardcoded SSH username** - Changed to `ssh <username>@ip` with note about template-specific usernames
5. **LOW: Missing requestId** - Added requestId to vm-ready template context for consistency
6. **LOW: Missing error test** - Added TemplateError mapping test for sendVmReadyNotification

### Code Review Fixes (Amelia)
1. **HIGH: Task 4 False Claim** - Removed claim about querying `guestOs` since it's not available in summary.
2. **MEDIUM: Dead Code** - Removed unused `guestOs` field from `VmReadyNotification` and updated handlers/tests.