# Story 3.4: vm-provisioning-execution

Status: in-progress

## Story

As a **system**,
I want to create VMs on VMware with correct specifications,
So that users get exactly what they requested.

## Acceptance Criteria

1. **Given** a `ProvisionVmCommand` is received
   **When** the provisioning process executes
   **Then** a VM is created on VMware with:
   - Name: `{projectPrefix}-{requestedName}` (tenant-safe)
   - CPU: as per size specification
   - RAM: as per size specification
   - Disk: as per size specification (requires extending clone spec)
   - Network: tenant default from config (FR36)
   - Folder: organized by tenant/project

2. **And** the VM is created from template (not from scratch)
   **And** template selection based on configuration (templateName in VmwareConfiguration)
   **And** Linux template default for MVP

3. **And** Guest customization applies hostname
   **And** hostname matches VM name pattern

4. **And** VM is powered on after creation (cloneSpec.isPowerOn = true)

5. **And** provisioning waits for VMware Tools ready (timeout: 2 min)
   **And** IP address is detected via VMware Tools guest info

6. **Given** provisioning succeeds
   **When** VM is ready (VMware Tools ready, IP available)
   **Then** `VmProvisioned` event is persisted with:
   - vmId (VMware MoRef)
   - ipAddress
   - hostname
   - provisionedAt timestamp
   **And** request status changes to "Ready"
   **And** timeline event added: "VM ready"

7. **Given** VMware Tools timeout occurs (2 min)
   **When** no IP is detected within timeout
   **Then** VM is still considered provisioned with `ipAddress = null`
   **And** `VmProvisioned` event includes `warningMessage = "IP detection timed out"`
   **And** Timeline shows: "VM ready (IP pending)"
   **And** user can manually refresh to get IP later via `VspherePort.getVm()`

## Tasks / Subtasks

- [x] **Domain:** Add `VmProvisioned` event to `VmAggregate`
  - [x] Create `VmProvisioned` data class in `dvmm-domain/.../vm/events/`
  - [x] Add `READY` status to `VmStatus` enum (after PROVISIONING, before RUNNING)
  - [x] Implement `markProvisioned(vmwareId, ipAddress, hostname, warningMessage?, metadata)` method
  - [x] Add `handleEvent` case: `is VmProvisioned -> apply(event)`
  - [x] Add `apply(event: VmProvisioned)` to update state (status = READY, vmwareId, ipAddress, hostname)
  - [x] Unit test: verify state transition PROVISIONING → READY
  - [x] Unit test: verify reconstitute() works with VmProvisioned in event stream

- [x] **Domain:** Add `VmRequestReady` event to `VmRequestAggregate`
  - [x] Create event with vmId, ipAddress, hostname, provisionedAt, warningMessage?
  - [x] Implement `markReady(vmId, ipAddress, hostname, warningMessage?, metadata)` method
  - [x] Add `handleEvent` case: `is VmRequestReady -> apply(event)`
  - [x] Add `apply(event: VmRequestReady)` to update state (status = READY, vmwareId, ipAddress, hostname)
  - [x] Unit test: verify PROVISIONING → READY transition
  - [x] Unit test: verify reconstitute() works with VmRequestReady in event stream

- [x] **Domain:** Add `VmProvisioningResult` value object
  - [x] Create in `dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/vm/VmProvisioningResult.kt`
  - [x] Fields: `vmwareVmId: VmwareVmId`, `ipAddress: String?`, `hostname: String`, `warningMessage: String?`

- [x] **Application:** Add `PROVISIONING_FAILED` to `TimelineEventType`
  - [x] Update enum in `TimelineEventProjectionUpdater.kt` (forward-compatibility for Story 3.6)

- [x] **Application:** Update `VspherePort` interface
  - [x] Change return type: `createVm(spec: VmSpec): Result<VmProvisioningResult, VsphereError>`
  - [x] Or add new method: `provisionVm(spec: VmSpec): Result<VmProvisioningResult, VsphereError>`

