# Story 1.10: VCSIM Integration

Status: review

## Story

As a **developer**,
I want VMware vCenter Simulator for integration tests,
so that I can test VMware operations without real infrastructure.

## Requirements Context Summary

- **Epic/AC source:** Story 1.10 in `docs/epics.md` — configure VCSIM (vCenter Server Simulator) container via Testcontainers for realistic VMware API testing without hardware dependencies.
- **Architecture constraint:** Ports & Adapters pattern requires VMware vSphere integration to be testable via test doubles; VCSIM provides official VMware-maintained API simulation.
- **Test Design guidance:** `docs/test-design-system.md` Section 7.3 — VCSIM over WireMock for realistic SOAP API responses, scalable inventories, official VMware support.
- **Prerequisites:** Story 1.9 (Testcontainers Setup) — Testcontainers infrastructure already established.
- **Epic 3 preparation:** VCSIM foundation enables Stories 3.1-3.8 (VMware provisioning) to be developed with test coverage before real vCenter access.

## Acceptance Criteria

1. **VCSIM container starts for VMware integration tests**
   - Given I run VMware integration tests
   - When the test class starts
   - Then VCSIM container is automatically started via Testcontainers
   - And the container provides vSphere API-compatible `/sdk` endpoint.

2. **VcsimTestFixture provides helper methods**
   - Given VCSIM container is running
   - When I use `VcsimTestFixture`
   - Then I can create test VMs via `createVm(spec: VmSpec): VmRef`
   - And I can create test networks via `createNetwork(name: String): NetworkRef`
   - And I can create test datastores via `createDatastore(name: String): DatastoreRef`
   - And I can simulate provisioning operations via `simulateProvisioning(vmRef: VmRef)`.

3. **Connection parameters injected via test properties**
   - Given VCSIM container is started
   - When the Spring context loads
   - Then vSphere connection properties are automatically configured:
     - `vsphere.url` = VCSIM container SDK URL
     - `vsphere.username` = "user" (VCSIM default)
     - `vsphere.password` = "pass" (VCSIM default)
   - And no manual configuration required in tests.

4. **VCSIM state resets between test classes**
   - Given test class A creates VMs in VCSIM
   - When test class B runs
   - Then test class B starts with fresh VCSIM state (no VMs from class A)
   - And state isolation is achieved via container restart or API reset.

5. **VCSIM container reused within test class (performance)**
   - Given multiple tests in the same test class
   - When tests run sequentially
   - Then the same VCSIM container instance is reused
   - And container startup overhead is minimized.

## Test Plan

- **Unit:** `VcsimTestFixture` helper method input validation.
- **Integration:** VCSIM container starts successfully with Testcontainers.
- **Integration:** `/sdk` endpoint responds to vSphere API calls.
- **Integration:** `createVm()` creates VM visible in VCSIM inventory.
- **Integration:** `createNetwork()` creates network visible in VCSIM.
- **Integration:** State resets between test classes (isolation verification).
- **Integration:** Connection properties are correctly injected.
- **Integration:** Multiple tests in same class reuse container instance.

## Structure Alignment / Previous Learnings

### Learnings from Previous Story

#### From Story 1-8-jooq-projection-base (Status: done)

- **Module Pattern Established:** New modules follow pattern of `eaf-{module}/build.gradle.kts` with `eaf.kotlin-conventions` plugin.
- **Library vs Application:** Library modules must disable `bootJar` and enable `jar`.
- **Test Fixtures:** Use `src/testFixtures` for shared test utilities — `VcsimTestFixture` should follow this pattern.
- **Testcontainers Pattern:** Singleton containers via companion object established in Story 1.9.
- **Property Injection:** `@DynamicPropertySource` pattern for container-based properties.

