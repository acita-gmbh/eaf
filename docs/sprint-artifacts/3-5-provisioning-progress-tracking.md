# Story 3.5: provisioning-progress-tracking

Status: done

## Story

As an **end user**,
I want to see provisioning progress,
so that I know my VM is being created.

## Acceptance Criteria

**Given** my request is in "Provisioning" status
**When** I view the request detail
**Then** I see progress stages:
- ○ Created (queued)
- ● Cloning from template... (in progress)
- ○ Apply configuration
- ○ Start VM
- ○ Wait for network
- ○ Ready

**And** progress updates without page refresh (SSE/polling)
**And** each stage shows timestamp when completed
**And** estimated remaining time shown (based on average)
**And** current stage animated (spinner)

**Given** provisioning takes longer than expected
**When** 10 minutes pass
**Then** status shows: "Provisioning is taking longer than usual..."

### Satisfied Functional Requirements
- FR37: System tracks provisioning progress and updates status

## Tasks / Subtasks

- [x] **Domain:** Define `VmProvisioningStage` enum for tracking progress
  - [x] Add `CLONING`, `CONFIGURING`, `POWERING_ON`, `WAITING_FOR_NETWORK`, `READY` stages.
  - [x] **Note:** `CREATED` is a UI-only concept (not signaled by vSphere) - kept in frontend only.
- [x] **Domain:** Add `VmProvisioningProgressUpdated` event to `VmAggregate`
  - [x] Include `currentStage: VmProvisioningStage`, `details: String`.
  - [x] **Constraint:** Do NOT include granular `progressPercent` to prevent Event Store flooding. Track discrete stages only.
- [x] **Application:** Update `VspherePort` interface with Progress Callback
  - [x] Signature: `suspend fun createVm(spec: VmSpec, onProgress: suspend (VmProvisioningStage) -> Unit = {}): Result<VmProvisioningResult, VsphereError>`
  - [x] Use default argument `{}` for backward compatibility with existing tests.
- [x] **Infrastructure:** Modify `VsphereClient.createVm`
  - [x] Accept `onProgress` callback.
  - [x] Invoke `onProgress(CLONING)` before clone task.
  - [x] Invoke `onProgress(CONFIGURING)` after clone, before customization.
  - [x] Invoke `onProgress(WAITING_FOR_NETWORK)` before tools wait.
  - [x] **Note:** Do NOT depend on Domain Events in Infrastructure. Pass simple Enum.
- [x] **Infrastructure:** Update `VcsimAdapter`
  - [x] Simulate delays between stages (e.g., `delay(500ms)`).
  - [x] Invoke `onProgress` callback to simulate realistic progression for tests.
- [x] **Infrastructure:** Update event deserializers
  - [x] Add `VmProvisioningProgressUpdated` to `JacksonVmEventDeserializer.resolveEventClass()`
- [x] **Application:** Update `TriggerProvisioningHandler` (Saga)
  - [x] Pass a lambda to `vspherePort.createVm` that:
    1. Loads the `VmAggregate`.
    2. Calls `aggregate.updateProgress(stage)`.
    3. Persists the new event (separate transaction per progress update).
- [x] **Application:** Create `VmProvisioningProgressProjection` & Repository
  - [x] Table `provisioning_progress`: `vm_request_id` (PK), `stage`, `updated_at`.
  - [x] **Cleanup Logic:** When `VmProvisioned` or `VmProvisioningFailed` event is handled, DELETE the row from this table (data is ephemeral).
- [x] **Application:** Create `VmProvisioningProgressQueryService`
  - [x] Endpoint `GET /api/requests/{id}/provisioning-progress`.
- [x] **Frontend:** Integrate with `VmProvisioningProgressQueryService`
  - [x] Use TanStack Query with `refetchInterval` (3s) for polling.
  - [x] Display discrete stages (stepper UI).
- [x] **Tests:** Integration test using VCSIM to verify intermediate events are persisted and projection is updated.

### Review Follow-ups (AI)

- [x] **(AI-Review, HIGH)** AC implemented: "estimated remaining time shown (based on average)" - Added `ESTIMATED_STAGE_DURATIONS` map in `VmProvisioningProgressProjection`, ETA calculation in `calculateEstimatedRemaining()`, and frontend display with `formatEta()` helper.
- [x] **(AI-Review, HIGH)** AC implemented: "each stage shows timestamp when completed" - Added `stage_timestamps` JSONB column, handler accumulates per-stage timestamps, frontend displays correct timestamp for each completed stage.

## Dev Agent Record

### Context Reference

