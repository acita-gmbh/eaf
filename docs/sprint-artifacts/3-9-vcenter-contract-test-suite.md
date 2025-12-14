# Story 3.9: vCenter Contract Test Suite

Status: drafted

## Story

As a **development team**,
We want a contract test suite that validates our VCSIM-based tests against a real vCenter Server,
So that we can confidently deploy to production knowing our API assumptions are correct.

## Business Value

**Risk Mitigation:** VCSIM (vCenter Simulator) may not perfectly replicate real vCenter behavior. This story creates a validation layer ensuring our integration tests accurately reflect production API behavior.

**Go-Live Gate:** Phase 1 smoke tests serve as a deployment prerequisite - DCM cannot go live until basic vCenter connectivity and provisioning work against real infrastructure.

## Acceptance Criteria

### AC-3.9.1: Smoke Test Suite (Go-Live Gate)

**Given** a configured vCenter test environment with valid credentials
**When** the smoke test suite runs
**Then** all critical paths must pass:
- [ ] Connection test succeeds (`testConnection()` returns `ConnectionInfo`)
- [ ] Datacenter discovery returns expected datacenter
- [ ] Cluster discovery returns at least one cluster
- [ ] Template exists and is accessible
- [ ] Basic VM clone operation completes within 5 minutes
- [ ] VM cleanup (delete) succeeds

**Blocking:** These tests MUST pass before production deployment.

### AC-3.9.2: Contract Test Suite (Post Go-Live)

**Given** existing VCSIM-based test expectations
**When** running the same test scenarios against real vCenter
**Then** API responses must match VCSIM behavior for:
- [ ] `listDatacenters()` response structure
- [ ] `listClusters(datacenter)` response structure
- [ ] `listDatastores(cluster)` response structure
- [ ] `listNetworks(datacenter)` response structure
- [ ] `createVm(spec)` progress callback sequence (CLONING → CONFIGURING → POWERING_ON → WAITING_FOR_NETWORK → READY)
- [ ] `VmProvisioningResult` field population (vmwareVmId format, IP address presence)
- [ ] Error response mapping (`VsphereError` sealed class variants)

### AC-3.9.3: Stress Test Suite (Post MVP)

**Given** an isolated vCenter test cluster
**When** running concurrent provisioning requests
**Then** the system handles:
- [ ] 5 concurrent VM provisions without resource contention
- [ ] Circuit breaker activation under simulated API degradation
- [ ] Saga compensation for partially failed provisions
- [ ] Rate limiting at the vCenter API level

**Note:** Stress tests require dedicated test infrastructure and are deferred to post-MVP.

### Satisfied Functional Requirements

- **FR77:** System validates vCenter connectivity before provisioning
- **FR78:** System gracefully degrades when vCenter is unavailable
- **FR79:** API contract validation ensures VCSIM accuracy

## Technical Context

### Why This Story Exists

Our Epic 3 implementation uses VCSIM (VMware vCenter Simulator) for all integration tests. While VCSIM provides a convenient testing environment, it has known limitations:

1. **Simplified Responses:** VCSIM returns minimal/mock data that may differ from production
2. **No Real Resource Constraints:** VCSIM doesn't enforce CPU/memory limits
3. **Timing Differences:** Real vCenter operations take longer (networking, disk I/O)
4. **VMware Tools Behavior:** VCSIM simulates instant tool readiness; real VMs need boot time
5. **VCF SDK 9.0 Behavior:** Empty KeyStore SSL handling needs verification (see `VcenterAdapter.kt:136-142`)

### VCF SDK 9.0 Considerations

