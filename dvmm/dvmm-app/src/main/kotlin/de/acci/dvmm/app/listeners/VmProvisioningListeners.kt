package de.acci.dvmm.app.listeners

import de.acci.dvmm.application.vm.TriggerProvisioningHandler
import de.acci.dvmm.application.vm.VmProvisioningListener
import de.acci.dvmm.application.vm.VmRequestStatusUpdater
import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import de.acci.dvmm.domain.vmrequest.events.VmRequestApproved
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking

/**
 * Spring Event Listeners for VM Provisioning flow.
 *
 * Delegates Spring application events to Domain/Application handlers.
 */
@Component
public class VmProvisioningListeners(
    private val provisioningListener: VmProvisioningListener,
    private val triggerProvisioningHandler: TriggerProvisioningHandler,
    private val requestStatusUpdater: VmRequestStatusUpdater
) {
    private val logger = KotlinLogging.logger {}

    @EventListener
    public fun onVmRequestApproved(event: VmRequestApproved): Unit = runBlocking {
        logger.info { "Listener received VmRequestApproved: ${event.aggregateId.value}" }
        provisioningListener.onVmRequestApproved(event)
    }

    @EventListener
    public fun onVmProvisioningStarted(event: VmProvisioningStarted): Unit = runBlocking {
        logger.info { "Listener received VmProvisioningStarted: ${event.aggregateId.value}" }
        triggerProvisioningHandler.onVmProvisioningStarted(event)
        requestStatusUpdater.onVmProvisioningStarted(event)
    }
}