package de.acci.dvmm.application.vm

import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VmwareConfigurationPort
import de.acci.dvmm.application.vmware.VspherePort
import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Handles VmProvisioningStarted event by calling VspherePort to create the VM.
 */
public class TriggerProvisioningHandler(
    private val vspherePort: VspherePort,
    private val configPort: VmwareConfigurationPort
) {
    private val logger = KotlinLogging.logger {}

    public suspend fun onVmProvisioningStarted(event: VmProvisioningStarted) {
        val tenantId = event.metadata.tenantId
        val config = configPort.findByTenantId(tenantId)
        
        if (config == null) {
            logger.error { 
                "VMware configuration missing for tenant ${tenantId.value}. " +
                "Cannot provision VM ${event.aggregateId.value} (Request: ${event.requestId.value})" 
            }
            return
        }

        val spec = VmSpec(
            name = event.vmName.value,
            template = config.templateName,
            cpu = event.size.cpuCores,
            memoryGb = event.size.memoryGb
        )

        val result = vspherePort.createVm(spec)
        when (result) {
            is de.acci.eaf.core.result.Result.Success -> {
                val vmId = result.value
                logger.info { "Initiated VM provisioning for ${event.vmName.value}. vCenter ID: ${vmId.value}" }
            }
            is de.acci.eaf.core.result.Result.Failure -> {
                val error = result.error
                logger.error { "Failed to initiate provisioning for ${event.vmName.value}: $error" }
            }
        }
    }
}
