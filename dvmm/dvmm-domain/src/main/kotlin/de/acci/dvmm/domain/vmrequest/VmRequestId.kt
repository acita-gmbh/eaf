package de.acci.dvmm.domain.vmrequest

import de.acci.eaf.core.error.InvalidIdentifierFormatException
import java.util.UUID

/**
 * Unique identifier for a VM request.
 * Provides type safety for VM request references across the domain.
 */
@JvmInline
public value class VmRequestId(
    /** The raw UUID value */
    public val value: UUID
) {
    public companion object {
        /** Generate a new random VM request identifier. */
        public fun generate(): VmRequestId = VmRequestId(UUID.randomUUID())

        /** Parse from canonical UUID string. */
        public fun fromString(source: String): VmRequestId =
            try {
                VmRequestId(UUID.fromString(source))
            } catch (e: IllegalArgumentException) {
                throw InvalidIdentifierFormatException("VmRequestId", source, e)
            }
    }
}
