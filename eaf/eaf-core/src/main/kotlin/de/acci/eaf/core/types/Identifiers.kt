package de.acci.eaf.core.types

import java.util.UUID

/**
 * Tenant identifier value object.
 * Purpose: strict typing for multi-tenant context propagation.
 */
@JvmInline
public value class TenantId(public val value: UUID) {
    public companion object {
        /** Generate a new random tenant identifier. */
        public fun generate(): TenantId = TenantId(UUID.randomUUID())

        /** Parse from canonical UUID string. */
        public fun fromString(source: String): TenantId = TenantId(UUID.fromString(source))
    }
}

/**
 * User identifier value object.
 * Purpose: avoid leaking raw UUID strings across layers.
 */
@JvmInline
public value class UserId(public val value: UUID) {
    public companion object {
        /** Generate a new random user identifier. */
        public fun generate(): UserId = UserId(UUID.randomUUID())

        /** Parse from canonical UUID string. */
        public fun fromString(source: String): UserId = UserId(UUID.fromString(source))
    }
}

/**
 * Correlation identifier for tracing and audit.
 * Purpose: correlate logs/events without adding Throwable dependencies.
 */
@JvmInline
public value class CorrelationId(public val value: UUID) {
    public companion object {
        /** Generate a new correlation ID. */
        public fun generate(): CorrelationId = CorrelationId(UUID.randomUUID())

        /** Parse from canonical UUID string. */
        public fun fromString(source: String): CorrelationId = CorrelationId(UUID.fromString(source))
    }
}
