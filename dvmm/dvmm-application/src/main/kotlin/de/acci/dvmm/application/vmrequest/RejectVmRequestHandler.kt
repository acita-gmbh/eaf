package de.acci.dvmm.application.vmrequest

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import kotlin.coroutines.cancellation.CancellationException

/**
 * Errors that can occur when rejecting a VM request.
 */
public sealed class RejectVmRequestError {
    /**
     * Request not found in the event store.
     */
    public data class NotFound(
        val requestId: VmRequestId,
        val message: String = "VM request not found: ${requestId.value}"
    ) : RejectVmRequestError()

    /**
     * Admin is not authorized to reject this request.
     * Occurs when admin tries to reject their own request (separation of duties).
     */
    public data class Forbidden(
        val message: String = "Cannot reject own request: separation of duties requires a different admin"
    ) : RejectVmRequestError()

    /**
     * Request is not in a rejectable state.
     * Only PENDING requests can be rejected.
     */
    public data class InvalidState(
        val currentState: String,
        val message: String = "Cannot reject request in state: $currentState"
    ) : RejectVmRequestError()

    /**
     * Concurrent modification detected (optimistic locking failure).
     */
    public data class ConcurrencyConflict(
        val message: String
    ) : RejectVmRequestError()

    /**
     * Unexpected persistence failure.
     */
    public data class PersistenceFailure(
        val message: String
    ) : RejectVmRequestError()

    /**
     * Invalid reason provided (too short or too long).
     */
    public data class InvalidReason(
        val message: String
    ) : RejectVmRequestError()
}

/**
 * Result of successfully rejecting a VM request.
 */
public data class RejectVmRequestResult(
    val requestId: VmRequestId
)

/**
 * Handler for RejectVmRequestCommand.
 *
 * Loads the VmRequestAggregate from the event store, verifies admin is not
 * the requester (separation of duties), performs the rejection with a mandatory
 * reason, and persists the resulting events.
 *
 * ## Authorization
 *
 * Only admins can reject requests, and they cannot reject their own requests.
 * Attempting to reject your own request returns Forbidden error.
 *
 * ## Optimistic Locking
 *
 * The command includes the expected version. If another admin modified the
 * request concurrently, a ConcurrencyConflict error is returned.
 *
 * ## Reason Validation
 *
 * Rejection reason must be 10-500 characters. Invalid reason returns InvalidReason error.
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = RejectVmRequestHandler(eventStore, eventDeserializer)
 * val result = handler.handle(command)
 * result.fold(
 *     onSuccess = { println("Rejected request: ${it.requestId}") },
 *     onFailure = { println("Failed: $it") }
 * )
 * ```
 */
