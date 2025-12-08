# Validation Report

**Document:** `docs/sprint-artifacts/3-5-provisioning-progress-tracking.md`
**Checklist:** `.bmad/bmm/workflows/4-implementation/create-story/checklist.md`
**Date:** 2025-12-07

## Summary
- **Overall:** PASS with Enhancements
- **Critical Issues:** 2
- **Enhancement Opportunities:** 3

## Section Results

### 1. Reinvention Prevention
**PASS**. The story leverages existing patterns (Projections, Events) but introduces a new specific projection for progress which is appropriate for ephemeral/detailed data.

### 2. Technical Specifications
**PARTIAL**.
- **Issue 1 (Critical):** The mechanism for `VsphereClient` to emit progress is vague ("modify signature... or listener"). This interacts poorly with the `suspend` nature and transactional boundaries of `VmAggregate`.
- **Issue 2 (Critical):** Event Granularity. The task mentions `progressPercent: Int`. If the implementation emits an event for every 1% increment, it will flood the Event Store (100 events per VM provisioning). We must restrict this to *Stage Changes* or *Significant Steps* (e.g., every 25%).
- **Gap:** The database schema for `ProvisioningProgress` table is missing (columns).

### 3. File Structure
**PASS**. File list is comprehensive.

### 4. Regression Prevention
**PASS**. Tests include integration covering the full flow.

## Failed Items (Critical)

### 1. Event Store Flooding Risk (Event Granularity)
**Description:** The task "Include `progressPercent: Int`" combined with "periodically emit... events" risks flooding the immutable Event Store with high-volume, low-value data (e.g., 1%, 2%, 3%...).
**Impact:** Event Store bloat, slower replays, increased storage cost.
**Recommendation:** Explicitly constrain progress events to **Stage Changes** (e.g., CLONING -> CONFIGURING) and coarse-grained updates (e.g., maybe 50% clone). Do NOT stream raw percent updates into the Event Store. For smooth UI progress bars, the frontend should interpolate between stages or use a separate non-ES channel (but ES is chosen here).
**Fix:** Add "Guidance: Emit events ONLY on Stage transition or major checkpoints. Do not emit on granular percentage changes."

### 2. Layer Violation & Transactional Boundaries
**Description:** `VsphereClient` (Infrastructure) is tasked to "emit `VmProvisioningProgressUpdated` events". This is a Domain Event. Infrastructure should not depend on Domain Events. Also, `createVm` is a long-running process. Events must be persisted *during* execution, not at the end.
**Impact:** Tightly coupled architecture, potential transaction timeouts if not handled correctly (waiting 5 mins for transaction to commit).
**Recommendation:**
1.  `VsphereClient` should accept a functional callback: `onProgress: suspend (ProgressReport) -> Unit`.
2.  The `ProgressReport` should be a simple DTO, not a Domain Event.
3.  The calling Application Service (`TriggerProvisioningHandler`) implements the callback to:
    -   Load Aggregate
    -   Call `aggregate.updateProgress()`
    -   Save Aggregate (commit transaction)
    -   *Crucial:* Each progress update must be its own small transaction.

## Enhancement Opportunities

### 1. Backward Compatibility for VspherePort
The `createVm` signature change breaks existing code. Use a default argument for the listener:
`suspend fun createVm(spec: VmSpec, onProgress: suspend (VmProvisioningStage) -> Unit = {})`
This allows existing tests/code to run without modification.

### 2. Ephemeral Data Lifecycle
The `VmProvisioningProgressProjection` table will grow indefinitely.
**Add Task:** "Cleanup: When `VmProvisioned` (Final Success) or `VmProvisioningFailed` event is received, the handler should DELETE the row from `VmProvisioningProgressProjection` (or mark it complete)." The UI can then fall back to the "Status: Ready" from the main request view.

### 3. VCSIM Simulation
VCSIM needs to be updated to actually *simulate* the delay and emit the intermediate stages, otherwise integration tests will just jump 0 -> 100%.

## LLM Optimization
The "Dev Notes" section is very verbose with copy-pasted context. This can be summarized to save tokens for the developer agent, focusing only on *what is different* or *specifically required* for this story.

## Recommendations
1.  **Must Fix:** Refine `VsphereClient` interaction pattern (Callback, not Event emission) and restrict Event granularity.
2.  **Should Improve:** Add cleanup logic for the projection table.
3.  **Optimize:** Trim "Dev Notes" of generic context.
