package de.acci.dvmm.application.vm

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.tenant.TenantContext
import io.github.oshai.kotlinlogging.KotlinLogging

import kotlin.coroutines.cancellation.CancellationException

private val logger = KotlinLogging.logger {}

public class VmProvisioningProgressQueryService(
    private val repository: VmProvisioningProgressProjectionRepository
) {
    public suspend fun getProgress(vmRequestId: VmRequestId): Result<VmProvisioningProgressProjection?, Error> {
        val tenantId = try {
            TenantContext.current()
        } catch (e: CancellationException) {
            throw e // Allow proper coroutine cancellation
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get tenant context for provisioning progress query" }
            return Error("No tenant context").failure()
        }

        return repository.findByVmRequestId(vmRequestId, tenantId).success()
    }

    public data class Error(val message: String)
}