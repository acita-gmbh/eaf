package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.VmRequestAggregate
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.onFailure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.eventsourcing.EventStoreError
import io.github.oshai.kotlinlogging.KotlinLogging

public sealed class MarkVmRequestProvisioningError {
    public data class NotFound(val message: String) : MarkVmRequestProvisioningError()
    public data class InvalidState(val message: String) : MarkVmRequestProvisioningError()
    public data class ConcurrencyConflict(val message: String) : MarkVmRequestProvisioningError()
    public data class PersistenceFailure(val message: String) : MarkVmRequestProvisioningError()
}

public class MarkVmRequestProvisioningHandler(
    private val eventStore: EventStore,
    private val deserializer: VmRequestEventDeserializer
) {
    private val logger = KotlinLogging.logger {}

    public suspend fun handle(
        command: MarkVmRequestProvisioningCommand,
        correlationId: CorrelationId = CorrelationId.generate()
    ): Result<Unit, MarkVmRequestProvisioningError> {
        
        val storedEvents = eventStore.load(command.requestId.value)
        if (storedEvents.isEmpty()) {
            return MarkVmRequestProvisioningError.NotFound("VM request ${command.requestId.value} not found").failure()
        }

        val events = try {
            storedEvents.map { deserializer.deserialize(it) }
        } catch (e: Exception) {
            return MarkVmRequestProvisioningError.PersistenceFailure("Failed to deserialize events: ${e.message}").failure()
        }

        val aggregate = VmRequestAggregate.reconstitute(command.requestId, events)

        if (aggregate.tenantId != command.tenantId) {
             return MarkVmRequestProvisioningError.NotFound("VM request not found (tenant mismatch)").failure()
        }

        val metadata = EventMetadata.create(
            tenantId = command.tenantId,
            userId = command.userId,
            correlationId = correlationId
        )
        
        try {
            aggregate.markProvisioning(metadata)
        } catch (e: Exception) {
             return MarkVmRequestProvisioningError.InvalidState(e.message ?: "Invalid state transition").failure()
        }

        val expectedVersion = aggregate.version - aggregate.uncommittedEvents.size
        val appendResult = try {
            eventStore.append(
                aggregateId = aggregate.id.value,
                events = aggregate.uncommittedEvents,
                expectedVersion = expectedVersion
            )
        } catch (e: Exception) {
            return MarkVmRequestProvisioningError.PersistenceFailure("Failed to persist: ${e.message}").failure()
        }

        return when (appendResult) {
            is Result.Success -> Unit.success()
            is Result.Failure -> {
                if (appendResult.error is EventStoreError.ConcurrencyConflict) {
                    MarkVmRequestProvisioningError.ConcurrencyConflict("Concurrent modification").failure()
                } else {
                    MarkVmRequestProvisioningError.PersistenceFailure("Store error: ${appendResult.error}").failure()
                }
            }
        }
    }
}
