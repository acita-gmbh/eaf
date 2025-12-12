package de.acci.dvmm.application.vm

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.tenant.TenantContext
import io.github.oshai.kotlinlogging.KotlinLogging

import kotlin.coroutines.cancellation.CancellationException

private val logger = KotlinLogging.logger {}

/**
 * Service for querying VM provisioning progress.
 */
public class VmProvisioningProgressQueryService(
    private val repository: VmProvisioningProgressProjectionRepository
) {
    /**
     * Retrieves the current provisioning progress for a request.
     *
     * @param vmRequestId ID of the request.
     * @return The progress projection, or null if not found.
     */
    public suspend fun getProgress(vmRequestId: VmRequestId): Result<VmProvisioningProgressProjection?, Error> {
        val tenantId = try {
            TenantContext.current()
        } catch (e: CancellationException) {
            throw e // Allow proper coroutine cancellation
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get tenant context for provisioning progress query of request ${vmRequestId.value}" }
            return Error.TenantContextUnavailable(vmRequestId, e).failure()
        }

        return repository.findByVmRequestId(vmRequestId, tenantId).success()
    }

    /** Service error. */
    public sealed interface Error {
        public data class TenantContextUnavailable(
            val vmRequestId: VmRequestId,
            val cause: Throwable? = null
        ) : Error
    }
}