[Source: docs/sprint-artifacts/1-8-jooq-projection-base.md#Dev-Agent-Record]

### Project Structure Notes

- `eaf-testing` module contains shared test utilities (`TenantTestContext`, `ProjectionTestUtils`).
- VCSIM fixture should be added to `eaf-testing/src/main/kotlin/de/acci/eaf/testing/vcsim/`.
- Testcontainers configuration follows singleton pattern from Story 1.9.
- Package: `de.acci.eaf.testing.vcsim`.

## Tasks / Subtasks

- [x] **Task 1: Add VCSIM Testcontainer dependency** (AC: 1)
  - [x] Add `vmware/vcsim` container support to `eaf-testing/build.gradle.kts`
  - [x] Note: vSphere SDK not needed - using Java HTTP client for /about endpoint verification
  - [x] Verify container image pulls successfully (vmware/vcsim:v0.47.0)

- [x] **Task 2: Create VcsimContainer class** (AC: 1, 3, 4, 5)
  - [x] Create `VcsimContainer` extending `GenericContainer` in `eaf-testing`
  - [x] Configure default VCSIM environment variables (VCSIM_CLUSTER, VCSIM_HOST, VCSIM_VM)
  - [x] Expose `/sdk` port (default: 8989)
  - [x] Implement `getSdkUrl()` method returning full vSphere SDK URL
  - [x] Add `getUsername()` and `getPassword()` methods (VCSIM defaults)

- [x] **Task 3: Create VcsimTestFixture helper class** (AC: 2)
  - [x] Create `VcsimTestFixture` class with VCSIM container reference
  - [x] Implement `createVm(spec: VmSpec): VmRef` helper
  - [x] Implement `createNetwork(name: String): NetworkRef` helper
  - [x] Implement `createDatastore(name: String): DatastoreRef` helper
  - [x] Implement `simulateProvisioning(vmRef: VmRef)` helper
  - [x] Implement `resetState()` for state cleanup between test classes
  - [x] Add unit tests for fixture methods (VcsimTypesTest)

- [x] **Task 4: Create @VcsimTest annotation** (AC: 3, 4, 5)
  - [x] Create `@VcsimTest` meta-annotation combining `@Testcontainers` + VCSIM config
  - [x] Implement `VcsimExtension` for lifecycle management
  - [x] Configure singleton container pattern for class-level reuse
  - [x] Implement automatic state reset via `@BeforeAll`/`@AfterAll`
  - [x] Add parameter injection for VcsimTestFixture and VcsimContainer

- [x] **Task 5: Create sample integration test** (AC: 1, 2, 3, 4, 5)
  - [x] Create `VcsimIntegrationTest` demonstrating all fixture capabilities
  - [x] Test: Container starts and `/sdk` endpoint responds
  - [x] Test: Create VM via fixture, verify moRef generated
  - [x] Test: Create network via fixture, verify moRef generated
  - [x] Test: State isolation between test classes (VcsimStateIsolationTest)
  - [x] Test: Connection properties correctly injected

- [x] **Task 6: Document VCSIM usage patterns** (AC: 1, 2, 3)
  - [x] Add KDoc documentation to `VcsimContainer` and `VcsimTestFixture`
  - [x] Document VCSIM environment variable configuration options
  - [x] Document scaling options (VCSIM_CLUSTER, VCSIM_HOST, VCSIM_VM counts)

## Dev Notes

- **Relevant architecture patterns:** Ports & Adapters with test double strategy; Testcontainers for realistic integration testing without infrastructure.
- **Source tree components to touch:**
  - `eaf/eaf-testing/build.gradle.kts` (add VCSIM container dependency)
  - `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/vcsim/VcsimContainer.kt` (new)
  - `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/vcsim/VcsimTestFixture.kt` (new)
  - `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/vcsim/VcsimExtension.kt` (new)
  - `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/vcsim/VcsimTest.kt` (annotation)
  - `eaf/eaf-testing/src/test/kotlin/de/acci/eaf/testing/vcsim/VcsimIntegrationTest.kt` (new)
- **Testing standards:** Use Testcontainers with singleton pattern; achieve ≥80% coverage and ≥70% mutation score.

### VCSIM Configuration Reference

```kotlin
// eaf-testing/src/main/kotlin/de/acci/eaf/testing/vcsim/VcsimContainer.kt
class VcsimContainer : GenericContainer<VcsimContainer>("vmware/vcsim:v0.47.0") {
    init {
        withExposedPorts(8989)
        withEnv("VCSIM_CLUSTER", "2")    // 2 clusters
        withEnv("VCSIM_HOST", "4")       // 4 hosts per cluster
        withEnv("VCSIM_VM", "10")        // 10 VMs per host (80 total)
        withEnv("VCSIM_POOL", "2")       // 2 resource pools
        withEnv("VCSIM_FOLDER", "3")     // 3 folders
        waitingFor(Wait.forHttp("/about").forPort(8989))
    }

    fun getSdkUrl(): String = "https://${host}:${getMappedPort(8989)}/sdk"
    fun getUsername(): String = "user"
    fun getPassword(): String = "pass"
}
```

### VCSIM Test Annotation Pattern

```kotlin
// eaf-testing/src/main/kotlin/de/acci/eaf/testing/vcsim/VcsimTest.kt
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(VcsimExtension::class)
@Testcontainers
annotation class VcsimTest
```

### References

- [Source: docs/epics.md#Story-1.10-VCSIM-Integration]
- [Source: docs/test-design-system.md#Section-7.3-VMware-vCenter-Simulator-VCSIM]
- [Source: docs/architecture.md#Integration-External-Systems]
- [Source: docs/sprint-artifacts/1-8-jooq-projection-base.md#Dev-Agent-Record]
- [VCSIM Repository: https://github.com/vmware/govmomi/blob/main/vcsim/README.md]
- [VCSIM Setup Guide: https://enterpriseadmins.org/blog/virtualization/scaling-your-tests-how-to-set-up-a-vcenter-server-simulator/]

## Dev Agent Record

### Context Reference

- `docs/sprint-artifacts/1-10-vcsim-integration.context.xml`

### Agent Model Used

claude-opus-4-5-20251101

### Debug Log References

- Wait strategy changed from `forHttp("/about")` to `forLogMessage(".*GOVC_URL.*")` due to HTTPS self-signed cert issues
- Switched from Ktor HTTP client to Java HttpClient for simpler TLS handling with insecure certificates

### Completion Notes List

- All 6 tasks completed successfully
- 63 tests passing (20 VcsimContainerTest, 22 VcsimTypesTest, 16 VcsimIntegrationTest, 5 VcsimStateIsolationTest)
- Coverage meets ≥80% threshold (koverVerify passed)
- VCSIM container starts and provides `/sdk` endpoint
- VcsimTestFixture provides all helper methods (createVm, createNetwork, createDatastore, simulateProvisioning, resetState)
- @VcsimTest annotation with parameter injection working
- State isolation via resetState() method between test classes
- Container reuse within test class via singleton pattern

### File List

**New Files:**
- `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/vcsim/VcsimContainer.kt`
- `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/vcsim/VcsimTypes.kt`
- `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/vcsim/VcsimTestFixture.kt`
- `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/vcsim/VcsimExtension.kt`
- `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/vcsim/VcsimTest.kt`
- `eaf/eaf-testing/src/test/kotlin/de/acci/eaf/testing/vcsim/VcsimContainerTest.kt`
- `eaf/eaf-testing/src/test/kotlin/de/acci/eaf/testing/vcsim/VcsimTypesTest.kt`
- `eaf/eaf-testing/src/test/kotlin/de/acci/eaf/testing/vcsim/VcsimIntegrationTest.kt`
- `eaf/eaf-testing/src/test/kotlin/de/acci/eaf/testing/vcsim/VcsimStateIsolationTest.kt`

**Modified Files:**
- `eaf/eaf-testing/src/main/kotlin/de/acci/eaf/testing/TestContainers.kt` (added vcsim singleton)
- `gradle/libs.versions.toml` (added ktor version - note: ktor deps removed, only version entry remains)

### Change Log

- 2025-11-27: Story drafted from epics.md, test-design-system.md, and architecture.md
- 2025-11-27: Story context generated, status changed to ready-for-dev
- 2025-11-27: Implementation complete - all tasks done, all tests passing, coverage ≥80%
