package de.acci.eaf.testing.vcsim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for VCSIM type value objects.
 */
class VcsimTypesTest {

    // VmSpec tests

    @Test
    fun `VmSpec creates with valid name`() {
        val spec = VmSpec(name = "test-vm")
        assertEquals("test-vm", spec.name)
        assertEquals(VmSpec.DEFAULT_CPU_COUNT, spec.cpuCount)
        assertEquals(VmSpec.DEFAULT_MEMORY_MB, spec.memoryMb)
        assertEquals(VmSpec.DEFAULT_DISK_SIZE_GB, spec.diskSizeGb)
    }

    @Test
    fun `VmSpec creates with custom configuration`() {
        val spec = VmSpec(
            name = "custom-vm",
            cpuCount = 8,
            memoryMb = 16384,
            diskSizeGb = 100,
            guestId = "ubuntu64Guest"
        )
        assertEquals("custom-vm", spec.name)
        assertEquals(8, spec.cpuCount)
        assertEquals(16384, spec.memoryMb)
        assertEquals(100, spec.diskSizeGb)
        assertEquals("ubuntu64Guest", spec.guestId)
    }

    @Test
    fun `VmSpec rejects blank name`() {
        assertThrows<IllegalArgumentException> {
            VmSpec(name = "")
        }
    }

    @Test
    fun `VmSpec rejects whitespace-only name`() {
        assertThrows<IllegalArgumentException> {
            VmSpec(name = "   ")
        }
    }

    @Test
    fun `VmSpec rejects zero CPU count`() {
        assertThrows<IllegalArgumentException> {
            VmSpec(name = "test", cpuCount = 0)
        }
    }

    @Test
    fun `VmSpec rejects negative CPU count`() {
        assertThrows<IllegalArgumentException> {
            VmSpec(name = "test", cpuCount = -1)
        }
    }

    @Test
    fun `VmSpec rejects zero memory`() {
        assertThrows<IllegalArgumentException> {
            VmSpec(name = "test", memoryMb = 0)
        }
    }

    @Test
    fun `VmSpec rejects zero disk size`() {
        assertThrows<IllegalArgumentException> {
            VmSpec(name = "test", diskSizeGb = 0)
        }
    }

    // VmRef tests

    @Test
    fun `VmRef creates with valid values`() {
        val ref = VmRef(
            moRef = "vm-123",
            name = "my-vm",
            powerState = VmPowerState.POWERED_ON
        )
        assertEquals("vm-123", ref.moRef)
        assertEquals("my-vm", ref.name)
        assertEquals(VmPowerState.POWERED_ON, ref.powerState)
    }

    @Test
    fun `VmRef defaults to powered off`() {
        val ref = VmRef(moRef = "vm-123", name = "my-vm")
        assertEquals(VmPowerState.POWERED_OFF, ref.powerState)
    }

    @Test
    fun `VmRef rejects blank moRef`() {
        assertThrows<IllegalArgumentException> {
            VmRef(moRef = "", name = "test")
        }
    }

    @Test
    fun `VmRef rejects blank name`() {
        assertThrows<IllegalArgumentException> {
            VmRef(moRef = "vm-123", name = "")
        }
    }

    // NetworkRef tests

    @Test
    fun `NetworkRef creates with valid values`() {
        val ref = NetworkRef(moRef = "network-10", name = "VM Network")
        assertEquals("network-10", ref.moRef)
        assertEquals("VM Network", ref.name)
    }

    @Test
    fun `NetworkRef rejects blank moRef`() {
        assertThrows<IllegalArgumentException> {
            NetworkRef(moRef = "", name = "test")
        }
    }

    @Test
    fun `NetworkRef rejects blank name`() {
        assertThrows<IllegalArgumentException> {
            NetworkRef(moRef = "network-10", name = "")
        }
    }

    // DatastoreRef tests

    @Test
    fun `DatastoreRef creates with valid values`() {
        val ref = DatastoreRef(
            moRef = "datastore-15",
            name = "LocalStorage",
            capacityBytes = 1_000_000_000_000L, // 1TB
            freeSpaceBytes = 500_000_000_000L   // 500GB
        )
        assertEquals("datastore-15", ref.moRef)
        assertEquals("LocalStorage", ref.name)
        assertEquals(1_000_000_000_000L, ref.capacityBytes)
        assertEquals(500_000_000_000L, ref.freeSpaceBytes)
    }

    @Test
    fun `DatastoreRef defaults to zero capacity`() {
        val ref = DatastoreRef(moRef = "datastore-15", name = "test")
        assertEquals(0L, ref.capacityBytes)
        assertEquals(0L, ref.freeSpaceBytes)
    }

    @Test
    fun `DatastoreRef rejects blank moRef`() {
        assertThrows<IllegalArgumentException> {
            DatastoreRef(moRef = "", name = "test")
        }
    }

    @Test
    fun `DatastoreRef rejects blank name`() {
        assertThrows<IllegalArgumentException> {
            DatastoreRef(moRef = "datastore-15", name = "")
        }
    }

    @Test
    fun `DatastoreRef rejects negative capacity`() {
        assertThrows<IllegalArgumentException> {
            DatastoreRef(moRef = "datastore-15", name = "test", capacityBytes = -1)
        }
    }

    @Test
    fun `DatastoreRef rejects negative free space`() {
        assertThrows<IllegalArgumentException> {
            DatastoreRef(moRef = "datastore-15", name = "test", freeSpaceBytes = -1)
        }
    }

    // VmPowerState tests

    @Test
    fun `VmPowerState has expected values`() {
        val states = VmPowerState.entries
        assertEquals(3, states.size)
        assertEquals(VmPowerState.POWERED_ON, VmPowerState.valueOf("POWERED_ON"))
        assertEquals(VmPowerState.POWERED_OFF, VmPowerState.valueOf("POWERED_OFF"))
        assertEquals(VmPowerState.SUSPENDED, VmPowerState.valueOf("SUSPENDED"))
    }
}
