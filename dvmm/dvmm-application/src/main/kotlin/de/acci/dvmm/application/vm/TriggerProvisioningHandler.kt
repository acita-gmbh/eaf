package de.acci.dvmm.application.vm

import de.acci.dvmm.application.vmrequest.NewTimelineEvent
import de.acci.dvmm.application.vmrequest.TimelineEventProjectionUpdater
import de.acci.dvmm.application.vmrequest.TimelineEventType
import de.acci.dvmm.application.vmrequest.VmRequestEventDeserializer
import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VmwareConfigurationPort
import de.acci.dvmm.application.vmware.VspherePort
import de.acci.dvmm.domain.vm.VmAggregate
import de.acci.dvmm.domain.vm.VmProvisioningResult
import de.acci.dvmm.domain.vm.events.VmProvisioningFailed
import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import de.acci.dvmm.domain.vmrequest.VmRequestAggregate
import de.acci.eaf.core.result.Result
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.EventStore
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.util.UUID

/**
 * Handles VmProvisioningStarted event by calling VspherePort to create the VM.
 *
 * On success, emits VmProvisioned (VM aggregate) and VmRequestReady (VmRequest aggregate)
 * events, plus updates the timeline projection.
 *
 * On failure (missing config or vSphere error), emits VmProvisioningFailed event
 * so downstream systems can react appropriately.
 */
public class TriggerProvisioningHandler(
    private val vspherePort: VspherePort,
    private val configPort: VmwareConfigurationPort,
    private val eventStore: EventStore,
    private val vmEventDeserializer: VmEventDeserializer,
    private val vmRequestEventDeserializer: VmRequestEventDeserializer,
    private val timelineUpdater: TimelineEventProjectionUpdater
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
                val provisioningResult = result.value
                logger.info {
                    "VM provisioning completed for ${event.vmName.value}. " +
                        "vCenter ID: ${provisioningResult.vmwareVmId.value}, " +
                        "IP: ${provisioningResult.ipAddress ?: "pending"}"
                }
                emitSuccess(event, provisioningResult)
            }
            is Result.Failure -> {
                val error = result.error
                val reason = "vSphere provisioning failed: $error"
                logger.error { "Failed to initiate provisioning for ${event.vmName.value}: $error" }
                emitFailure(event, reason)
            }
        }
    }

    private suspend fun emitSuccess(event: VmProvisioningStarted, provisioningResult: VmProvisioningResult) {
        try {
            // 1. Update VM aggregate with VmProvisioned event
            val vmEvents = eventStore.load(event.aggregateId.value)
            if (vmEvents.isEmpty()) {
                logger.error { "Cannot emit VmProvisioned: aggregate ${event.aggregateId.value} not found" }
                return
            }

            val vmAggregate = VmAggregate.reconstitute(
                id = event.aggregateId,
                events = vmEvents.map { vmEventDeserializer.deserialize(it) }
            )
            vmAggregate.markProvisioned(
                vmwareVmId = provisioningResult.vmwareVmId,
                ipAddress = provisioningResult.ipAddress,
                hostname = provisioningResult.hostname,
                warningMessage = provisioningResult.warningMessage,
                metadata = event.metadata
            )

            val vmAppendResult = eventStore.append(
                aggregateId = event.aggregateId.value,
                events = vmAggregate.uncommittedEvents,
                expectedVersion = vmEvents.size.toLong()
            )
            when (vmAppendResult) {
                is Result.Success -> logger.info { "Emitted VmProvisioned for VM ${event.aggregateId.value}" }
                is Result.Failure -> {
                    logger.error { "Failed to emit VmProvisioned: ${vmAppendResult.error}" }
                    return
                }
            }

            // 2. Update VmRequest aggregate with VmRequestReady event
            val requestEvents = eventStore.load(event.requestId.value)
            if (requestEvents.isEmpty()) {
                logger.error { "Cannot emit VmRequestReady: request ${event.requestId.value} not found" }
                return
            }

            val requestAggregate = VmRequestAggregate.reconstitute(
                id = event.requestId,
                events = requestEvents.map { vmRequestEventDeserializer.deserialize(it) }
            )
            requestAggregate.markReady(
                vmwareVmId = provisioningResult.vmwareVmId,
                ipAddress = provisioningResult.ipAddress,
                hostname = provisioningResult.hostname,
                warningMessage = provisioningResult.warningMessage,
                metadata = event.metadata
            )

            val requestAppendResult = eventStore.append(
                aggregateId = event.requestId.value,
                events = requestAggregate.uncommittedEvents,
                expectedVersion = requestEvents.size.toLong()
            )
            when (requestAppendResult) {
                is Result.Success -> logger.info { "Emitted VmRequestReady for request ${event.requestId.value}" }
                is Result.Failure -> {
                    logger.error { "Failed to emit VmRequestReady: ${requestAppendResult.error}" }
                    return
                }
            }

            // 3. Add timeline event for VM_READY
            val timelineResult = timelineUpdater.addTimelineEvent(
                NewTimelineEvent(
                    id = UUID.randomUUID(),
                    requestId = event.requestId,
                    tenantId = event.metadata.tenantId,
                    eventType = TimelineEventType.VM_READY,
                    actorId = null, // System event
                    actorName = "System",
                    details = buildReadyDetails(provisioningResult),
                    occurredAt = Instant.now()
                )
            )
            when (timelineResult) {
                is Result.Success -> logger.info { "Added VM_READY timeline event for request ${event.requestId.value}" }
                is Result.Failure -> logger.error { "Failed to add VM_READY timeline: ${timelineResult.error}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to emit provisioning success events for VM ${event.aggregateId.value}" }
        }
    }

    private fun buildReadyDetails(result: VmProvisioningResult): String {
        val parts = mutableListOf<String>()
        parts.add("VM ID: ${result.vmwareVmId.value}")
        parts.add("Hostname: ${result.hostname}")
        result.ipAddress?.let { parts.add("IP: $it") }
        result.warningMessage?.let { parts.add("Warning: $it") }
        return parts.joinToString(", ")
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
