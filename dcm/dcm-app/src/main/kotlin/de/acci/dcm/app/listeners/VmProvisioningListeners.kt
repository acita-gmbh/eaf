package de.acci.dcm.app.listeners

import de.acci.dcm.application.vm.TriggerProvisioningHandler
import de.acci.dcm.application.vm.VmProvisioningListener
import de.acci.dcm.application.vm.VmRequestStatusUpdater
import de.acci.dcm.domain.vm.events.VmProvisioningStarted
import de.acci.dcm.domain.vmrequest.events.VmRequestApproved
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Spring Event Listeners for VM Provisioning flow.
 *
 * Delegates Spring application events to Domain/Application handlers.
 * Uses application-scoped CoroutineScope to avoid blocking event-loop threads.
 */
@Component
public class VmProvisioningListeners(
    private val provisioningListener: VmProvisioningListener,
    private val triggerProvisioningHandler: TriggerProvisioningHandler,
    private val requestStatusUpdater: VmRequestStatusUpdater
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Application-scoped coroutine scope for async event handling.
     * Uses SupervisorJob to prevent child failures from cancelling siblings.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @EventListener
    public fun onVmRequestApproved(event: VmRequestApproved) {
        scope.launch {
            try {
                logger.info { "Listener received VmRequestApproved: ${event.aggregateId.value}" }
                provisioningListener.onVmRequestApproved(event)
            } catch (e: Exception) {
                logger.error(e) { "Error handling VmRequestApproved for ${event.aggregateId.value}" }
            }
        }
    }

    @EventListener
    public fun onVmProvisioningStarted(event: VmProvisioningStarted) {
        logger.info { "Listener received VmProvisioningStarted: ${event.aggregateId.value}" }
        // Launch handlers independently so failure of one doesn't block the other
        scope.launch {
            try {
                triggerProvisioningHandler.onVmProvisioningStarted(event)
            } catch (e: Exception) {
                logger.error(e) { "Error in triggerProvisioningHandler for ${event.aggregateId.value}" }
            }
        }
        scope.launch {
            try {
                requestStatusUpdater.onVmProvisioningStarted(event)
            } catch (e: Exception) {
                logger.error(e) { "Error in requestStatusUpdater for ${event.aggregateId.value}" }
            }
        }
    }

    @PreDestroy
    public fun cleanup() {
        scope.cancel()
    }
}