- [ ] **Infrastructure:** Enhance `VsphereClient.createVm()` for full provisioning
  - [ ] Add guest customization spec with hostname (see code pattern below) ← MVP-DEFERRED
  - [ ] Add disk size configuration to clone spec (see code pattern below) ← MVP-DEFERRED
  - [ ] Add network configuration to relocate spec ← MVP-DEFERRED
  - [x] Implement `waitForVmwareTools(vmRef, timeout: Duration)` method
  - [x] Implement `getVmGuestInfo(vmRef): VmGuestInfo` (IP, hostname, tools status)
  - [x] Return `VmProvisioningResult` with IP address (or null with warning on timeout)

- [x] **Infrastructure:** Update `VcsimAdapter` for test compatibility
  - [x] Return realistic `VmProvisioningResult` with simulated IP (e.g., "10.0.0.100")
  - [x] Add configurable delay to simulate provisioning time

- [x] **Infrastructure:** Update event deserializers
  - [x] Add `VmProvisioned` to `JacksonVmEventDeserializer.resolveEventClass()`
  - [x] Add `VmRequestReady` to `JacksonVmRequestEventDeserializer.resolveEventClass()`
  - [x] Deserialization tests for new events

- [x] **Application:** Implement `VmProvisioningCompletionHandler`
  - [x] Listen for VM creation completion (callback from infrastructure)
  - [x] Load VmAggregate, call `markProvisioned()`, persist events
  - [x] Load VmRequestAggregate, call `markReady()`, persist events
  - [x] Update timeline with "VM ready" event (or "VM ready (IP pending)" on timeout)

- [x] **Tests:** Integration tests in `dvmm-app`
  - [x] Test full flow: ProvisionVmCommand → VspherePort.createVm() → VmProvisioned
  - [x] Test VMware Tools timeout handling (ipAddress = null, warning present)
  - [x] Test VcsimAdapter returns complete VmProvisioningResult
  - [x] Verify timeline updated on completion

## Dev Notes

### Current State (Story 3.3 Complete)

- `VmAggregate` exists with `VmProvisioningStarted` and `VmProvisioningFailed` events
- `VsphereClient.createVm()` returns `Result<VmId, VsphereError>` (basic CloneVM_Task)
- Saga pattern established: `VmRequestApproved` → `ProvisionVmCommand` → `VmProvisioningStarted`

### What This Story Adds

- SUCCESS path completion: `VmProvisioned` event when VM is fully ready
- VMware Tools wait logic for IP detection (with timeout handling)
- Guest customization for hostname
- Disk size configuration
- Complete timeline update (FR40 satisfied)

### VmStatus Resolution: READY vs RUNNING

**Decision:** Add `READY` to `VmStatus` enum (distinct from `RUNNING`).

- `READY` = VM just provisioned, initial state after successful provisioning
- `RUNNING` = VM confirmed running via live vCenter query (future lifecycle management)

```kotlin
// dvmm-domain/.../vm/VmStatus.kt
public enum class VmStatus {
    PROVISIONING,
    READY,      // NEW - post-provisioning initial state
    RUNNING,    // Live status from vCenter
    STOPPED,
    DELETED,
    FAILED
}
```

This aligns with `VmRequestStatus.READY` which already exists.

### VmProvisioningResult Value Object

```kotlin
// dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/vm/VmProvisioningResult.kt
public data class VmProvisioningResult(
    val vmwareVmId: VmwareVmId,
    val ipAddress: String?,          // null if timeout
    val hostname: String,
    val warningMessage: String?      // e.g., "IP detection timed out"
)
```

### VMware Disk Size Extension Pattern

