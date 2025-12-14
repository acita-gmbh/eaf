package de.acci.dcm.application.project

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.eventsourcing.StoredEvent

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
 * @param events The events loaded from the event store
 * @param expectedTenantId The tenant ID from the command/request
 * @return true if events belong to the expected tenant, false otherwise
 */
internal fun validateTenantOwnership(events: List<StoredEvent>, expectedTenantId: TenantId): Boolean {
    if (events.isEmpty()) {
        return true // No events = aggregate doesn't exist, handled by NotFound
    }

    // Check first event's tenant - all events for an aggregate share the same tenant
    val eventTenantId = events.first().metadata.tenantId
    return eventTenantId == expectedTenantId
}
