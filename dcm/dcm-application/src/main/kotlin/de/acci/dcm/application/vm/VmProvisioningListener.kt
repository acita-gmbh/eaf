package de.acci.dcm.application.vm

import de.acci.dcm.application.vmrequest.VmRequestEventDeserializer
import de.acci.dcm.domain.vmrequest.VmRequestAggregate
import de.acci.dcm.domain.vmrequest.VmRequestStatus
import de.acci.dcm.domain.vmrequest.events.VmRequestApproved
import de.acci.eaf.core.result.onFailure
import de.acci.eaf.core.result.onSuccess
import de.acci.eaf.eventsourcing.EventStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.coroutines.cancellation.CancellationException

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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
             logger.error(e) { "Failed to deserialize events for request ${event.aggregateId.value}" }
             return
        }
        
        val aggregate = VmRequestAggregate.reconstitute(event.aggregateId, events)

        // Idempotency check: Only provision if status is exactly APPROVED
        // If status has progressed beyond APPROVED (e.g., PROVISIONING), another process already handled this
        if (aggregate.status != VmRequestStatus.APPROVED) {
            logger.info {
                "Skipping provisioning for request ${event.aggregateId.value}: " +
                    "status is ${aggregate.status}, expected APPROVED (idempotency check)"
            }
            return
        }

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