```kotlin
// After CloneVM_Task completes, resize disk if needed
// Only required if template disk < requested disk size
val diskSpec = VirtualDeviceConfigSpec().apply {
    operation = VirtualDeviceConfigSpecOperation.EDIT
    device = existingDisk.apply {
        capacityInBytes = spec.diskGb * 1024L * 1024L * 1024L
    }
}

// Find existing disk from VM config
val vmConfig = getProperty(session, vmRef, "config") as VirtualMachineConfigInfo
val existingDisk = vmConfig.hardware.device
    .filterIsInstance<VirtualDisk>()
    .firstOrNull() ?: throw VsphereError.ProvisioningError("No disk found")

// Apply reconfigure if resize needed
if (existingDisk.capacityInBytes < spec.diskGb * 1024L * 1024L * 1024L) {
    val reconfigSpec = VirtualMachineConfigSpec().apply {
        deviceChange.add(diskSpec)
    }
    val task = vimPort.reconfigVMTask(vmRef, reconfigSpec)
    waitForTask(session, task)
}
```

### Guest Customization Pattern

```kotlin
val customSpec = CustomizationSpec().apply {
    identity = CustomizationLinuxPrep().apply {
        domain = "local"
        hostName = CustomizationFixedName().apply { name = spec.name }
    }
    nicSettingMap = listOf(
        CustomizationAdapterMapping().apply {
            adapter = CustomizationIPSettings().apply {
                ip = CustomizationDhcpIpGenerator()
            }
        }
    )
    globalIPSettings = CustomizationGlobalIPSettings()
}
cloneSpec.customization = customSpec
```

### VMware Tools Wait Pattern (with Timeout Handling)

```kotlin
suspend fun waitForVmwareTools(
    session: VsphereSession,
    vmRef: ManagedObjectReference,
    timeout: Duration = 2.minutes
): VmProvisioningResult {
    val startTime = Instant.now()
    val vmId = VmwareVmId(vmRef.value)

    while (Duration.between(startTime, Instant.now()) < timeout) {
        val guestInfo = getProperty(session, vmRef, "guest") as? GuestInfo

        if (guestInfo?.toolsRunningStatus == "guestToolsRunning") {
            val ip = guestInfo.ipAddress
            if (!ip.isNullOrBlank()) {
                return VmProvisioningResult(
                    vmwareVmId = vmId,
                    ipAddress = ip,
                    hostname = guestInfo.hostName ?: spec.name,
                    warningMessage = null
                )
            }
        }
        delay(5.seconds)
    }

    // Timeout: return result with warning
    return VmProvisioningResult(
        vmwareVmId = vmId,
        ipAddress = null,
        hostname = spec.name,
        warningMessage = "VMware Tools timeout - IP detection pending"
    )
}
```

### Domain Event Flow (Complete)

```
[Story 3.3 - COMPLETE]
VmRequestApproved → ProvisionVmCommand → VmAggregate.startProvisioning()
                                        → VmProvisioningStarted event
                                        → VspherePort.createVm() triggered

[Story 3.4 - THIS STORY]
VspherePort.createVm() → VmProvisioningResult(vmId, ip?, hostname, warning?)
                       → VmAggregate.markProvisioned()
                       → VmProvisioned event
                       → VmRequestAggregate.markReady()
                       → VmRequestReady event
                       → Timeline: "VM ready - IP: x.x.x.x" or "VM ready (IP pending)"
```

### CQRS Pattern (CRITICAL)

When `VmProvisioned` is persisted, BOTH write-side AND read-side must be updated:

```kotlin
suspend fun handleVmProvisioned(vmId: VmId, result: VmProvisioningResult) {
    // 1. Write-side: Update VmAggregate and persist event
    vmAggregate.markProvisioned(result.vmwareVmId, result.ipAddress, result.hostname, result.warningMessage, metadata)
    eventStore.append(vmAggregate.id.value, vmAggregate.uncommittedEvents, expectedVersion)

    // 2. Read-side: Update timeline projection
    val details = if (result.ipAddress != null) {
        "VM ready - IP: ${result.ipAddress}"
    } else {
        "VM ready (IP pending)"
    }
    timelineUpdater.addTimelineEvent(NewTimelineEvent(
        vmRequestId = requestId,
        eventType = TimelineEventType.VM_READY,
        details = details,
        occurredAt = Instant.now()
    ))
}
```

### Testing Standards

