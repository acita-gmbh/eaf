package de.acci.dvmm.domain.vm

import de.acci.eaf.core.error.InvalidIdentifierFormatException
import java.util.UUID

/**
 * Unique identifier for a Virtual Machine.
 */
@JvmInline
public value class VmId(public val value: UUID) {
    public companion object {
        public fun generate(): VmId = VmId(UUID.randomUUID())

        public fun fromString(source: String): VmId =
            try {
                VmId(UUID.fromString(source))
            } catch (e: IllegalArgumentException) {
                throw InvalidIdentifierFormatException("VmId", source, e)
            }
    }
}
