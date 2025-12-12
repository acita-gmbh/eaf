package de.acci.dcm.domain.vmrequest

import de.acci.eaf.core.error.InvalidIdentifierFormatException
import java.util.UUID

/**
 * Unique identifier for a project.
 * Projects are containers for VM requests and resource quotas.
 */
@JvmInline
public value class ProjectId(public val value: UUID) {
    public companion object {
        /** Generate a new random project identifier. */
        public fun generate(): ProjectId = ProjectId(UUID.randomUUID())

        /** Parse from canonical UUID string. */
        public fun fromString(source: String): ProjectId =
            try {
                ProjectId(UUID.fromString(source))
            } catch (e: IllegalArgumentException) {
                throw InvalidIdentifierFormatException("ProjectId", source, e)
            }
    }
}
