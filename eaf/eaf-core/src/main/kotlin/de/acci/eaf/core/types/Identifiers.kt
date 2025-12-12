package de.acci.eaf.core.types

import de.acci.eaf.core.error.InvalidIdentifierFormatException
import java.util.UUID

/**
 * Tenant identifier value object.
 * Purpose: strict typing for multi-tenant context propagation.
 */
@JvmInline
public value class TenantId(
    /** The raw UUID value */
    public val value: UUID
) {
    public companion object {
        /** Generate a new random tenant identifier. */
        public fun generate(): TenantId = TenantId(UUID.randomUUID())

        /** Parse from canonical UUID string. */
        public fun fromString(source: String): TenantId =
            try {
                TenantId(UUID.fromString(source))
            } catch (e: IllegalArgumentException) {
                throw InvalidIdentifierFormatException("TenantId", source, e)
            }
    }
}

/**
 * User identifier value object.
 * Purpose: avoid leaking raw UUID strings across layers.
 */
@JvmInline
public value class UserId(
    /** The raw UUID value */
    public val value: UUID
) {
    public companion object {
        /** Generate a new random user identifier. */
        public fun generate(): UserId = UserId(UUID.randomUUID())

        /** Parse from canonical UUID string. */
        public fun fromString(source: String): UserId =
            try {
                UserId(UUID.fromString(source))
            } catch (e: IllegalArgumentException) {
                throw InvalidIdentifierFormatException("UserId", source, e)
            }
    }
}

/**
 * Correlation identifier for tracing and audit.
 * Purpose: correlate logs/events without adding Throwable dependencies.
 */
@JvmInline
public value class CorrelationId(
    /** The raw UUID value */
    public val value: UUID
) {
    public companion object {
        /** Generate a new correlation ID. */
        public fun generate(): CorrelationId = CorrelationId(UUID.randomUUID())

        /** Parse from canonical UUID string. */
        public fun fromString(source: String): CorrelationId =
            try {
                CorrelationId(UUID.fromString(source))
            } catch (e: IllegalArgumentException) {
                throw InvalidIdentifierFormatException("CorrelationId", source, e)
            }
    }
}
