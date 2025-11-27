package de.acci.eaf.testing.vcsim

/**
 * Specification for creating a virtual machine in VCSIM.
 *
 * @property name Display name for the VM
 * @property cpuCount Number of virtual CPUs (default: 2)
 * @property memoryMb Memory in megabytes (default: 4096)
 * @property diskSizeGb Disk size in gigabytes (default: 40)
 * @property guestId Guest OS identifier (default: "otherLinux64Guest")
 */
public data class VmSpec(
    val name: String,
    val cpuCount: Int = DEFAULT_CPU_COUNT,
    val memoryMb: Int = DEFAULT_MEMORY_MB,
    val diskSizeGb: Int = DEFAULT_DISK_SIZE_GB,
    val guestId: String = DEFAULT_GUEST_ID
) {
    init {
        require(name.isNotBlank()) { "VM name must not be blank" }
        require(cpuCount > 0) { "CPU count must be positive, got $cpuCount" }
        require(memoryMb > 0) { "Memory must be positive, got $memoryMb MB" }
        require(diskSizeGb > 0) { "Disk size must be positive, got $diskSizeGb GB" }
    }

    public companion object {
        /** Default number of virtual CPUs */
        public const val DEFAULT_CPU_COUNT: Int = 2

        /** Default memory in megabytes */
        public const val DEFAULT_MEMORY_MB: Int = 4096

        /** Default disk size in gigabytes */
        public const val DEFAULT_DISK_SIZE_GB: Int = 40

        /** Default guest OS identifier */
        public const val DEFAULT_GUEST_ID: String = "otherLinux64Guest"
    }
}

/**
 * Reference to a virtual machine in VCSIM inventory.
 *
 * @property moRef Managed Object Reference (e.g., "vm-123")
 * @property name Display name of the VM
 * @property powerState Current power state
 */
public data class VmRef(
    val moRef: String,
    val name: String,
    val powerState: VmPowerState = VmPowerState.POWERED_OFF
) {
    init {
        require(moRef.isNotBlank()) { "VM moRef must not be blank" }
        require(name.isNotBlank()) { "VM name must not be blank" }
    }
}

/**
 * Power state of a virtual machine.
 */
public enum class VmPowerState {
    POWERED_ON,
    POWERED_OFF,
    SUSPENDED
}

/**
 * Reference to a network in VCSIM inventory.
 *
 * @property moRef Managed Object Reference (e.g., "network-10")
 * @property name Display name of the network
 */
public data class NetworkRef(
    val moRef: String,
    val name: String
) {
    init {
        require(moRef.isNotBlank()) { "Network moRef must not be blank" }
        require(name.isNotBlank()) { "Network name must not be blank" }
    }
}

/**
 * Reference to a datastore in VCSIM inventory.
 *
 * @property moRef Managed Object Reference (e.g., "datastore-15")
 * @property name Display name of the datastore
 * @property capacityBytes Total capacity in bytes
 * @property freeSpaceBytes Available free space in bytes
 */
public data class DatastoreRef(
    val moRef: String,
    val name: String,
    val capacityBytes: Long = 0L,
    val freeSpaceBytes: Long = 0L
) {
    init {
        require(moRef.isNotBlank()) { "Datastore moRef must not be blank" }
        require(name.isNotBlank()) { "Datastore name must not be blank" }
        require(capacityBytes >= 0) { "Capacity must be non-negative, got $capacityBytes" }
        require(freeSpaceBytes >= 0) { "Free space must be non-negative, got $freeSpaceBytes" }
    }
}
