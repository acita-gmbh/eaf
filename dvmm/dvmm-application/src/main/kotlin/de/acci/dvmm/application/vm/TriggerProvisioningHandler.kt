package de.acci.dvmm.application.vm

import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VmwareConfigurationPort
import de.acci.dvmm.application.vmware.VspherePort
import de.acci.dvmm.domain.vm.events.VmProvisioningFailed
import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import de.acci.eaf.core.result.Result
import de.acci.eaf.eventsourcing.EventStore
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Handles VmProvisioningStarted event by calling VspherePort to create the VM.
 *
 * On failure (missing config or vSphere error), emits VmProvisioningFailed event
 * so downstream systems can react appropriately.
 */
public class TriggerProvisioningHandler(
    private val vspherePort: VspherePort,
    private val configPort: VmwareConfigurationPort,
    private val eventStore: EventStore
) {
    private val logger = KotlinLogging.logger {}

    public suspend fun onVmProvisioningStarted(event: VmProvisioningStarted) {
        val tenantId = event.metadata.tenantId
        val config = configPort.findByTenantId(tenantId)

        if (config == null) {
            val reason = "VMware configuration missing for tenant ${tenantId.value}"
            logger.error {
                "$reason. Cannot provision VM ${event.aggregateId.value} (Request: ${event.requestId.value})"
            }
            emitFailure(event, reason)
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
            is Result.Success -> {
                val vmId = result.value
                logger.info { "Initiated VM provisioning for ${event.vmName.value}. vCenter ID: ${vmId.value}" }
            }
            is Result.Failure -> {
                val error = result.error
                val reason = "vSphere provisioning failed: $error"
                logger.error { "Failed to initiate provisioning for ${event.vmName.value}: $error" }
                emitFailure(event, reason)
            }
        }
    }

    private suspend fun emitFailure(event: VmProvisioningStarted, reason: String) {
        val failedEvent = VmProvisioningFailed(
            aggregateId = event.aggregateId,
            requestId = event.requestId,
            reason = reason,
            metadata = event.metadata
        )

        try {
            // Load current version to handle potential concurrent modifications
            val currentEvents = eventStore.load(event.aggregateId.value)
            if (currentEvents.isEmpty()) {
                logger.error { "Cannot emit VmProvisioningFailed: aggregate ${event.aggregateId.value} not found in event store" }
                return
            }
            val currentVersion = currentEvents.size.toLong()

            val result = eventStore.append(
                aggregateId = event.aggregateId.value,
                events = listOf(failedEvent),
                expectedVersion = currentVersion
            )
            when (result) {
                is Result.Success -> logger.info { "Emitted VmProvisioningFailed for VM ${event.aggregateId.value}" }
                is Result.Failure -> logger.error { "Failed to emit VmProvisioningFailed: ${result.error}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to emit VmProvisioningFailed event for VM ${event.aggregateId.value}" }
        }
    }
}