public class RejectVmRequestHandler(
    private val eventStore: EventStore,
    private val eventDeserializer: VmRequestEventDeserializer,
    private val projectionUpdater: VmRequestProjectionUpdater = NoOpVmRequestProjectionUpdater,
    private val timelineUpdater: TimelineEventProjectionUpdater = NoOpTimelineEventProjectionUpdater,
    private val notificationSender: VmRequestNotificationSender = NoOpVmRequestNotificationSender
) {
    private val logger = KotlinLogging.logger {}

    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }

    /**
     * Handle the reject VM request command.
     *
     * @param command The command to process
     * @param correlationId Optional correlation ID for distributed tracing
     * @return Result containing the rejected request ID or an error
     */
    public suspend fun handle(
        command: RejectVmRequestCommand,
        correlationId: CorrelationId = CorrelationId.generate()
    ): Result<RejectVmRequestResult, RejectVmRequestError> {
        // Load events from event store
        val storedEvents = try {
            eventStore.load(command.requestId.value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to load events for request: " +
                    "requestId=${command.requestId.value}, " +
                    "tenantId=${command.tenantId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return RejectVmRequestError.PersistenceFailure(
                message = "Failed to load request: ${e.message}"
            ).failure()
        }

        // Check if aggregate exists
        if (storedEvents.isEmpty()) {
            return RejectVmRequestError.NotFound(
                requestId = command.requestId
            ).failure()
        }

        // Deserialize events and reconstitute aggregate
        val domainEvents = try {
            storedEvents.map { eventDeserializer.deserialize(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to deserialize events for request: " +
                    "requestId=${command.requestId.value}, " +
                    "tenantId=${command.tenantId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return RejectVmRequestError.PersistenceFailure(
                message = "Failed to deserialize request events: ${e.message}"
            ).failure()
        }
        val aggregate = VmRequestAggregate.reconstitute(command.requestId, domainEvents)

        // Verify expected version matches (optimistic locking)
        if (aggregate.version != command.version) {
            return RejectVmRequestError.ConcurrencyConflict(
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

        // Perform rejection (may throw SelfApprovalException, InvalidStateException, or IllegalArgumentException)
        try {
            aggregate.reject(
                adminId = command.adminId,
                reason = command.reason,
                metadata = metadata
            )
        } catch (e: SelfApprovalException) {
            logger.warn(e) {
                "Self-rejection attempt: " +
                    "requestId=${command.requestId.value}, " +
                    "adminId=${command.adminId.value}"
            }
            return RejectVmRequestError.Forbidden().failure()
        } catch (e: InvalidStateException) {
            logger.debug {
                "Rejection failed: requestId=${command.requestId.value}, currentState=${e.currentState.name}"
            }
            return RejectVmRequestError.InvalidState(
                currentState = e.currentState.name
            ).failure()
        } catch (e: IllegalArgumentException) {
            logger.debug {
                "Invalid rejection reason: requestId=${command.requestId.value}, error=${e.message}"
            }
            return RejectVmRequestError.InvalidReason(
                message = e.message ?: "Invalid rejection reason"
            ).failure()
        }

        // Persist new events to the event store
        val appendResult = try {
            eventStore.append(
                aggregateId = command.requestId.value,
                events = aggregate.uncommittedEvents,
                expectedVersion = expectedVersion
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to persist rejection event: " +
                    "requestId=${command.requestId.value}, " +
                    "tenantId=${command.tenantId.value}, " +
                    "adminId=${command.adminId.value}, " +
                    "correlationId=${correlationId.value}"
            }
            return RejectVmRequestError.PersistenceFailure(
                message = "Failed to persist rejection: ${e.message}"
            ).failure()
        }

        return when (appendResult) {
            is Result.Success -> {
                aggregate.clearUncommittedEvents()

                logger.info {
                    "Admin action: tenant=${command.tenantId.value}, " +
                        "admin=${command.adminId.value}, " +
                        "action=REJECT, " +
                        "requestId=${command.requestId.value}"
                }

                // Update projection - log errors but don't fail the command
                projectionUpdater.updateStatus(
                    VmRequestStatusUpdate(
                        id = command.requestId,
                        status = VmRequestStatus.REJECTED,
                        version = aggregate.version.toInt(),
                        rejectedBy = command.adminId,
                        rejectionReason = command.reason
                    )
                ).onFailure { projectionError ->
                    logProjectionError(projectionError, command.requestId, correlationId)
                }

                // Update timeline projection with reason in details
                // Serialize reason to JSON, with fallback if serialization fails
                val details = try {
                    objectMapper.writeValueAsString(mapOf("reason" to command.reason))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(e) {
                        "Failed to serialize rejection reason for timeline: " +
                            "requestId=${command.requestId.value}, " +
                            "correlationId=${correlationId.value}. " +
                            "Using fallback details."
                    }
                    // Fallback: simple JSON that doesn't require serialization
                    """{"reason":"<see-event-store>"}"""
                }

                timelineUpdater.addTimelineEvent(
                    NewTimelineEvent(
                        id = UUID.nameUUIDFromBytes(
                            "REJECTED:${correlationId.value}".toByteArray()
                        ),
                        requestId = command.requestId,
                        tenantId = command.tenantId,
                        eventType = TimelineEventType.REJECTED,
                        actorId = command.adminId,
                        actorName = null, // Resolved at query time
                        details = details,
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
                            "correlationId=${correlationId.value}"
                    }
                    null
                }
                if (requesterEmail != null) {
                    notificationSender.sendRejectedNotification(
                        RequestRejectedNotification(
                            requestId = command.requestId,
                            tenantId = command.tenantId,
                            requesterEmail = requesterEmail,
                            vmName = aggregate.vmName.value,
                            projectName = aggregate.projectId.value.toString(),
                            reason = command.reason
                        )
                    ).onFailure { notificationError ->
                        logger.logNotificationError(notificationError, command.requestId, correlationId, "Rejection")
                    }
                }

                RejectVmRequestResult(requestId = command.requestId).success()
            }
            is Result.Failure -> when (val error = appendResult.error) {
                is EventStoreError.ConcurrencyConflict -> {
                    RejectVmRequestError.ConcurrencyConflict(
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
