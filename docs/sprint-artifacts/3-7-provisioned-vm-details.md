# Story 3.7: Provisioned VM Details

Status: ready-for-dev

## Story

As an **end user**,
I want to see the details of my provisioned VM (IP, status, connection info),
so that I can connect to and utilize the virtual machine I requested.

## Acceptance Criteria

### AC-3.7.1: VM Details View
**Given** my VM is successfully provisioned (Status: ACTIVE/READY)
**When** I view the request detail page
**Then** I see a "VM Details" card containing:
- **Status Badge:** "Ready" (Green/Emerald-500)
- **Power State:** "Running" (or current state)
- **Network Info:**
  - IP Address: `192.168.x.x` (Click to copy)
  - Hostname: `{project}-{name}`
- **Specs:** vCPU, RAM, Disk (as provisioned)
- **Uptime:** Duration since last boot (e.g., "2 days, 4 hours")
- **Created:** Timestamp

### AC-3.7.2: Connection Instructions
**Given** the VM details are visible
**When** I look for connection info
**Then** I see context-aware instructions based on OS:
- **Linux:** `ssh {user}@{ip_address}` (Copy button)
- **Windows:** `mstsc /v:{ip_address}` (Copy button)
- **Console:** "Open Web Console" link (opens in new tab)

### AC-3.7.3: Live Status Refresh
**Given** the VM details might be stale
**When** I click "Refresh Status"
**Then** the system queries vSphere for the latest Power State and IP
**And** if changed, updates the display immediately
**And** updates the persistence (projection) with new values

### AC-3.7.4: VM Actions (Basic)
**Given** the VM is running
**When** I view the actions menu
**Then** I see options:
- Refresh Status
- (Future: Restart, Power Off - disabled/hidden for now if not in scope, but UI should support the slot)

### Satisfied Functional Requirements
- FR40: Users can view provisioned VM details (IP, status)

## Tasks / Subtasks

### Backend Tasks

- [ ] **Task 1: Extend VmRequest Projection (AC: 3.7.1)**
  - [ ] Update `dvmm_vms` table (created in Epic 1/2, verify columns) to ensure it has `ip_address`, `hostname` (actual Guest OS hostname), `power_state`, `guest_os` (critical for connection instructions).
  - [ ] Update `VmRequestProjectionHandler` to map `VmProvisioned` event data (IP, Hostname) to the `dvmm_vms` table.
  - [ ] Ensure `VmProvisioned` event *actually* contains IP/Hostname (verify Story 3.4 implementation). If not, creating a `VmDetailsUpdated` event might be needed.

- [ ] **Task 2: Implement GetVmDetailsQuery (AC: 3.7.1)**
  - [ ] Use existing `VmDto` (or extend/create `VmDetailsDto` if distinct view needed) ensuring alignment with API contracts.
  - [ ] Implement `GetVmDetailsQuery` handler using jOOQ to fetch from `dvmm_vms`.
  - [ ] Ensure IP address, Hostname, and `bootTime` (for uptime calculation) are exposed in the API response.

- [ ] **Task 3: Implement SyncVmStatus Command (AC: 3.7.3)**
  - [ ] Create `SyncVmStatusCommand(vmId)` and handler.
  - [ ] Handler calls `vspherePort.getVm(vmId)`.
  - [ ] Compare current state with aggregate state.
  - [ ] If different (e.g., Power State changed, IP changed), emit `VmStatusChanged` event.
  - [ ] `VmAggregate` applies `VmStatusChanged`.
  - [ ] Projection updates `dvmm_vms` table.

- [ ] **Task 4: Implement VspherePort.getVm (AC: 3.7.3)**
  - [ ] Implement `getVm(vmId)` in `VsphereClient` (using official SDK).
  - [ ] **CRITICAL:** Consult `GEMINI.md` for VCF SDK 9.0 patterns (e.g., UPPER_SNAKE_CASE enums).
  - [ ] Map vSphere `GuestInfo` (ipAddress, hostName) and `Runtime` (powerState, bootTime) to domain model.
  - [ ] Ensure `guest_os` is captured for frontend connection instructions logic.
  - [ ] Update `VcsimAdapter` to return simulated runtime info including mock `bootTime`.

### Frontend Tasks

- [ ] **Task 5: VM Details Component (AC: 3.7.1, 3.7.2)**
  - [ ] Create `VmDetailsCard.tsx` using shadcn Card.
  - [ ] Implement status badge (Green for Running) mapped from `VmDto.status` / `VmDto.powerState`.
  - [ ] Implement "Copy to Clipboard" for IP and SSH command.
  - [ ] Conditional rendering for OS-specific instructions (SSH vs RDP) based on `guest_os`.
  - [ ] Calculate and display "Uptime" using `bootTime` from API response.

- [ ] **Task 6: Integrate Sync Action (AC: 3.7.3)**
  - [ ] Add "Refresh" button to `VmDetailsCard`.
  - [ ] Call `POST /api/vms/{id}/sync` on click.
  - [ ] Show loading spinner while syncing.
  - [ ] Invalidate React Query cache to refresh data after sync.

## Dev Notes

### Architecture Patterns & Constraints
-   **CQRS Separation:** Do NOT query vSphere directly in the `GET` request. The `GET` request must remain fast and read from the DB projection. Use the "Sync" pattern (Task 3) to fetch fresh data from vSphere.
-   **Event Consistency:** The `dvmm_vms` table is the read model. It is updated *only* by events (`VmProvisioned`, `VmStatusChanged`).
-   **VCSIM Support:** The `VcsimAdapter` must simulate a running VM with an IP address after provisioning is complete. Hardcode a mock IP (e.g., `192.168.1.100`) in the VCSIM adapter for testing.

### Source Tree Locations
-   `dvmm-application/src/main/kotlin/de/acci/dvmm/application/vm/SyncVmStatusHandler.kt` (New)
-   `dvmm-infrastructure/src/main/kotlin/de/acci/dvmm/infrastructure/vmware/VsphereClient.kt` (Update)
-   `dvmm-web/src/components/vms/VmDetailsCard.tsx` (New)

### Testing Standards
-   **Integration Test:** Test `SyncVmStatusHandler` with `VcsimAdapter`. Verify that calling sync triggers an event if data changed.
-   **E2E Test:** Verify the "VM Details" card appears after the "Provisioning" state is complete.

### Previous Story Learnings (3.6)
-   Re-use the `VsphereError` handling if `getVm` fails (e.g., VM deleted manually on vCenter).
-   Ensure `VmProvisioned` event payload schema matches what the projection expects.

## Dev Agent Record

### Context Reference
-   **Architecture:** docs/architecture.md (CQRS, Hexagonal)
-   **UX:** docs/ux/design-specification.md (VM Details Card)
-   **Epics:** docs/epics.md (Story 3.7)

### Agent Model Used
Gemini 2.0 Flash (via BMad)
