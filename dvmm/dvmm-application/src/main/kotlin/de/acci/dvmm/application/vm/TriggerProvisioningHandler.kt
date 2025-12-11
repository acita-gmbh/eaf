package de.acci.dvmm.application.vm

import de.acci.dvmm.application.vmrequest.NewTimelineEvent
import de.acci.dvmm.application.vmrequest.ProvisioningFailedAdminNotification
import de.acci.dvmm.application.vmrequest.ProvisioningFailedUserNotification
import de.acci.dvmm.application.vmrequest.TimelineEventProjectionUpdater
import de.acci.dvmm.application.vmrequest.TimelineEventType
import de.acci.dvmm.application.vmrequest.VmRequestEventDeserializer
import de.acci.dvmm.application.vmrequest.VmRequestNotificationSender
import de.acci.dvmm.application.vmrequest.VmRequestReadRepository
import de.acci.dvmm.application.vmrequest.logNotificationError
import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VmwareConfigurationPort
import de.acci.dvmm.application.vmware.VsphereError
import de.acci.dvmm.application.vmware.HypervisorPort
import de.acci.dvmm.domain.vm.VmAggregate
import de.acci.dvmm.domain.vm.VmProvisioningResult
import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.dvmm.domain.vm.VmwareVmId
import de.acci.dvmm.domain.vm.events.VmProvisioningFailed
import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import de.acci.dvmm.domain.vm.events.VmProvisioned
import de.acci.dvmm.domain.vmrequest.VmRequestAggregate
import de.acci.dvmm.domain.vmrequest.events.VmRequestReady
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.notifications.EmailAddress
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Handles VmProvisioningStarted event by calling the hypervisor to create the VM.
 *
 * On success, emits VmProvisioned (VM aggregate) and VmRequestReady (VmRequest aggregate)
 * events, plus updates the timeline projection.
 *
 * On failure (missing config or hypervisor error), emits VmProvisioningFailed event
 * so downstream systems can react appropriately.
 *
 * ## Multi-Hypervisor Support (ADR-004)
 *
 * This handler uses [ResilientProvisioningService] which wraps [HypervisorPort] with
 * automatic retry logic for transient errors (AC-3.6.1). The service supports multiple
 * hypervisors (VMware vSphere, Proxmox, Hyper-V, PowerVM) without changing handler logic.
 */
