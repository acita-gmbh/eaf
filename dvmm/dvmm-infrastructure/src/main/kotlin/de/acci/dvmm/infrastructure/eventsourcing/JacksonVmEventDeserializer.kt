package de.acci.dvmm.infrastructure.eventsourcing

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import de.acci.dvmm.application.vm.VmEventDeserializer
import de.acci.dvmm.domain.vm.events.VmProvisioningFailed
import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.StoredEvent

/**
 * Jackson-based implementation of VmEventDeserializer.
 *
 * Deserializes stored events to their appropriate Vm domain event types
 * based on the eventType field in StoredEvent.
 *
 * Uses the EventStoreObjectMapper-configured ObjectMapper for proper
 * handling of EAF value classes (TenantId, UserId, CorrelationId, etc.).
 */
public class JacksonVmEventDeserializer(
    private val objectMapper: ObjectMapper
) : VmEventDeserializer {

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
            "VmProvisioningStarted" -> VmProvisioningStarted::class.java
            "VmProvisioningFailed" -> VmProvisioningFailed::class.java
            else -> throw IllegalArgumentException(
                "Unknown event type: $eventType. " +
                    "Add mapping to JacksonVmEventDeserializer."
            )
        }
    }
}
