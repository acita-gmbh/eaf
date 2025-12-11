package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.application.vmware.HypervisorPort
import de.acci.dvmm.application.vmware.VmId
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Errors that can occur when synchronizing VM status.
 */
public sealed class SyncVmStatusError {
    /**
     * VM request not found in the projection.
     */
    public data class NotFound(
        val requestId: VmRequestId,
        val message: String = "VM request not found: ${requestId.value}"
    ) : SyncVmStatusError()

    /**
     * VM has not been provisioned yet (no vmwareVmId).
     */
    public data class NotProvisioned(
        val requestId: VmRequestId,
        val message: String = "VM has not been provisioned yet"
    ) : SyncVmStatusError()

    /**
     * Failed to query VM status from vSphere.
     */
    public data class HypervisorError(
        val message: String
    ) : SyncVmStatusError()

    /**
     * Failed to update the projection.
     */
    public data class UpdateFailure(
        val message: String
    ) : SyncVmStatusError()
}

/**
 * Result of a successful VM status sync.
 *
 * @property requestId The request that was synced
 * @property powerState Current power state
 * @property ipAddress Current IP address (null if not detected)
 */
public data class SyncVmStatusResult(
    val requestId: VmRequestId,
    val powerState: String,
    val ipAddress: String?
)

/**
 * Handler for SyncVmStatusCommand.
 *
 * Story 3-7: Synchronizes VM runtime details from vSphere to the projection.
 *
 * ## Workflow
 *
 * 1. Look up the VMware VM ID from the projection
 * 2. Query vSphere for current VM status
 * 3. Update the projection with new values
 *
 * ## Authorization
 *
 * Currently, any authenticated user can sync status for VMs they can view.
 * The underlying projection query uses RLS to enforce tenant isolation.
 *
 * ## Usage
 *
 * ```kotlin
 * val handler = SyncVmStatusHandler(hypervisorPort, projectionPort, clock)
 * val result = handler.handle(command)
 * result.fold(
 *     onSuccess = { println("Synced: ${it.powerState}, IP: ${it.ipAddress}") },
 *     onFailure = { println("Failed: $it") }
 * )
 * ```
 */
public class SyncVmStatusHandler(
    private val hypervisorPort: HypervisorPort,
    private val projectionPort: VmStatusProjectionPort,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Handle the sync VM status command.
     *
     * @param command Command containing request ID and context
     * @return Result containing sync result or error
     */
    public suspend fun handle(
        command: SyncVmStatusCommand
    ): Result<SyncVmStatusResult, SyncVmStatusError> {
        val requestId = command.requestId

        logger.debug { "Syncing VM status for request ${requestId.value}" }

        // Step 1: Get VMware VM ID from projection
        val vmwareVmId = try {
            projectionPort.getVmwareVmId(requestId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to look up vmwareVmId for request ${requestId.value}" }
            return SyncVmStatusError.UpdateFailure("Failed to look up VM: ${e.message}").failure()
        }

        if (vmwareVmId == null) {
            logger.debug { "VM not provisioned yet for request ${requestId.value}" }
            return SyncVmStatusError.NotProvisioned(requestId).failure()
        }

        // Step 2: Query vSphere for current VM status
        val vmInfo = when (val result = hypervisorPort.getVm(VmId(vmwareVmId))) {
            is Result.Success -> result.value
            is Result.Failure -> {
                logger.warn { "Failed to get VM status from vSphere: ${result.error.message}" }
                return SyncVmStatusError.HypervisorError(
                    result.error.userMessage
                ).failure()
            }
        }

        // Step 3: Update the projection with new values
        val updatedRows = try {
            projectionPort.updateVmDetails(
                requestId = requestId,
                vmwareVmId = vmwareVmId,
                ipAddress = vmInfo.ipAddress,
                hostname = vmInfo.hostname,
                powerState = vmInfo.powerState.name,
                guestOs = vmInfo.guestOs,
                lastSyncedAt = clock.instant()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to update projection for request ${requestId.value}" }
            return SyncVmStatusError.UpdateFailure("Failed to save VM status: ${e.message}").failure()
        }

        if (updatedRows == 0) {
            logger.warn { "No rows updated for request ${requestId.value} - may not exist" }
            return SyncVmStatusError.NotFound(requestId).failure()
        }

        logger.info {
            "Synced VM status for request ${requestId.value}: " +
                "powerState=${vmInfo.powerState}, ip=${vmInfo.ipAddress}"
        }

        return SyncVmStatusResult(
            requestId = requestId,
            powerState = vmInfo.powerState.name,
            ipAddress = vmInfo.ipAddress
        ).success()
    }
}
