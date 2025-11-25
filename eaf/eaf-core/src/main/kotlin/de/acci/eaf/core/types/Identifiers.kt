package de.acci.eaf.core.types

import java.util.UUID

/**
 * Tenant identifier value object.
 */
@JvmInline
public value class TenantId(public val value: UUID) {
    public companion object {
        public fun generate(): TenantId = TenantId(UUID.randomUUID())
        public fun fromString(source: String): TenantId = TenantId(UUID.fromString(source))
    }
}

/**
 * User identifier value object.
 */
@JvmInline
public value class UserId(public val value: UUID) {
    public companion object {
        public fun generate(): UserId = UserId(UUID.randomUUID())
        public fun fromString(source: String): UserId = UserId(UUID.fromString(source))
    }
}

/**
 * Correlation identifier for tracing and audit.
 */
@JvmInline
public value class CorrelationId(public val value: UUID) {
    public companion object {
        public fun generate(): CorrelationId = CorrelationId(UUID.randomUUID())
        public fun fromString(source: String): CorrelationId = CorrelationId(UUID.fromString(source))
    }
}