- **Epic 3 Learnings:** Focus on Critical Risk of VMware integration, VCSIM for testing, idempotent provisioning, and circuit breaker.
- **Story 3.4 Learnings:** Core VM creation (CloneVM_Task), `VmProvisioned` event, `VmProvisioningResult`, `VmStatus.READY`, `VsphereClient.createVm()` and `VmProvisioningCompletionHandler`. Emphasis on two-sided update (event store + projection).
- **Git Intelligence:** Recent commits show a solid foundation in VMware integration (Story 3.3 and 3.4), making this story a logical next step to add user-facing progress.
- **Project Context Bible:** Critical guidelines on Kotlin style, module boundaries, testing, security (404 for forbidden), CQRS, and jOOQ projection symmetry are paramount. `CancellationException` rethrowing in coroutines is a general rule.

### Agent Model Used

gemini-1.5-pro

### Debug Log References

### Completion Notes List
- Implemented `VmProvisioningStage` and `VmProvisioningProgressUpdated` domain event.
- Updated `VmAggregate` to handle progress updates.
- Added comprehensive unit tests in `VmAggregateProgressTest`.
- Updated `VspherePort` interface with `onProgress` callback.
- Implemented progress tracking in `VsphereClient` (Infrastructure) calling the callback.
- Modified `VcsimAdapter` to simulate progress delays.
- Enhanced `JacksonVmEventDeserializer` to handle the new event.
- Extended `TriggerProvisioningHandler` to persist progress events during provisioning.
- Created `provisioning_progress` table and jOOQ mapping.
- Implemented `VmProvisioningProgressProjection`, Repository, and Query Service.
- Exposed progress via `VmProvisioningProgressController`.
- Implemented frontend progress tracking with `useProvisioningProgress` hook and `ProvisioningProgress` component.
- **[2025-12-08]** Added per-stage timestamps: V010 migration adds `stage_timestamps` JSONB column, handler accumulates timestamps per stage, frontend displays correct timestamp for each completed stage.
- **[2025-12-08]** Added ETA calculation: `ESTIMATED_STAGE_DURATIONS` map with typical stage durations, `calculateEstimatedRemaining()` computes remaining seconds, frontend displays "ETA: ~Xm Ys" with Clock icon.

### File List
- Ultimate context engine analysis completed - comprehensive developer guide created on 2025-12-07.
- dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vm/VmProvisioningStage.kt
- dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vm/events/VmProvisioningProgressUpdated.kt
- dcm/dcm-domain/src/main/kotlin/de/acci/dcm/domain/vm/VmAggregate.kt
- dcm/dcm-domain/src/test/kotlin/de/acci/dcm/domain/vm/VmAggregateProgressTest.kt
- dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vmware/VspherePort.kt
- dcm/dcm-infrastructure/src/main/kotlin/de/acci/dcm/infrastructure/vmware/VcenterAdapter.kt
- dcm/dcm-infrastructure/src/main/kotlin/de/acci/dcm/infrastructure/vmware/VsphereClient.kt
- dcm/dcm-infrastructure/src/main/kotlin/de/acci/dcm/infrastructure/vmware/VcsimAdapter.kt
- dcm/dcm-infrastructure/src/main/kotlin/de/acci/dcm/infrastructure/eventsourcing/JacksonVmEventDeserializer.kt
- dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vm/TriggerProvisioningHandler.kt
- dcm/dcm-infrastructure/src/main/resources/db/migration/V009__create_provisioning_progress_table.sql
- dcm/dcm-infrastructure/src/main/resources/db/migration/V010__add_stage_timestamps_to_progress.sql
- dcm/dcm-infrastructure/src/main/resources/db/jooq-init.sql
- dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vm/VmProvisioningProgressProjection.kt
- dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vm/VmProvisioningProgressProjectionRepository.kt
- dcm/dcm-infrastructure/src/main/kotlin/de/acci/dcm/infrastructure/projection/VmProvisioningProgressProjectionRepositoryAdapter.kt
- dcm/dcm-application/src/main/kotlin/de/acci/dcm/application/vm/VmProvisioningProgressQueryService.kt
- dcm/dcm-api/src/main/kotlin/de/acci/dcm/api/controller/VmProvisioningProgressController.kt
- dcm/dcm-web/src/api/vm-requests.ts
- dcm/dcm-web/src/hooks/useProvisioningProgress.ts
- dcm/dcm-web/src/components/requests/ProvisioningProgress.tsx
- dcm/dcm-web/src/pages/RequestDetail.tsx
- dcm/dcm-app/src/main/kotlin/de/acci/dcm/config/ApplicationConfig.kt
- dcm/dcm-app/src/test/kotlin/de/acci/dcm/vmrequest/VmProvisioningIntegrationTest.kt
- dcm/dcm-application/src/test/kotlin/de/acci/dcm/application/vm/TriggerProvisioningHandlerTest.kt
- dcm/dcm-infrastructure/src/test/kotlin/de/acci/dcm/infrastructure/vmware/VcenterAdapterTest.kt
- dcm/dcm-web/src/components/requests/ProvisioningProgress.test.tsx
- dcm/dcm-web/src/pages/RequestDetail.test.tsx
- docs/sprint-artifacts/sprint-status.yaml
- docs/sprint-artifacts/validation-report-3-5-provisioning-progress-tracking-2025-12-07.md
