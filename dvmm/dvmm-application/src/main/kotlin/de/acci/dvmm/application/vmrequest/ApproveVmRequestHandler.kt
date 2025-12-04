package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.exceptions.InvalidStateException
import de.acci.dvmm.domain.exceptions.SelfApprovalException
import de.acci.dvmm.domain.vmrequest.VmRequestAggregate
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmRequestStatus
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.onFailure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.eventsourcing.EventStoreError
import de.acci.eaf.eventsourcing.projection.ProjectionError
import de.acci.eaf.notifications.EmailAddress
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

/**
 * Errors that can occur when approving a VM request.
 */
public sealed class ApproveVmRequestError {
    /**
     * Request not found in the event store.
     */
    public data class NotFound(
        val requestId: VmRequestId,
        val message: String = "VM request not found: ${requestId.value}"
    ) : ApproveVmRequestError()

    /**
     * Admin is not authorized to approve this request.
     * Occurs when admin tries to approve their own request (separation of duties).
     */
    public data class Forbidden(
        val message: String = "Cannot approve own request: separation of duties requires a different admin"
    ) : ApproveVmRequestError()

    /**
     * Request is not in an approvable state.
     * Only PENDING requests can be approved.
     */
    public data class InvalidState(
        val currentState: String,
        val message: String = "Cannot approve request in state: $currentState"
    ) : ApproveVmRequestError()

    /**
     * Concurrent modification detected (optimistic locking failure).
     */
    public data class ConcurrencyConflict(
        val message: String
    ) : ApproveVmRequestError()

    /**
     * Unexpected persistence failure.
     */
    public data class PersistenceFailure(
        val message: String
    ) : ApproveVmRequestError()
}

/**
 * Result of successfully approving a VM request.
 */
public data class ApproveVmRequestResult(
    val requestId: VmRequestId
)

/**
 * Handler for ApproveVmRequestCommand.
 *
 * Loads the VmRequestAggregate from the event store, verifies admin is not
 * the requester (separation of duties), performs the approval, and persists
 * the resulting events.
 *
 * ## Authorization
 *
 * Only admins can approve requests, and they cannot approve their own requests.
 * Attempting to approve your own request returns Forbidden error.
 *
 * ## Optimistic Locking
 *
 * The command includes the expected version. If another admin modified the
 * request concurrently, a ConcurrencyConflict error is returned.
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = ApproveVmRequestHandler(eventStore, eventDeserializer)
 * val result = handler.handle(command)
 * result.fold(
 *     onSuccess = { println("Approved request: ${it.requestId}") },
 *     onFailure = { println("Failed: $it") }
 * )
 * ```
 */
