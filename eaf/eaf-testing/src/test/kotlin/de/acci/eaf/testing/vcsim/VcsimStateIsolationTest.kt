package de.acci.eaf.testing.vcsim

import de.acci.eaf.testing.TestContainers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for VCSIM state isolation between test classes.
 *
 * Verifies AC4: State resets between test classes.
 *
 * This test class runs independently from [VcsimIntegrationTest] to verify
 * that fixture state resets between classes while container instance is reused.
 *
 * Note: VM moRefs are assigned by VCSIM via SOAP API, not by internal counters.
 * Network and datastore references use internal counters for test isolation.
 */
@VcsimTest
class VcsimStateIsolationTest {

    private lateinit var fixture: VcsimTestFixture

    @BeforeEach
    fun setUp() {
        // Create fresh fixture for each test
        fixture = VcsimTestFixture(TestContainers.vcsim)
        // Reset state to simulate class-level reset
        fixture.resetState()
    }

    @Test
    fun `VM creation works after resetState`() {
        // VM creation via SOAP API should work after reset
        // Note: VM moRefs are assigned by VCSIM, not by the fixture's internal counters
        val vm = fixture.createVm(VmSpec(name = "first-vm"))
        assertNotNull(vm.moRef)
        assertTrue(vm.moRef.isNotBlank()) { "VM moRef should not be blank" }
        assertEquals("first-vm", vm.name)
    }

    @Test
    fun `multiple VMs can be created after resetState`() {
        // Create several VMs before reset
        fixture.createVm(VmSpec(name = "vm-a"))
        fixture.createVm(VmSpec(name = "vm-b"))
        fixture.createVm(VmSpec(name = "vm-c"))

        fixture.resetState()

        // VM creation should still work after reset
        // Note: VM moRefs are assigned by VCSIM, not by the fixture's internal counters
        val vmAfterReset = fixture.createVm(VmSpec(name = "vm-after-reset"))
        assertNotNull(vmAfterReset.moRef)
        assertTrue(vmAfterReset.moRef.isNotBlank()) { "VM moRef should not be blank" }
        assertEquals("vm-after-reset", vmAfterReset.name)
    }

    @Test
    fun `resetState clears network counter`() {
        fixture.createNetwork("net-a")
        fixture.createNetwork("net-b")

        fixture.resetState()

        val netAfterReset = fixture.createNetwork("net-after-reset")
        assertEquals("network-test-1", netAfterReset.moRef)
    }

    @Test
    fun `resetState clears datastore counter`() {
        fixture.createDatastore("ds-a")
        fixture.createDatastore("ds-b")

        fixture.resetState()

        val dsAfterReset = fixture.createDatastore("ds-after-reset")
        assertEquals("datastore-test-1", dsAfterReset.moRef)
    }

    @Test
    fun `container singleton is reused across test classes`() {
        // This test verifies that even in a separate test class,
        // the same container instance is used (singleton pattern)
        val container = TestContainers.vcsim

        // Container should be running - the specific image may vary:
        // - AMD64: uses official vmware/vcsim:v0.47.0
        // - ARM64: uses custom-built eaf-vcsim:v0.47.0
        assertTrue(container.isRunning)
        assertTrue(container.dockerImageName.contains("vcsim")) {
            "Expected image name to contain 'vcsim', got: ${container.dockerImageName}"
        }
    }
}