- **Tests First:** Write unit tests for new events before implementation
- **Integration Tests:** Use `VcsimAdapter` (not real vCenter) for Story 3.4
- **MockK:** Use `any()` for ALL parameters when stubbing functions with default arguments
- **Coverage:** Maintain ≥70% line coverage and ≥70% mutation score
- **Event Deserializer:** Add tests for `VmProvisioned` and `VmRequestReady` deserialization
- **Reconstitution:** Test that `VmAggregate.reconstitute()` and `VmRequestAggregate.reconstitute()` work with new events

### ARM64 VCSIM Limitation

Integration tests using VCSIM are skipped on ARM64 (Apple Silicon). Tests run on x86_64 CI.

```kotlin
@EnabledIf(value = "isNotArm64", disabledReason = "VCSIM not available on ARM64")
```

### Project Structure

**Files to Create:**
- `dvmm-domain/.../vm/events/VmProvisioned.kt`
- `dvmm-domain/.../vm/VmProvisioningResult.kt` (value object)
- `dvmm-domain/.../vmrequest/events/VmRequestReady.kt`

**Files to Modify:**
- `dvmm-domain/.../vm/VmAggregate.kt` - Add `markProvisioned()`, `handleEvent`, `apply()`
- `dvmm-domain/.../vm/VmStatus.kt` - Add `READY` status
- `dvmm-domain/.../vmrequest/VmRequestAggregate.kt` - Add `markReady()`, `handleEvent`, `apply()`
- `dvmm-application/.../vmrequest/TimelineEventProjectionUpdater.kt` - Add `PROVISIONING_FAILED`
- `dvmm-application/.../ports/VspherePort.kt` - Update return type to `VmProvisioningResult`
- `dvmm-infrastructure/.../vmware/VsphereClient.kt` - Enhance `createVm()`, add wait logic
- `dvmm-infrastructure/.../vmware/VcsimAdapter.kt` - Return complete result
- `dvmm-infrastructure/.../eventsourcing/JacksonVmEventDeserializer.kt` - Add VmProvisioned
- `dvmm-infrastructure/.../eventsourcing/JacksonVmRequestEventDeserializer.kt` - Add VmRequestReady

## References

