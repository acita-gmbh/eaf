package de.acci.eaf.eventsourcing

import de.acci.eaf.core.types.TenantId

/**
 * Validates that loaded events belong to the expected tenant.
 *
 * This is a defense-in-depth security measure. Even if PostgreSQL RLS
 * is properly configured, this validation ensures handlers cannot
 * accidentally process events from another tenant.
 *
 * ## Security
 *
 * Per OWASP guidelines, this function returns a simple boolean rather
 * than throwing an exception with details. Callers should return
 * `NotFound` (not `Forbidden`) to prevent tenant enumeration attacks.
 *
 * ## Usage
 *
 * ```kotlin
 * val storedEvents = eventStore.load(aggregateId)
 *
 * if (!storedEvents.belongsToTenant(command.tenantId)) {
 *     return NotFoundError(aggregateId).failure()
 * }
 * ```
 *
 * @param expectedTenantId The tenant ID from the command/request
 * @return true if events belong to the expected tenant, false otherwise
 */
public fun List<StoredEvent>.belongsToTenant(expectedTenantId: TenantId): Boolean {
    if (isEmpty()) {
        return true // No events = aggregate doesn't exist, handled by NotFound
    }

    // Defense-in-depth: ensure ALL loaded events belong to the expected tenant
    // This protects against potential bugs, corruption, or misconfigured RLS
    return all { it.metadata.tenantId == expectedTenantId }
}