public class ApproveVmRequestHandler(
    private val eventStore: EventStore,
    private val eventDeserializer: VmRequestEventDeserializer,
    private val projectionUpdater: VmRequestProjectionUpdater = NoOpVmRequestProjectionUpdater,
    private val timelineUpdater: TimelineEventProjectionUpdater = NoOpTimelineEventProjectionUpdater,
    private val notificationSender: VmRequestNotificationSender = NoOpVmRequestNotificationSender
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the approve VM request command.
     *
     * @param command The command to process
     * @param correlationId Optional correlation ID for distributed tracing
     * @return Result containing the approved request ID or an error
     */
    public suspend fun handle(
        command: ApproveVmRequestCommand,
        correlationId: CorrelationId = CorrelationId.generate()
    ): Result<ApproveVmRequestResult, ApproveVmRequestError> {
        // Load events from event store
        val storedEvents = try {
            eventStore.load(command.requestId.value)
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to load events for request: " +
                    "requestId=${command.requestId.value}, " +
                    "tenantId=${command.tenantId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return ApproveVmRequestError.PersistenceFailure(
                message = "Failed to load request: ${e.message}"
            ).failure()
        }

        // Check if aggregate exists
        if (storedEvents.isEmpty()) {
            return ApproveVmRequestError.NotFound(
                requestId = command.requestId
            ).failure()
        }

        // Deserialize events and reconstitute aggregate
        val domainEvents = try {
            storedEvents.map { eventDeserializer.deserialize(it) }
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to deserialize events for request: " +
                    "requestId=${command.requestId.value}, " +
                    "tenantId=${command.tenantId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return ApproveVmRequestError.PersistenceFailure(
                message = "Failed to deserialize request events: ${e.message}"
            ).failure()
        }
        val aggregate = VmRequestAggregate.reconstitute(command.requestId, domainEvents)

        // Verify expected version matches (optimistic locking)
        if (aggregate.version != command.version) {
            return ApproveVmRequestError.ConcurrencyConflict(
                message = "Version mismatch: expected ${command.version}, actual ${aggregate.version}"
            ).failure()
        }

        val expectedVersion = aggregate.version

        // Create event metadata
        val metadata = EventMetadata.create(
            tenantId = command.tenantId,
            userId = command.adminId,
            correlationId = correlationId
        )

        // Perform approval (may throw SelfApprovalException or InvalidStateException)
        try {
            aggregate.approve(adminId = command.adminId, metadata = metadata)
        } catch (e: SelfApprovalException) {
            logger.warn(e) {
                "Self-approval attempt: " +
                    "requestId=${command.requestId.value}, " +
                    "adminId=${command.adminId.value}"
            }
            return ApproveVmRequestError.Forbidden().failure()
        } catch (e: InvalidStateException) {
            logger.debug {
                "Approval failed: requestId=${command.requestId.value}, currentState=${e.currentState.name}"
            }
            return ApproveVmRequestError.InvalidState(
                currentState = e.currentState.name
            ).failure()
        }

        // Persist new events to the event store
        val appendResult = try {
            eventStore.append(
                aggregateId = command.requestId.value,
                events = aggregate.uncommittedEvents,
                expectedVersion = expectedVersion
            )
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to persist approval event: " +
                    "requestId=${command.requestId.value}, " +
                    "tenantId=${command.tenantId.value}, " +
                    "adminId=${command.adminId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return ApproveVmRequestError.PersistenceFailure(
                message = "Failed to persist approval: ${e.message}"
            ).failure()
        }

        return when (appendResult) {
            is Result.Success -> {
                aggregate.clearUncommittedEvents()

                logger.info {
                    "Admin action: tenant=${command.tenantId.value}, " +
                        "admin=${command.adminId.value}, " +
                        "action=APPROVE, " +
                        "requestId=${command.requestId.value}"
                }

                // Update projection - log errors but don't fail the command
                projectionUpdater.updateStatus(
                    VmRequestStatusUpdate(
                        id = command.requestId,
                        status = VmRequestStatus.APPROVED,
                        version = aggregate.version.toInt(),
                        approvedBy = command.adminId
                    )
                ).onFailure { projectionError ->
                    logProjectionError(projectionError, command.requestId, correlationId)
                }

                // Update timeline projection
                timelineUpdater.addTimelineEvent(
                    NewTimelineEvent(
                        id = UUID.nameUUIDFromBytes(
                            "APPROVED:${correlationId.value}".toByteArray()
                        ),
                        requestId = command.requestId,
                        tenantId = command.tenantId,
                        eventType = TimelineEventType.APPROVED,
                        actorId = command.adminId,
                        actorName = null, // Resolved at query time
                        details = null,
                        occurredAt = metadata.timestamp
                    )
                ).onFailure { projectionError ->
                    logProjectionError(projectionError, command.requestId, correlationId)
                }

                // Send notification (fire-and-forget, don't fail the command)
                val requesterEmail = try {
                    EmailAddress.of(aggregate.requesterEmail)
                } catch (e: IllegalArgumentException) {
                    logger.error {
                        "Invalid requester email in aggregate, skipping notification: " +
                            "requestId=${command.requestId.value}, " +
                            "email='${aggregate.requesterEmail}', " +
                            "correlationId=${correlationId.value}"
                    }
                    null
                }
                if (requesterEmail != null) {
                    notificationSender.sendApprovedNotification(
                        RequestApprovedNotification(
                            requestId = command.requestId,
                            tenantId = command.tenantId,
                            requesterEmail = requesterEmail,
                            vmName = aggregate.vmName.value,
                            projectName = aggregate.projectId.value.toString()
                        )
                    ).onFailure { notificationError ->
                        logger.logNotificationError(notificationError, command.requestId, correlationId, "Approval")
                    }
                }

                ApproveVmRequestResult(requestId = command.requestId).success()
            }
            is Result.Failure -> when (val error = appendResult.error) {
                is EventStoreError.ConcurrencyConflict -> {
                    ApproveVmRequestError.ConcurrencyConflict(
                        message = "Concurrent modification detected for request ${error.aggregateId}"
                    ).failure()
                }
            }
        }
    }

    private fun logProjectionError(
        error: ProjectionError,
        requestId: VmRequestId,
        correlationId: CorrelationId
    ) {
        when (error) {
            is ProjectionError.DatabaseError -> {
                logger.warn {
                    "Projection update failed for request ${requestId.value}: ${error.message}. " +
                        "correlationId=${correlationId.value}. " +
                        "Projection can be rebuilt from event store."
                }
            }
            is ProjectionError.NotFound -> {
                logger.warn {
                    "Projection not found for request ${requestId.value}. " +
                        "correlationId=${correlationId.value}. " +
                        "Projection may need to be reconstructed from event store."
                }
            }
        }
    }

}
