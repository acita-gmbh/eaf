package de.acci.dvmm.domain.vm

/**
 * Value object representing a VMware VM identifier (MoRef).
 *
 * This is the identifier assigned by VMware vCenter to uniquely identify
 * a virtual machine within the vCenter instance. Format is typically "vm-12345".
 */
@JvmInline
public value class VmwareVmId private constructor(public val value: String) {

    init {
        require(value.isNotBlank()) { "VMware VM ID must not be blank" }
    }

    public companion object {
        /**
         * Creates a VmwareVmId from a string value.
         */
        public fun of(value: String): VmwareVmId = VmwareVmId(value)
    }

    override fun toString(): String = value
}
