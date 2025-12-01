package de.acci.eaf.eventsourcing.projection

/**
 * Errors that can occur during projection update operations.
 *
 * Projection updates are typically fire-and-forget operations where failures
 * should be logged for monitoring but not fail the command that triggered them.
 * Failed projections can be reconstructed from the event store.
 */
public sealed class ProjectionError {

    /**
     * Database operation failed.
     *
     * @property aggregateId The aggregate whose projection failed to update
     * @property message Human-readable error description
     * @property cause Optional underlying exception
     */
    public data class DatabaseError(
        public val aggregateId: String,
        public val message: String,
        public val cause: Throwable? = null
    ) : ProjectionError()

    /**
     * Projection record not found when update was expected.
     *
     * This may indicate the projection is out of sync and needs reconstruction
     * from the event store.
     *
     * @property aggregateId The aggregate whose projection was not found
     */
    public data class NotFound(
        public val aggregateId: String
    ) : ProjectionError()
}
