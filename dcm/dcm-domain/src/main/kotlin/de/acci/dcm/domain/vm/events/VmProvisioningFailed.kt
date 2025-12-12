package de.acci.dcm.domain.vm.events

import de.acci.dcm.domain.vm.VmId
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata
import java.time.Instant

/**
 * Event emitted when VM provisioning fails.
 *
 * This event signals that the provisioning process could not complete,
 * allowing downstream systems to react (e.g., update request status, notify user).
 *
 * ## AC-3.6.2 Compliance
 *
 * This event captures all required failure information:
 * - `errorCode`: Machine-readable error code for categorization
 * - `errorMessage`: User-friendly error message (was `reason`)
 * - `retryCount`: Number of retry attempts before final failure
 * - `lastAttemptAt`: Timestamp of the final failed attempt
 *
 * @property aggregateId The VM aggregate ID
 * @property requestId The originating request ID for traceability
 * @property errorCode Machine-readable error code (e.g., "CONNECTION_TIMEOUT", "INSUFFICIENT_RESOURCES")
 * @property errorMessage User-friendly error message suitable for display
 * @property retryCount Number of retry attempts made (including the initial attempt)
 * @property lastAttemptAt Timestamp of the final failed attempt
 * @property reason Legacy field - use [errorMessage] instead. Kept for backward compatibility.
 */
public data class VmProvisioningFailed(
    public val aggregateId: VmId,
    public val requestId: VmRequestId,
    /** @deprecated Use [errorMessage] instead */
    public val reason: String,
    public val errorCode: String = "UNKNOWN",
    public val errorMessage: String = reason,
    public val retryCount: Int = 1,
    /** Defaults to EPOCH for backward compatibility with legacy events missing this field */
    public val lastAttemptAt: Instant = Instant.EPOCH,
    override val metadata: EventMetadata
) : DomainEvent {
    override val aggregateType: String = AGGREGATE_TYPE

    public companion object {
        public const val AGGREGATE_TYPE: String = "Vm"
    }
}
