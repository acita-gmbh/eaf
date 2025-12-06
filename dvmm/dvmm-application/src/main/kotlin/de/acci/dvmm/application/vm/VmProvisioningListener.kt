package de.acci.dvmm.application.vm

import de.acci.dvmm.application.vmrequest.VmRequestEventDeserializer
import de.acci.dvmm.domain.vmrequest.VmRequestAggregate
import de.acci.dvmm.domain.vmrequest.events.VmRequestApproved
import de.acci.eaf.core.result.onFailure
import de.acci.eaf.core.result.onSuccess
import de.acci.eaf.eventsourcing.EventStore
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Listens for VmRequestApproved events and triggers VM provisioning.
 *
 * Acts as a Process Manager / Saga:
 * 1. Listen for VmRequestApproved
 * 2. Load VmRequestAggregate to get full spec (size, etc.)
 * 3. Dispatch ProvisionVmCommand
 */
public class VmProvisioningListener(
    private val eventStore: EventStore,
    private val deserializer: VmRequestEventDeserializer,
    private val provisionVmHandler: ProvisionVmHandler
) {
    private val logger = KotlinLogging.logger {}

    public suspend fun onVmRequestApproved(event: VmRequestApproved) {
        logger.info { "Received approval for request ${event.aggregateId.value}. Triggering provisioning..." }

        // Load aggregate to get full details (like size) which are not in the event
        val storedEvents = eventStore.load(event.aggregateId.value)
        
        if (storedEvents.isEmpty()) {
            logger.error { "Failed to load VM request aggregate ${event.aggregateId.value}: Aggregate not found" }
            return
        }

        val events = try {
            storedEvents.map { deserializer.deserialize(it) }
        } catch (e: Exception) {
             logger.error(e) { "Failed to deserialize events for request ${event.aggregateId.value}" }
             return
        }
        
        val aggregate = VmRequestAggregate.reconstitute(event.aggregateId, events)
        
        val command = ProvisionVmCommand(
            requestId = aggregate.id,
            tenantId = event.metadata.tenantId,
            projectId = aggregate.projectId,
            vmName = aggregate.vmName,
            size = aggregate.size,
            requesterId = aggregate.requesterId
        )

        provisionVmHandler.handle(command, event.metadata.correlationId)
            .onFailure { error ->
                 logger.error { "Failed to dispatch ProvisionVmCommand for request ${aggregate.id.value}: $error" }
            }
            .onSuccess { result ->
                logger.info { "Provisioning started for VM ${result.vmId.value} (Request: ${aggregate.id.value})" }
            }
    }
}