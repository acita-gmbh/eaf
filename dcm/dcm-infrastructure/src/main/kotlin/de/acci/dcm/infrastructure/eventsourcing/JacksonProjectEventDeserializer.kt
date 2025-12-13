package de.acci.dcm.infrastructure.eventsourcing

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import de.acci.dcm.application.project.ProjectEventDeserializer
import de.acci.dcm.domain.project.events.ProjectArchived
import de.acci.dcm.domain.project.events.ProjectCreated
import de.acci.dcm.domain.project.events.ProjectUnarchived
import de.acci.dcm.domain.project.events.ProjectUpdated
import de.acci.dcm.domain.project.events.UserAssignedToProject
import de.acci.dcm.domain.project.events.UserRemovedFromProject
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.StoredEvent

/**
 * Jackson-based implementation of ProjectEventDeserializer.
 *
 * Deserializes stored events to their appropriate domain event types
 * based on the eventType field in StoredEvent.
 *
 * Uses the EventStoreObjectMapper-configured ObjectMapper for proper
 * handling of EAF value classes (TenantId, UserId, CorrelationId, etc.).
 */
public class JacksonProjectEventDeserializer(
    private val objectMapper: ObjectMapper
) : ProjectEventDeserializer {

    override fun deserialize(storedEvent: StoredEvent): DomainEvent {
        val eventClass = resolveEventClass(storedEvent.eventType)
        return try {
            objectMapper.readValue(storedEvent.payload, eventClass)
        } catch (e: JsonProcessingException) {
            throw IllegalStateException(
                "Failed to deserialize ${storedEvent.eventType} for aggregate ${storedEvent.aggregateId}: ${e.message}",
                e
            )
        }
    }

    private fun resolveEventClass(eventType: String): Class<out DomainEvent> {
        return when (eventType) {
            "ProjectCreated" -> ProjectCreated::class.java
            "ProjectUpdated" -> ProjectUpdated::class.java
            "ProjectArchived" -> ProjectArchived::class.java
            "ProjectUnarchived" -> ProjectUnarchived::class.java
            "UserAssignedToProject" -> UserAssignedToProject::class.java
            "UserRemovedFromProject" -> UserRemovedFromProject::class.java
            else -> throw IllegalArgumentException(
                "Unknown event type: $eventType. " +
                    "Add mapping to JacksonProjectEventDeserializer."
            )
        }
    }
}
