package de.acci.dvmm.application.vm

import de.acci.dvmm.domain.vm.VmAggregate
import de.acci.dvmm.domain.vm.VmId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.EventStore
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Errors that can occur during VM provisioning command handling.
 */
public sealed class ProvisionVmError {
    public data class PersistenceFailure(val message: String) : ProvisionVmError()
}

/**
 * Result of handling ProvisionVmCommand.
 */
public data class ProvisionVmResult(val vmId: VmId)

/**
 * Handles the command to provision a VM.
 * Creates the VmAggregate and persists the VmProvisioningStarted event.
 */
public class ProvisionVmHandler(
    private val eventStore: EventStore
) {
    private val logger = KotlinLogging.logger {}

    public suspend fun handle(
        command: ProvisionVmCommand,
        correlationId: CorrelationId = CorrelationId.generate()
    ): Result<ProvisionVmResult, ProvisionVmError> {
        
        val metadata = EventMetadata.create(
            tenantId = command.tenantId,
            userId = command.requesterId,
            correlationId = correlationId
        )

        val aggregate = VmAggregate.startProvisioning(
            requestId = command.requestId,
            projectId = command.projectId,
            vmName = command.vmName,
            size = command.size,
            requesterId = command.requesterId,
            metadata = metadata
        )

        val appendResult = try {
            eventStore.append(
                aggregateId = aggregate.id.value,
                events = aggregate.uncommittedEvents,
                expectedVersion = 0 // New aggregate
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to persist new VmAggregate for request ${command.requestId.value}" }
            return ProvisionVmError.PersistenceFailure(e.message ?: "Unknown error").failure()
        }

        return when (appendResult) {
            is Result.Success -> {
                aggregate.clearUncommittedEvents()
                ProvisionVmResult(aggregate.id).success()
            }
            is Result.Failure -> {
                logger.error { "Failed to append events for request ${command.requestId.value}: ${appendResult.error}" }
                ProvisionVmError.PersistenceFailure("Failed to append events: ${appendResult.error}").failure()
            }
        }
    }
}