public class TriggerProvisioningHandler(
    private val provisioningService: ResilientProvisioningService,
    private val configPort: VmwareConfigurationPort,
    private val eventStore: EventStore,
    private val vmEventDeserializer: VmEventDeserializer,
    private val vmRequestEventDeserializer: VmRequestEventDeserializer,
    private val timelineUpdater: TimelineEventProjectionUpdater,
    private val vmRequestReadRepository: VmRequestReadRepository,
    private val progressRepository: VmProvisioningProgressProjectionRepository,
    private val notificationSender: VmRequestNotificationSender,
    /** Admin email for provisioning failure alerts (AC-3.6.5). Null disables admin notifications. */
    private val adminNotificationEmail: String? = null
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

        // Get project name for prefixing (AC1: {projectPrefix}-{requestedName})
        val projectInfo = try {
             vmRequestReadRepository.findById(event.requestId)
        } catch (e: CancellationException) {
            throw e  // Allow proper coroutine cancellation
        } catch (e: Exception) {
            logger.error(e) { "Failed to load project info for request ${event.requestId.value}" }
            null
        }

        if (projectInfo == null) {
            val reason = "Could not find project info for request ${event.requestId.value}"
            logger.error { reason }
            emitFailure(event, reason)
            return
        }

        // Generate prefix from project name (uppercase, first 4 chars, safe for VM name)
        // Example: "My Project" -> "MYPR"
        val projectPrefix = projectInfo.projectName
            .filter { it.isLetterOrDigit() }
            .take(4)
            .uppercase()
            .ifBlank { "PROJ" }

        val prefixedVmName = "$projectPrefix-${event.vmName.value}"
        logger.info { "Provisioning VM with name: $prefixedVmName (Original: ${event.vmName.value}, Project: ${projectInfo.projectName})" }

        val spec = VmSpec(
            name = prefixedVmName,
            template = config.templateName,
            cpu = event.size.cpuCores,
            memoryGb = event.size.memoryGb
        )

        // CorrelationId is never null (auto-generated by EventMetadata.create())
        val correlationId = event.metadata.correlationId.value.toString()
        val result = provisioningService.createVmWithRetry(
            spec = spec,
            correlationId = correlationId
        ) { stage ->
            emitProgress(event, stage)
        }

        when (result) {
            is Result.Success -> {
                val provisioningResult = result.value
                logger.info {
                    "VM provisioning completed for $prefixedVmName. " +
                        "vCenter ID: ${provisioningResult.vmwareVmId.value}, " +
                        "IP: ${provisioningResult.ipAddress ?: "pending"}"
                }
                emitSuccess(event, provisioningResult, prefixedVmName)
            }
            is Result.Failure -> {
                val error = result.error
                logger.error { "Failed to provision $prefixedVmName: $error" }
                when (error) {
                    is ResilientProvisioningService.RetryExhaustedError -> emitFailure(event, error)
                    is VsphereError -> emitFailure(event, error)
                    else -> emitFailure(event, "Unexpected error: $error")
                }
            }
        }
    }

    private suspend fun emitProgress(event: VmProvisioningStarted, stage: VmProvisioningStage) {
        val vmId = event.aggregateId.value
        try {
            val vmEvents = eventStore.load(vmId)
            if (vmEvents.isEmpty()) {
                logger.error { "Cannot emit progress: VM aggregate $vmId not found" }
                return
            }

            val vmAggregate = VmAggregate.reconstitute(
                id = event.aggregateId,
                events = vmEvents.map { vmEventDeserializer.deserialize(it) }
            )

            vmAggregate.updateProgress(stage, event.metadata)

            val appendResult = eventStore.append(
                aggregateId = vmId,
                events = vmAggregate.uncommittedEvents,
                expectedVersion = vmEvents.size.toLong()
            )

            when (appendResult) {
                is Result.Success -> {
                    logger.debug { "Emitted progress $stage for VM $vmId" }
                    // Update projection with accumulated stage timestamps
                    try {
                        val now = Instant.now()

                        // Load existing projection to preserve accumulated timestamps
                        val existing = progressRepository.findByVmRequestId(
                            event.requestId,
                            event.metadata.tenantId
                        )

                        // Build accumulated timestamps: existing + current stage
                        val accumulatedTimestamps = (existing?.stageTimestamps ?: emptyMap()) + (stage to now)
                        val startedAt = existing?.startedAt ?: now
                        val estimatedRemaining = VmProvisioningProgressProjection.calculateEstimatedRemaining(stage)

                        progressRepository.save(
                            VmProvisioningProgressProjection(
                                vmRequestId = event.requestId,
                                stage = stage,
                                details = "Provisioning stage: ${stage.name.lowercase().replace('_', ' ')}",
                                startedAt = startedAt,
                                updatedAt = now,
                                stageTimestamps = accumulatedTimestamps,
                                estimatedRemainingSeconds = estimatedRemaining
                            ),
                            event.metadata.tenantId
                        )
                    } catch (e: CancellationException) {
                        throw e  // Allow proper coroutine cancellation
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to update progress projection for VM $vmId" }
                    }
                }
                is Result.Failure -> logger.error { "Failed to emit progress $stage: ${appendResult.error}" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
             logger.error(e) { "Failed to emit progress $stage for VM $vmId" }
        }
    }

    /**
     * Emits success events for VM and VmRequest aggregates, plus timeline update.
     *
     * **Note on partial failures:** This method performs 3 sequential updates without
     * distributed transaction guarantees. If step 1 (VmProvisioned) succeeds but step 2
     * (VmRequestReady) fails, the VM aggregate is updated but the VmRequest is not.
     * This is acceptable for eventual consistency - a retry mechanism or manual
     * reconciliation can fix the inconsistency. Detailed logging ensures traceability.
     */
    private suspend fun emitSuccess(
        event: VmProvisioningStarted, 
        provisioningResult: VmProvisioningResult,
        provisionedHostname: String
    ) {
        val vmId = event.aggregateId.value
        val requestId = event.requestId.value

        // Cleanup progress projection
        try {
            progressRepository.delete(event.requestId, event.metadata.tenantId)
        } catch (e: CancellationException) {
            throw e  // Allow proper coroutine cancellation
        } catch (e: Exception) {
            logger.warn(e) { "Failed to cleanup progress projection for request $requestId" }
        }

        // Step 1: Update VM aggregate with VmProvisioned event
        try {
            val vmEvents = eventStore.load(vmId)
            if (vmEvents.isEmpty()) {
                logger.error { "[Step 1/3] Cannot emit VmProvisioned: VM aggregate $vmId not found" }
                return
            }

            val vmAggregate = VmAggregate.reconstitute(
                id = event.aggregateId,
                events = vmEvents.map { vmEventDeserializer.deserialize(it) }
            )
            vmAggregate.markProvisioned(
                vmwareVmId = provisioningResult.vmwareVmId,
                ipAddress = provisioningResult.ipAddress,
                hostname = provisionedHostname, // Use the actual prefixed hostname
                warningMessage = provisioningResult.warningMessage,
                metadata = event.metadata
            )

            val vmAppendResult = eventStore.append(
                aggregateId = vmId,
                events = vmAggregate.uncommittedEvents,
                expectedVersion = vmEvents.size.toLong()
            )
            when (vmAppendResult) {
                is Result.Success -> logger.info { "[Step 1/3] Emitted VmProvisioned for VM $vmId" }
                is Result.Failure -> {
                    logger.error { "[Step 1/3] Failed to emit VmProvisioned: ${vmAppendResult.error}" }
                    return
                }
            }
        } catch (e: CancellationException) {
            throw e  // Allow proper coroutine cancellation
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "[Step 1/3] Invalid state transition for VM $vmId" }
            return
        } catch (e: IllegalStateException) {
            logger.error(e) { "[Step 1/3] Invalid aggregate state for VM $vmId" }
            return
        }

        // Step 2: Update VmRequest aggregate with VmRequestReady event
        try {
            val requestEvents = eventStore.load(requestId)
            if (requestEvents.isEmpty()) {
                logger.error { "[Step 2/3] Cannot emit VmRequestReady: request $requestId not found (VM $vmId was updated)" }
                return
            }

            val requestAggregate = VmRequestAggregate.reconstitute(
                id = event.requestId,
                events = requestEvents.map { vmRequestEventDeserializer.deserialize(it) }
            )
            requestAggregate.markReady(
                vmwareVmId = provisioningResult.vmwareVmId,
                ipAddress = provisioningResult.ipAddress,
                hostname = provisionedHostname, // Use the actual prefixed hostname
                provisionedAt = Instant.now(),
                warningMessage = provisioningResult.warningMessage,
                metadata = event.metadata
            )

            val requestAppendResult = eventStore.append(
                aggregateId = requestId,
                events = requestAggregate.uncommittedEvents,
                expectedVersion = requestEvents.size.toLong()
            )
            when (requestAppendResult) {
                is Result.Success -> logger.info { "[Step 2/3] Emitted VmRequestReady for request $requestId" }
                is Result.Failure -> {
                    logger.error {
                        "CRITICAL: [Step 2/3] Failed to emit VmRequestReady for request $requestId " +
                            "after VM $vmId was already marked provisioned. " +
                            "System may be in inconsistent state. Error: ${requestAppendResult.error}"
                    }
                    return
                }
            }
        } catch (e: CancellationException) {
            throw e  // Allow proper coroutine cancellation
        } catch (e: de.acci.dvmm.domain.exceptions.InvalidStateException) {
            logger.error(e) { "[Step 2/3] Invalid state transition for request $requestId (VM $vmId was updated)" }
            return
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "[Step 2/3] Invalid argument for request $requestId (VM $vmId was updated)" }
            return
        }

        // Step 3: Add timeline event for VM_READY
        try {
            val timelineResult = timelineUpdater.addTimelineEvent(
                NewTimelineEvent(
                    id = UUID.randomUUID(),
                    requestId = event.requestId,
                    tenantId = event.metadata.tenantId,
                    eventType = TimelineEventType.VM_READY,
                    actorId = null, // System event
                    actorName = "System",
                    details = buildReadyDetails(provisioningResult, provisionedHostname),
                    occurredAt = Instant.now()
                )
            )
            when (timelineResult) {
                is Result.Success -> logger.info { "[Step 3/3] Added VM_READY timeline event for request $requestId" }
                is Result.Failure -> logger.error { "[Step 3/3] Failed to add VM_READY timeline: ${timelineResult.error}" }
            }
        } catch (e: CancellationException) {
            throw e  // Allow proper coroutine cancellation
        } catch (e: IllegalArgumentException) {
            logger.error(e) { "[Step 3/3] Invalid timeline event for request $requestId" }
        }
    }

    private fun buildReadyDetails(result: VmProvisioningResult, hostname: String): String {
        val parts = mutableListOf<String>()
        parts.add("VM ID: ${result.vmwareVmId.value}")
        parts.add("Hostname: $hostname")
        result.ipAddress?.let { parts.add("IP: $it") }
        result.warningMessage?.let { parts.add("Warning: $it") }
        return parts.joinToString(", ")
    }

    /**
     * Emits failure event for retry exhaustion (AC-3.6.2).
     *
     * Called when all retry attempts have been exhausted. Extracts actual
     * retry count from the RetryExhaustedError for accurate error reporting.
     */
    private suspend fun emitFailure(
        event: VmProvisioningStarted,
        error: ResilientProvisioningService.RetryExhaustedError
    ) {
        emitFailureInternal(
            event = event,
            reason = error.userMessage,
            errorCode = error.lastErrorCode.name,
            errorMessage = error.userMessage,
            retryCount = error.attemptCount,
            lastAttemptAt = Instant.now()
        )
    }

    /**
     * Emits failure event for permanent VsphereError (AC-3.6.3).
     *
     * Called for non-retriable errors that fail immediately without retry.
     * Extracts user-friendly error code and message from the VsphereError type.
     */
    private suspend fun emitFailure(event: VmProvisioningStarted, error: VsphereError) {
        val errorCode = error.errorCode.name
        val userMessage = error.userMessage
        // Permanent errors fail immediately without retry (retryCount=1 means single attempt)
        val retryCount = 1
        val lastAttemptAt = Instant.now()

        emitFailureInternal(
            event = event,
            reason = userMessage,
            errorCode = errorCode,
            errorMessage = userMessage,
            retryCount = retryCount,
            lastAttemptAt = lastAttemptAt
        )
    }

    /**
     * Emits failure event with a simple reason string (for non-vSphere errors).
     */
    private suspend fun emitFailure(event: VmProvisioningStarted, reason: String) {
        emitFailureInternal(
            event = event,
            reason = reason,
            errorCode = "UNKNOWN",
            errorMessage = reason,
            retryCount = 1,
            lastAttemptAt = Instant.now()
        )
    }

    private suspend fun emitFailureInternal(
        event: VmProvisioningStarted,
        reason: String,
        errorCode: String,
        errorMessage: String,
        retryCount: Int,
        lastAttemptAt: Instant
    ) {
        val failedEvent = VmProvisioningFailed(
            aggregateId = event.aggregateId,
            requestId = event.requestId,
            reason = reason,
            errorCode = errorCode,
            errorMessage = errorMessage,
            retryCount = retryCount,
            lastAttemptAt = lastAttemptAt,
            metadata = event.metadata
        )

        // Cleanup progress projection
        try {
            progressRepository.delete(event.requestId, event.metadata.tenantId)
        } catch (e: CancellationException) {
            throw e  // Allow proper coroutine cancellation
        } catch (e: Exception) {
            logger.warn(e) { "Failed to cleanup progress projection for request ${event.requestId.value}" }
        }

        try {
            // Step 1: Load current version to handle potential concurrent modifications
            val currentEvents = eventStore.load(event.aggregateId.value)
            if (currentEvents.isEmpty()) {
                logger.error { "[Step 1/4] Cannot emit VmProvisioningFailed: aggregate ${event.aggregateId.value} not found in event store" }
                return
            }
            val currentVersion = currentEvents.size.toLong()

            val result = eventStore.append(
                aggregateId = event.aggregateId.value,
                events = listOf(failedEvent),
                expectedVersion = currentVersion
            )
            when (result) {
                is Result.Success -> logger.info { "[Step 1/4] Emitted VmProvisioningFailed for VM ${event.aggregateId.value}" }
                is Result.Failure -> {
                    logger.error { "[Step 1/4] Failed to emit VmProvisioningFailed: ${result.error}" }
                    return
                }
            }
        } catch (e: CancellationException) {
            // Rethrow CancellationException to allow proper coroutine cancellation
            throw e
        } catch (e: Exception) {
            logger.error(e) { "[Step 1/4] Failed to emit VmProvisioningFailed event for VM ${event.aggregateId.value}" }
            return
        }

        // Step 2: Add timeline event for PROVISIONING_FAILED
        try {
            val timelineResult = timelineUpdater.addTimelineEvent(
                NewTimelineEvent(
                    id = UUID.randomUUID(),
                    requestId = event.requestId,
                    tenantId = event.metadata.tenantId,
                    eventType = TimelineEventType.PROVISIONING_FAILED,
                    actorId = null, // System event
                    actorName = "System",
                    details = reason,
                    occurredAt = Instant.now()
                )
            )
            when (timelineResult) {
                is Result.Success -> logger.info { "[Step 2/4] Added PROVISIONING_FAILED timeline event for request ${event.requestId.value}" }
                is Result.Failure -> logger.error {
                    "CRITICAL: [Step 2/4] Failed to add PROVISIONING_FAILED timeline for request ${event.requestId.value} " +
                        "after VM ${event.aggregateId.value} was already marked failed. " +
                        "Timeline is now inconsistent - users will not see the failure event. Error: ${timelineResult.error}"
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) {
                "CRITICAL: [Step 2/4] Failed to add PROVISIONING_FAILED timeline for request ${event.requestId.value} " +
                    "after VM ${event.aggregateId.value} was already marked failed. " +
                    "Timeline is now inconsistent - users will not see the failure event."
            }
        }

        // Step 3 & 4: Send failure notifications (AC-3.6.5)
        sendFailureNotifications(
            event = event,
            errorCode = errorCode,
            errorMessage = errorMessage,
            retryCount = retryCount
        )
    }

    /**
     * Sends provisioning failure notifications to user and admin (AC-3.6.5).
     *
     * - User notification: user-friendly error message
     * - Admin notification: technical details for troubleshooting
     *
     * Notification failures are logged but do not fail the overall operation.
     */
    private suspend fun sendFailureNotifications(
        event: VmProvisioningStarted,
        errorCode: String,
        errorMessage: String,
        retryCount: Int
    ) {
        // Load request details for notification context
        val requestDetails = try {
            vmRequestReadRepository.findById(event.requestId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "[Step 3/4] Failed to load request details for notifications, skipping: ${e::class.simpleName}" }
            return
        }

        if (requestDetails == null) {
            logger.warn { "[Step 3/4] Request ${event.requestId.value} not found, skipping notifications" }
            return
        }

        val requesterEmail = requestDetails.requesterEmail
        if (requesterEmail == null) {
            logger.warn { "[Step 3/4] No requester email for request ${event.requestId.value}, skipping user notification" }
        } else {
            // Step 3: Send user notification with user-friendly error
            try {
                val userNotification = ProvisioningFailedUserNotification(
                    requestId = event.requestId,
                    tenantId = event.metadata.tenantId,
                    requesterEmail = EmailAddress.of(requesterEmail),
                    vmName = requestDetails.vmName,
                    projectName = requestDetails.projectName,
                    errorMessage = errorMessage,
                    errorCode = errorCode
                )

                val userResult = notificationSender.sendProvisioningFailedUserNotification(userNotification)
                when (userResult) {
                    is Result.Success -> logger.info {
                        "[Step 3/4] Sent provisioning failed notification to user $requesterEmail"
                    }
                    is Result.Failure -> logger.logNotificationError(
                        notificationError = userResult.error,
                        requestId = event.requestId,
                        correlationId = event.metadata.correlationId,
                        action = "ProvisioningFailed (user)"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "[Step 3/4] Failed to send user notification for request ${event.requestId.value}" }
            }
        }

        // Step 4: Send admin notification with technical details
        if (adminNotificationEmail == null) {
            logger.debug { "[Step 4/4] Admin notification email not configured, skipping admin notification" }
            return
        }

        try {
            val adminNotification = ProvisioningFailedAdminNotification(
                requestId = event.requestId,
                tenantId = event.metadata.tenantId,
                adminEmail = EmailAddress.of(adminNotificationEmail),
                vmName = requestDetails.vmName,
                projectName = requestDetails.projectName,
                errorMessage = errorMessage,
                errorCode = errorCode,
                retryCount = retryCount,
                correlationId = event.metadata.correlationId,
                requesterEmail = requesterEmail ?: requestDetails.requesterName
            )

            val adminResult = notificationSender.sendProvisioningFailedAdminNotification(adminNotification)
            when (adminResult) {
                is Result.Success -> logger.info {
                    "[Step 4/4] Sent provisioning failed notification to admin $adminNotificationEmail"
                }
                is Result.Failure -> logger.logNotificationError(
                    notificationError = adminResult.error,
                    requestId = event.requestId,
                    correlationId = event.metadata.correlationId,
                    action = "ProvisioningFailed (admin)"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "[Step 4/4] Failed to send admin notification for request ${event.requestId.value}" }
        }
    }
}