Per [VMware's VCF SDK 9.0 announcement](https://blogs.vmware.com/cloud-foundation/2025/06/24/introducing-a-unified-vcf-sdk-9-0-for-python-and-java/):

- **Unified Authentication:** Single session can be reused across SOAP (vSphere Web Services) and REST (Automation APIs)
- **Jakarta Migration:** Requires `jakarta.*` imports instead of `javax.*` (breaking change in 9.x)
- **OpenAPI 3.0:** ~90% of VCF APIs now have OpenAPI specs (useful for contract generation)

Our `VcenterAdapter` uses the SOAP API (`VcenterClientFactory`). Key behaviors to validate:
- Session management and cleanup
- `PropertyCollector` query patterns
- `SearchIndex.findByInventoryPath()` path format expectations

### Current VCSIM Test Infrastructure

```
eaf/eaf-testing/
└── src/testFixtures/kotlin/de/acci/eaf/testing/
    ├── vcsim/
    │   ├── VcsimTest.kt           # @VcsimTest annotation
    │   └── VcsimContainerUtil.kt  # Container management
    └── TestContainers.kt          # Shared containers
```

**Known VCSIM Limitation:** VCF SDK 9.0's `VcenterClientFactory` only supports port 443. VCSIM Testcontainers uses dynamic ports (e.g., 55123), preventing SDK testing against VCSIM containers. This is documented in `VcenterAdapterVcsimIntegrationTest.kt`.

## Tasks / Subtasks

### Phase 1: Smoke Tests (Go-Live Gate)

- [ ] **Task 1: Create Test Infrastructure Profile (AC: 3.9.1)**
  - [ ] Add `vcenter-test` Spring profile in `application-vcenter-test.yaml`
  - [ ] Configure from environment variables: `VCENTER_URL`, `VCENTER_USER`, `VCENTER_PASSWORD`
  - [ ] Document required vCenter test environment setup in README
  - [ ] Create `@VcenterTest` annotation that skips when env vars are missing

- [ ] **Task 2: Implement Smoke Test Suite (AC: 3.9.1)**
  - [ ] Create `VcenterSmokeTest.kt` in `dcm-infrastructure/src/test/kotlin/.../vmware/contract/`
  - [ ] Test `testConnection()` validates all configured resources exist
  - [ ] Test `listDatacenters()` returns non-empty list
  - [ ] Test `listClusters()` returns at least one cluster
  - [ ] Test VM clone lifecycle: create → verify → delete
  - [ ] Add CI workflow that runs smoke tests (manual trigger only)

- [ ] **Task 3: Document Go-Live Checklist (AC: 3.9.1)**
  - [ ] Create `docs/go-live-checklist.md` with smoke test requirements
  - [ ] Include vCenter configuration prerequisites
  - [ ] Define minimum test coverage before production deployment

### Phase 2: Contract Tests (Post Go-Live)

- [ ] **Task 4: Extract VCSIM Expectations (AC: 3.9.2)**
  - [ ] Document `VcsimAdapter` response formats in `contract-expectations.md`
  - [ ] Capture `VmwareVmId` format expectations (`vm-<number>` vs `VirtualMachine:*`)
  - [ ] Document progress callback sequence timing assumptions
  - [ ] List `VsphereError` sealed class variants and their trigger conditions

- [ ] **Task 5: Implement Contract Comparison Tests (AC: 3.9.2)**
  - [ ] Create `VcenterContractTest.kt` extending `VcenterSmokeTest`
  - [ ] Add assertions comparing real vCenter responses to VCSIM expectations
  - [ ] Flag discrepancies with detailed logging (not failures initially)
  - [ ] Create `ContractDiscrepancy` sealed class for structured reporting

- [ ] **Task 6: Update VCSIM if Discrepancies Found (AC: 3.9.2)**
  - [ ] For each discrepancy, determine if:
    - VCSIM should be updated to match real behavior, OR
    - Production code should handle both variants
  - [ ] Update `VcsimAdapter` to match real vCenter response formats
  - [ ] Add comments documenting why VCSIM differs (if intentionally simplified)

### Phase 3: Stress Tests (Post MVP)

- [ ] **Task 7: Implement Concurrency Tests (AC: 3.9.3)**
  - [ ] Test 5 parallel `createVm()` calls
  - [ ] Verify no VM naming collisions (correlation ID uniqueness)
  - [ ] Monitor vCenter resource consumption during stress

- [ ] **Task 8: Circuit Breaker Validation (AC: 3.9.3)**
  - [ ] Simulate vCenter API degradation (mock slow responses)
  - [ ] Verify resilience4j circuit breaker activates appropriately
  - [ ] Test request queuing behavior during circuit-open state

- [ ] **Task 9: Saga Compensation Under Load (AC: 3.9.3)**
  - [ ] Trigger partial failures during concurrent provisions
  - [ ] Verify all orphaned VMs are cleaned up
  - [ ] Test idempotency with duplicate correlation IDs

## Dev Notes

### Test Environment Requirements

```yaml
# Required environment for contract tests
VCENTER_URL: https://vcenter.test.company.com/sdk
VCENTER_USER: dcm-test@vsphere.local
VCENTER_PASSWORD: <from-vault>
VCENTER_DATACENTER: TestDatacenter
VCENTER_CLUSTER: TestCluster
VCENTER_DATASTORE: TestDatastore
VCENTER_NETWORK: "VM Network"
VCENTER_TEMPLATE: ubuntu-22-04-template
```

**Security:** Credentials should be stored in CI secrets, never committed. Test user should have minimal permissions (VM.Create, VM.Delete on test folder only).

### Test Isolation Strategy

```kotlin
// Each test provisions with unique name using correlation ID
val testVmName = "dcm-contract-test-${UUID.randomUUID().toString().take(8)}"

// Always cleanup, even on test failure
@AfterEach
fun cleanup() {
    createdVmIds.forEach { vmId ->
        runCatching { hypervisorPort.deleteVm(vmId) }
            .onFailure { logger.warn { "Cleanup failed for $vmId: ${it.message}" } }
    }
}
```

### Expected Discrepancies to Investigate

Based on code review and `VcenterAdapterVcsimIntegrationTest.kt` comments:

| Area | VCSIM Behavior | Real vCenter (Expected) | Action |
|------|----------------|-------------------------|--------|
| VM ID format | `vm-100` (hardcoded) | `vm-<dynamic>` | Verify real format |
| IP detection | Instant (`192.168.1.100`) | Delayed (VMware Tools) | Add timeout handling |
| Progress timing | 500ms delays | Minutes for cloning | Adjust timeout expectations |
| SSL/KeyStore | Not tested | Production cert chain | Verify `ignoreCert` behavior |
| PropertyCollector | Simplified | Full property traversal | Verify query patterns work |

### Pact JVM Integration (Optional Future Enhancement)

Per [Pact JVM documentation](https://docs.pact.io/implementation_guides/jvm/readme), contract testing typically uses consumer-provider pacts. However, VMware vCenter is an external system we don't control, making traditional Pact less applicable.

Our approach is **recording-based validation**:
1. Record real vCenter responses during contract tests
2. Store as "golden files" in `test/resources/contracts/`
3. Compare VCSIM behavior against golden files
4. Update golden files when vCenter API changes (versioned)

### CI/CD Integration

```yaml
# .github/workflows/vcenter-contract-tests.yml
name: vCenter Contract Tests
on:
  workflow_dispatch:  # Manual trigger only
    inputs:
      test_phase:
        description: 'Test phase to run'
        required: true
        default: 'smoke'
        type: choice
        options:
          - smoke
          - contract
          - stress

jobs:
  contract-tests:
    runs-on: ubuntu-latest
    environment: vcenter-test  # Requires approval
    steps:
      - uses: actions/checkout@v4
      - name: Run Contract Tests
        run: ./gradlew :dcm:dcm-infrastructure:test --tests "*VcenterContractTest*"
        env:
          VCENTER_URL: ${{ secrets.VCENTER_URL }}
          VCENTER_USER: ${{ secrets.VCENTER_USER }}
          VCENTER_PASSWORD: ${{ secrets.VCENTER_PASSWORD }}
```

### Source Tree Locations

**New Files (Phase 1):**
- `dcm/dcm-infrastructure/src/test/kotlin/de/acci/dcm/infrastructure/vmware/contract/VcenterSmokeTest.kt`
- `dcm/dcm-infrastructure/src/test/resources/application-vcenter-test.yaml`
- `docs/go-live-checklist.md`

**New Files (Phase 2):**
- `dcm/dcm-infrastructure/src/test/kotlin/de/acci/dcm/infrastructure/vmware/contract/VcenterContractTest.kt`
- `docs/sprint-artifacts/contract-expectations.md`

**New Files (Phase 3):**
- `dcm/dcm-infrastructure/src/test/kotlin/de/acci/dcm/infrastructure/vmware/contract/VcenterStressTest.kt`

### Previous Story Learnings

**From Story 3.1 (VMware Connection Configuration):**
- `VcenterConnectionParams` validates URL format at construction
- `testConnection()` validates all resources (datacenter, cluster, datastore, network, template)
- SSL handling with `ignoreCert` flag needs real vCenter verification

**From Story 3.2 (vSphere API Client):**
- `VsphereClient` handles session management and reconnection
- Circuit breaker wraps all vSphere operations
- `VcsimAdapter.SimulatedVsphereException` for testing saga compensation

**From Epic 3 Retrospective:**
- VCF SDK 9.0 port limitation documented (only supports 443)
- VCSIM testing strategy validated with mock adapter approach

### What's Already Implemented (Do NOT Duplicate)

| Feature | Status | Location |
|---------|--------|----------|
| `VcenterAdapter` | Done (3.1.1) | `dcm-infrastructure/vmware/VcenterAdapter.kt` |
| `VcsimAdapter` | Done (3.2) | `dcm-infrastructure/vmware/VcsimAdapter.kt` |
| `HypervisorPort` interface | Done (3.1) | `dcm-application/vmware/HypervisorPort.kt` |
| VCSIM integration tests | Done (1.10) | `eaf-testing/vcsim/` |
| Saga compensation tests | Done (3.6) | `VcsimAdapterSagaCompensationTest.kt` |

## Blocker Information

**Status:** BLOCKED - Awaiting vCenter Test Infrastructure

**Required:**
1. Dedicated vCenter test instance accessible from CI runners
2. Test user account with VM provisioning permissions
3. Pre-configured test datacenter, cluster, datastore, network
4. Ubuntu 22.04 VM template for provisioning tests

**Unblocking Path:**
- Infrastructure team to provision test vCenter (ETA: TBD)
- Alternatively: Use VMware Hands-On Lab (temporary, limited)
- Consider VMware Cloud Foundation evaluation license

## Project Context Reference

- **Architecture:** docs/architecture.md (ADR-004: Multi-Hypervisor, HypervisorPort abstraction)
- **Tech Spec:** docs/sprint-artifacts/tech-spec-epic-3.md (Section 4.1: Domain Model, Phase 2 strategy)
- **Previous Story:** docs/sprint-artifacts/3-8-vm-ready-notification.md
- **VCSIM Tests:** dcm/dcm-infrastructure/src/test/kotlin/.../vmware/VcenterAdapterVcsimIntegrationTest.kt

## Research Sources

- [VCF SDK 9.0 Java/Python Announcement](https://blogs.vmware.com/cloud-foundation/2025/06/24/introducing-a-unified-vcf-sdk-9-0-for-python-and-java/)
- [Unified Authentication in VCF SDK 9.0](https://blogs.vmware.com/cloud-foundation/2025/11/19/unified-authentication-in-vmware-cloud-foundation-sdk-9-0-seamless-authentication-across-vsphere-and-vsan-apis/)
- [Pact JVM Documentation](https://docs.pact.io/implementation_guides/jvm/readme)
- [Pact JVM GitHub Repository](https://github.com/pact-foundation/pact-jvm)

## Dev Agent Record

### Context Reference
- Story context loaded from epics.md (Story 3.9 definition), architecture.md, project-context.md
- Previous story analysis: 3-8-vm-ready-notification.md
- Codebase analysis: VcenterAdapter.kt, VcsimAdapter.kt, HypervisorPort.kt, VcenterAdapterVcsimIntegrationTest.kt
- BMAD contract testing knowledge: .bmad/bmm/testarch/knowledge/contract-testing.md

### Agent Model Used
Claude Opus 4.5 (via Claude Code)

### Validation Notes
- Story is BLOCKED pending vCenter test infrastructure
- Phased approach aligns with tech-spec-epic-3.md strategy
- Contract test approach uses recording-based validation (not traditional Pact)
- VCF SDK 9.0 port limitation acknowledged and documented
