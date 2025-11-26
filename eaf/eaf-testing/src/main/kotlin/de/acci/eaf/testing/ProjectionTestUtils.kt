package de.acci.eaf.testing

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Utilities for testing projections in event-sourced systems.
 *
 * Projections may have eventual consistency delays. These utilities
 * help tests wait for projections to be updated before making assertions.
 */
public object ProjectionTestUtils {

    /**
     * Default timeout for waiting for projections.
     */
    public val DEFAULT_TIMEOUT: Duration = 5.seconds

    /**
     * Default polling interval when checking for projections.
     */
    public val DEFAULT_POLL_INTERVAL: Duration = 50.milliseconds

    /**
     * Waits for a projection to become available.
     *
     * This function polls the repository until the projection is found or the timeout expires.
     * Useful for handling eventual consistency in CQRS/Event Sourcing systems where projections
     * are updated asynchronously after events are persisted.
     *
     * @param T The projected entity type
     * @param repository A suspend function that queries for the projection and returns null if not found
     * @param timeout Maximum time to wait for the projection (default: 5 seconds)
     * @param pollInterval Time between polling attempts (default: 50ms)
     * @return The projected entity when found
     * @throws TimeoutCancellationException if the projection is not found within the timeout period
     *
     * @sample
     * ```kotlin
     * // Wait for a VmRequestProjection to appear after command processing
     * val projection = awaitProjection(
     *     repository = { vmRequestRepository.findById(aggregateId) },
     *     timeout = 5.seconds
     * )
     * assertEquals("PENDING", projection.status)
     * ```
     */
    public suspend fun <T : Any> awaitProjection(
        repository: suspend () -> T?,
        timeout: Duration = DEFAULT_TIMEOUT,
        pollInterval: Duration = DEFAULT_POLL_INTERVAL
    ): T {
        return withTimeout(timeout) {
            while (true) {
                val result = repository()
                if (result != null) {
                    return@withTimeout result
                }
                delay(pollInterval)
            }
            @Suppress("UNREACHABLE_CODE")
            error("This should never be reached")
        }
    }

    /**
     * Waits for a projection to become available for a specific aggregate.
     *
     * Convenience overload that includes the aggregate ID for better traceability.
     *
     * @param T The projected entity type
     * @param aggregateId The ID of the aggregate whose projection we're waiting for
     * @param repository A suspend function that queries for the projection and returns null if not found
     * @param timeout Maximum time to wait for the projection (default: 5 seconds)
     * @param pollInterval Time between polling attempts (default: 50ms)
     * @return The projected entity when found
     * @throws TimeoutCancellationException if the projection is not found within the timeout period
     */
    public suspend fun <T : Any> awaitProjection(
        aggregateId: UUID,
        repository: suspend () -> T?,
        timeout: Duration = DEFAULT_TIMEOUT,
        pollInterval: Duration = DEFAULT_POLL_INTERVAL
    ): T {
        // aggregateId is available for debugging/logging purposes if needed
        return awaitProjection(repository, timeout, pollInterval)
    }

    /**
     * Waits for a projection to satisfy a given condition.
     *
     * Useful when waiting for a specific state change in an existing projection.
     *
     * @param T The projected entity type
     * @param repository A suspend function that queries for the projection
     * @param condition A predicate that the projection must satisfy
     * @param timeout Maximum time to wait for the condition (default: 5 seconds)
     * @param pollInterval Time between polling attempts (default: 50ms)
     * @return The projected entity when the condition is satisfied
     * @throws TimeoutCancellationException if the condition is not satisfied within the timeout period
     *
     * @sample
     * ```kotlin
     * // Wait for a VM request to reach APPROVED status
     * val projection = awaitProjectionCondition(
     *     repository = { vmRequestRepository.findById(aggregateId) },
     *     condition = { it?.status == "APPROVED" }
     * )
     * ```
     */
    public suspend fun <T : Any> awaitProjectionCondition(
        repository: suspend () -> T?,
        condition: (T?) -> Boolean,
        timeout: Duration = DEFAULT_TIMEOUT,
        pollInterval: Duration = DEFAULT_POLL_INTERVAL
    ): T {
        return withTimeout(timeout) {
            while (true) {
                val result = repository()
                if (condition(result) && result != null) {
                    return@withTimeout result
                }
                delay(pollInterval)
            }
            @Suppress("UNREACHABLE_CODE")
            error("This should never be reached")
        }
    }
}

/**
 * Extension function for convenient access to awaitProjection.
 *
 * @see ProjectionTestUtils.awaitProjection
 */
public suspend fun <T : Any> awaitProjection(
    repository: suspend () -> T?,
    timeout: Duration = ProjectionTestUtils.DEFAULT_TIMEOUT,
    pollInterval: Duration = ProjectionTestUtils.DEFAULT_POLL_INTERVAL
): T = ProjectionTestUtils.awaitProjection(repository, timeout, pollInterval)

/**
 * Extension function for convenient access to awaitProjection with aggregate ID.
 *
 * @see ProjectionTestUtils.awaitProjection
 */
public suspend fun <T : Any> awaitProjection(
    aggregateId: UUID,
    repository: suspend () -> T?,
    timeout: Duration = ProjectionTestUtils.DEFAULT_TIMEOUT,
    pollInterval: Duration = ProjectionTestUtils.DEFAULT_POLL_INTERVAL
): T = ProjectionTestUtils.awaitProjection(aggregateId, repository, timeout, pollInterval)
