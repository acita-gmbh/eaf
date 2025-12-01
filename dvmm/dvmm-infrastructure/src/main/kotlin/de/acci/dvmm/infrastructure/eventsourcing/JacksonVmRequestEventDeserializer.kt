package de.acci.dvmm.infrastructure.eventsourcing

import com.fasterxml.jackson.databind.ObjectMapper
import de.acci.dvmm.application.vmrequest.VmRequestEventDeserializer
import de.acci.dvmm.domain.vmrequest.events.VmRequestCancelled
import de.acci.dvmm.domain.vmrequest.events.VmRequestCreated
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.StoredEvent

/**
 * Jackson-based implementation of VmRequestEventDeserializer.
 *
 * Deserializes stored events to their appropriate domain event types
 * based on the eventType field in StoredEvent.
 *
 * Uses the EventStoreObjectMapper-configured ObjectMapper for proper
 * handling of EAF value classes (TenantId, UserId, CorrelationId, etc.).
 */
public class JacksonVmRequestEventDeserializer(
    private val objectMapper: ObjectMapper
) : VmRequestEventDeserializer {

    override fun deserialize(storedEvent: StoredEvent): DomainEvent {
        val eventClass = resolveEventClass(storedEvent.eventType)
        return objectMapper.readValue(storedEvent.payload, eventClass)
    }

    private fun resolveEventClass(eventType: String): Class<out DomainEvent> {
        return when (eventType) {
            "VmRequestCreated" -> VmRequestCreated::class.java
            "VmRequestCancelled" -> VmRequestCancelled::class.java
            // Future event types will be added here:
            // "VmRequestApproved" -> VmRequestApproved::class.java
            // "VmRequestRejected" -> VmRequestRejected::class.java
            else -> throw IllegalArgumentException(
                "Unknown event type: $eventType. " +
                    "Add mapping to JacksonVmRequestEventDeserializer."
            )
        }
    }
}
