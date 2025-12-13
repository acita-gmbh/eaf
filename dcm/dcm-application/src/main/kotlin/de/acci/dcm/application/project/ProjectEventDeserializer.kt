package de.acci.dcm.application.project

import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.StoredEvent

/**
 * Interface for deserializing Project domain events from stored events.
 *
 * This abstraction allows the application layer to remain independent of the
 * specific serialization mechanism (Jackson, Kotlinx, etc.) used in the
 * infrastructure layer.
 *
 * Implementations must be able to deserialize:
 * - ProjectCreated
 * - ProjectUpdated
 * - ProjectArchived
 * - ProjectUnarchived
 * - UserAssignedToProject
 * - UserRemovedFromProject
 */
public interface ProjectEventDeserializer {
    /**
     * Deserialize a stored event to its domain event representation.
     *
     * @param storedEvent The stored event containing JSON payload and metadata
     * @return The deserialized domain event
     * @throws IllegalArgumentException if the event type is unknown
     * @throws IllegalStateException if deserialization fails
     */
    public fun deserialize(storedEvent: StoredEvent): DomainEvent
}
