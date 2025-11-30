package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.VmRequestAggregate
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.eventsourcing.EventStoreError

/**
 * Errors that can occur when creating a VM request.
 */
public sealed class CreateVmRequestError {
    /**
     * Request violates project quota limits.
     */
    public data class QuotaExceeded(
        val available: Int,
        val requested: Int,
        val message: String = "Project quota exceeded. Available: $available VMs"
    ) : CreateVmRequestError()

    /**
     * Concurrent modification detected.
     */
    public data class ConcurrencyConflict(
        val message: String
    ) : CreateVmRequestError()

    /**
     * Unexpected persistence failure.
     */
    public data class PersistenceFailure(
        val message: String
    ) : CreateVmRequestError()
}

/**
 * Result of successfully creating a VM request.
 */
public data class CreateVmRequestResult(
    val requestId: VmRequestId
)

/**
 * Handler for CreateVmRequestCommand.
 *
 * Creates a new VmRequestAggregate and persists the resulting events
 * to the event store. Performs quota validation before creating the request.
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = CreateVmRequestHandler(eventStore, quotaService)
 * val result = handler.handle(command)
 * result.fold(
 *     onSuccess = { println("Created request: ${it.requestId}") },
 *     onFailure = { println("Failed: ${it}") }
 * )
 * ```
 */
public class CreateVmRequestHandler(
    private val eventStore: EventStore,
    private val quotaChecker: QuotaChecker = AlwaysAvailableQuotaChecker
) {

    /**
     * Handle the create VM request command.
     *
     * @param command The command to process
     * @param correlationId Optional correlation ID for distributed tracing
     * @return Result containing the created request ID or an error
     */
    public suspend fun handle(
        command: CreateVmRequestCommand,
        correlationId: CorrelationId = CorrelationId.generate()
    ): Result<CreateVmRequestResult, CreateVmRequestError> {
        // Check quota (stubbed for now, full implementation in Epic 4)
        val quotaResult = quotaChecker.checkQuota(
            tenantId = command.tenantId,
            projectId = command.projectId
        )
        if (quotaResult is Result.Failure) {
            return quotaResult
        }

        // Create event metadata
        val metadata = EventMetadata.create(
            tenantId = command.tenantId,
            userId = command.requesterId,
            correlationId = correlationId
        )

        // Create the aggregate
        val aggregate = VmRequestAggregate.create(
            requesterId = command.requesterId,
            projectId = command.projectId,
            vmName = command.vmName,
            size = command.size,
            justification = command.justification,
            metadata = metadata
        )

        // Persist events to the event store
        val appendResult = eventStore.append(
            aggregateId = aggregate.id.value,
            events = aggregate.uncommittedEvents,
            expectedVersion = 0 // New aggregate starts at version 0
        )

        return when (appendResult) {
            is Result.Success -> {
                aggregate.clearUncommittedEvents()
                CreateVmRequestResult(requestId = aggregate.id).success()
            }
            is Result.Failure -> when (val error = appendResult.error) {
                is EventStoreError.ConcurrencyConflict -> {
                    CreateVmRequestError.ConcurrencyConflict(
                        message = "Concurrent modification detected for aggregate ${error.aggregateId}"
                    ).failure()
                }
            }
        }
    }
}

/**
 * Interface for quota checking.
 *
 * Implementations validate that the requested VM does not exceed
 * project quota limits.
 */
public interface QuotaChecker {
    /**
     * Check if the project has available quota for a new VM request.
     *
     * @return Success if quota available, Failure with QuotaExceeded otherwise
     */
    public suspend fun checkQuota(
        tenantId: de.acci.eaf.core.types.TenantId,
        projectId: de.acci.dvmm.domain.vmrequest.ProjectId
    ): Result<Unit, CreateVmRequestError.QuotaExceeded>
}

/**
 * Stub quota checker that always returns available.
 * Used until Epic 4 implements real quota enforcement.
 */
public object AlwaysAvailableQuotaChecker : QuotaChecker {
    override suspend fun checkQuota(
        tenantId: de.acci.eaf.core.types.TenantId,
        projectId: de.acci.dvmm.domain.vmrequest.ProjectId
    ): Result<Unit, CreateVmRequestError.QuotaExceeded> = Unit.success()
}