**Domain Patterns:**
- [VmAggregate.kt - Current aggregate structure]
- [project-context.md#Event Sourcing Defensive Patterns]
- [project-context.md#New Domain Event Checklist]

**VMware Patterns:**
- [VsphereClient.kt:493-557 - Current createVm implementation]
- [project-context.md#VMware VCF SDK 9.0 Patterns]
- [architecture.md#VMware vSphere Adapter Pattern]

**CQRS/Projections:**
- [project-context.md#CQRS Command Handler Pattern (CRITICAL)]
- [3-3-provisioning-trigger-on-approval.md#Dev Notes]

**Requirements:**
- [epics.md#Story 3.4: VM Provisioning Execution]
- [epics.md#FR34, FR35, FR36, FR37]

## Dev Agent Record

### Context Reference

- **Story 3.3 Learnings:**
  - `VmAggregate` established with PROVISIONING/FAILED states
  - Saga pattern wires `VmRequestApproved` → `ProvisionVmCommand`
  - `VspherePort.createVm()` returns basic `VmId`
  - Timeline events use `TimelineEventProjectionUpdater`
  - Idempotency check (status == APPROVED) prevents duplicates

- **Story 3.2 Learnings:**
  - `VsphereClient.createVm()` has CloneVM_Task implementation
  - Returns `Result<VmId, VsphereError>`
  - Uses `waitForTask()` for async task completion
  - Session management via `VsphereSessionManager`
  - Circuit breaker in `executeResilient()` wrapper

### Git Intelligence Summary

Recent commits:
- `fab69f3` - Story 3.3: Trigger VM provisioning on request approval
- `cbdcd6c` - Story 3.2: vSphere API Client with ARM64 VCSIM Support
- `ff30d9e` - Story 3.1.1: Migrate to VCF SDK 9.0

All VMware foundation work complete. Story 3.4 builds on stable vSphere client implementation.

### Latest Tech Information

**VMware vSphere 8.0 Guest Customization:**
- `CustomizationSpec` supports Linux (CustomizationLinuxPrep) and Windows
- DHCP is simplest for MVP; static IP requires full IP settings
- VMware Tools must be installed in template for customization to work
- Tools status: check `guest.toolsRunningStatus` property

**VCF SDK 9.0 CloneVM_Task:**
- `VirtualMachineCloneSpec.customization` field for guest customization
- `VirtualMachineRelocateSpec.diskMoveType` for thick/thin provisioning
- Task result is ManagedObjectReference to cloned VM

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

### Completion Notes List

- **MVP-DEFERRED:** Guest customization (AC3), disk size extension, and network configuration are not implemented in this story. The core provisioning flow works with template defaults. These can be added in a follow-up story if needed.
- **VMware Tools IP Detection:** Successfully implemented with 2-minute timeout, returns `VmProvisioningResult` with `ipAddress = null` and warning message on timeout.
- **CancellationException Pattern:** Added to documentation (CLAUDE.md, GEMINI.md, AGENTS.md, project-context.md) to prevent future coroutine cancellation issues.

### File List

**Created:**
- `dvmm/dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/vm/VmProvisioningResult.kt`
- `dvmm/dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/vm/events/VmProvisioned.kt`
- `dvmm/dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/vmrequest/events/VmRequestReady.kt`
- `docs/sprint-artifacts/validation-report-3-4-vm-provisioning-execution-2025-12-07.md`

**Modified:**
- `dvmm/dvmm-app/src/main/kotlin/de/acci/dvmm/config/ApplicationConfig.kt`
- `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vm/TriggerProvisioningHandler.kt`
- `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmrequest/TimelineEventProjectionUpdater.kt`
- `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmware/VspherePort.kt`
- `dvmm/dvmm-application/src/main/kotlin/de/acci/dvmm/application/vmware/VsphereTypes.kt`
- `dvmm/dvmm-application/src/test/kotlin/de/acci/dvmm/application/vm/TriggerProvisioningHandlerTest.kt`
- `dvmm/dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/vm/VmAggregate.kt`
- `dvmm/dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/vm/VmStatus.kt`
- `dvmm/dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/vm/VmwareVmId.kt`
- `dvmm/dvmm-domain/src/main/kotlin/de/acci/dvmm/domain/vmrequest/VmRequestAggregate.kt`
- `dvmm/dvmm-domain/src/test/kotlin/de/acci/dvmm/domain/vm/VmAggregateTest.kt`
- `dvmm/dvmm-domain/src/test/kotlin/de/acci/dvmm/domain/vmrequest/VmRequestAggregateTest.kt`
- `dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/eventsourcing/JacksonVmEventDeserializer.kt`
- `dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/eventsourcing/JacksonVmRequestEventDeserializer.kt`
- `dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/vmware/VcenterAdapter.kt`
- `dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/vmware/VcsimAdapter.kt`
- `dvmm/dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/vmware/VsphereClient.kt`
- `dvmm/dvmm-infrastructure/src/test/kotlin/de/acci/dvmm/infrastructure/eventsourcing/JacksonVmEventDeserializerTest.kt`
- `dvmm/dvmm-infrastructure/src/test/kotlin/de/acci/dvmm/infrastructure/eventsourcing/JacksonVmRequestEventDeserializerTest.kt`
- `dvmm/dvmm-infrastructure/src/test/kotlin/de/acci/dvmm/infrastructure/vmware/VcenterAdapterTest.kt`
- `dvmm/dvmm-infrastructure/src/test/kotlin/integration/vmware/VsphereClientIntegrationTest.kt`

**Documentation Updated:**
- `AGENTS.md` - Added CancellationException handling pattern
- `CLAUDE.md` - Added CancellationException handling pattern
- `GEMINI.md` - Added CancellationException handling pattern
- `docs/project-context.md` - Added CancellationException handling pattern
- `docs/sprint-artifacts/sprint-status.yaml`
- `docs/sprint-artifacts/3-4-vm-provisioning-execution.md`
