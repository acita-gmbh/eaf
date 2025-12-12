package de.acci.dcm.application.vm

import de.acci.dcm.application.vmrequest.MarkVmRequestProvisioningCommand
import de.acci.dcm.application.vmrequest.MarkVmRequestProvisioningHandler
import de.acci.dcm.domain.vm.events.VmProvisioningStarted
import io.github.oshai.kotlinlogging.KotlinLogging
import de.acci.eaf.core.result.onFailure
import de.acci.eaf.core.result.onSuccess

/**
 * Listens for VmProvisioningStarted events and updates the associated VmRequest status.
 */
public class VmRequestStatusUpdater(
    private val handler: MarkVmRequestProvisioningHandler
) {
    private val logger = KotlinLogging.logger {}

    public suspend fun onVmProvisioningStarted(event: VmProvisioningStarted) {
        val command = MarkVmRequestProvisioningCommand(
            requestId = event.requestId,
            tenantId = event.metadata.tenantId,
            userId = event.metadata.userId
        )
        
        handler.handle(command, event.metadata.correlationId)
            .onFailure { error ->
                logger.error { "Failed to mark request ${event.requestId.value} as provisioning: $error" }
            }
            .onSuccess {
                logger.info { "Marked request ${event.requestId.value} as provisioning" }
            }
    }
}
