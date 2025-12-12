package de.acci.dcm.application.vm

import de.acci.dcm.domain.vmrequest.VmRequestId
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
            return Error.TenantContextUnavailable(
                vmRequestId = vmRequestId,
                message = "Tenant context missing or invalid: ${e.message}"
            ).failure()
        }

        return try {
            repository.findByVmRequestId(vmRequestId, tenantId).success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to query provisioning progress for request ${vmRequestId.value}" }
            Error.QueryFailure("Failed to query provisioning progress: ${e.message}").failure()
        }
    }

    /** Service errors. */
    public sealed interface Error {
        /** Tenant context is missing or invalid. */
        public data class TenantContextUnavailable(
            val vmRequestId: VmRequestId,
            val message: String
        ) : Error

        /** Database query failed. */
        public data class QueryFailure(val message: String) : Error
    }
}