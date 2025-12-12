package de.acci.dcm.domain.vmware

import de.acci.eaf.core.error.InvalidIdentifierFormatException
import java.util.UUID

/**
 * Value object representing a unique identifier for a VMware configuration.
 *
 * VMware configuration IDs are UUIDs that uniquely identify a tenant's
 * vCenter connection configuration. Since there's only one configuration
 * per tenant, this ID is typically generated once during creation.
 *
 * @property value The underlying UUID value
 */
@JvmInline
public value class VmwareConfigurationId(public val value: UUID) {
    public companion object {
        /**
         * Generate a new random VMware configuration ID.
         */
        public fun generate(): VmwareConfigurationId = VmwareConfigurationId(UUID.randomUUID())

        /**
         * Create a VMware configuration ID from a string representation.
         *
         * @throws InvalidIdentifierFormatException if the string is not a valid UUID
         */
        public fun fromString(value: String): VmwareConfigurationId =
            try {
                VmwareConfigurationId(UUID.fromString(value))
            } catch (e: IllegalArgumentException) {
                throw InvalidIdentifierFormatException("VmwareConfigurationId", value, e)
            }
    }
}
