package de.acci.dvmm.infrastructure.eventsourcing

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import de.acci.dvmm.application.vmrequest.VmRequestEventDeserializer
import de.acci.dvmm.domain.vmrequest.events.VmRequestApproved
import de.acci.dvmm.domain.vmrequest.events.VmRequestCancelled
import de.acci.dvmm.domain.vmrequest.events.VmRequestCreated
import de.acci.dvmm.domain.vmrequest.events.VmRequestProvisioningStarted
import de.acci.dvmm.domain.vmrequest.events.VmRequestReady
import de.acci.dvmm.domain.vmrequest.events.VmRequestRejected
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
            "VmRequestCreated" -> VmRequestCreated::class.java
            "VmRequestCancelled" -> VmRequestCancelled::class.java
            "VmRequestApproved" -> VmRequestApproved::class.java
            "VmRequestRejected" -> VmRequestRejected::class.java
            "VmRequestProvisioningStarted" -> VmRequestProvisioningStarted::class.java
            "VmRequestReady" -> VmRequestReady::class.java
            else -> throw IllegalArgumentException(
                "Unknown event type: $eventType. " +
                    "Add mapping to JacksonVmRequestEventDeserializer."
            )
        }
    }
}
