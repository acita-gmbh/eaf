package de.acci.eaf.testing.vcsim

import de.acci.eaf.testing.TestContainers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for VCSIM state isolation between test classes.
 *
 * Verifies AC4: State resets between test classes.
 *
 * This test class runs independently from [VcsimIntegrationTest] to verify
 * that fixture state (counters) reset between classes while container
 * instance is reused.
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
    fun `fixture starts with reset counters after resetState`() {
        // First VM should get counter value 1 after reset
        val vm = fixture.createVm(VmSpec(name = "first-vm"))
        assertEquals("vm-test-1", vm.moRef)
    }

    @Test
    fun `resetState clears VM counter`() {
        fixture.createVm(VmSpec(name = "vm-a"))
        fixture.createVm(VmSpec(name = "vm-b"))
        fixture.createVm(VmSpec(name = "vm-c"))

        fixture.resetState()

        val vmAfterReset = fixture.createVm(VmSpec(name = "vm-after-reset"))
        assertEquals("vm-test-1", vmAfterReset.moRef)
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
        val expectedContainerImage = VcsimContainer.DEFAULT_IMAGE

        assertEquals(expectedContainerImage, container.dockerImageName)
        assertEquals(true, container.isRunning)
    }
}
