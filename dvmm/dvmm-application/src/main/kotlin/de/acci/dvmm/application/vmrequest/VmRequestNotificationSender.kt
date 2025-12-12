package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.notifications.EmailAddress
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Errors that can occur when sending VM request notifications.
 *
 * Notification failures are logged but do not fail the command.
 * Failed notifications can be retried through a dead-letter queue (future).
 */
public sealed interface VmRequestNotificationError {
    /** Descriptive message for logging. */
    public val message: String

    /**
     * Email sending failed (SMTP error, invalid recipient, etc.).
     */
    public data class SendFailure(
        override val message: String
    ) : VmRequestNotificationError

    /**
     * Template rendering failed (template not found, missing variables).
     */
    public data class TemplateError(
        public val templateName: String,
        override val message: String
    ) : VmRequestNotificationError
}

/**
 * Data for sending a "request created" notification.
 */
public data class RequestCreatedNotification(
    val requestId: VmRequestId,
    val tenantId: TenantId,
    val requesterEmail: EmailAddress,
    val vmName: String,
    val projectName: String
)

/**
 * Data for sending a "request approved" notification.
 */
public data class RequestApprovedNotification(
    val requestId: VmRequestId,
    val tenantId: TenantId,
    val requesterEmail: EmailAddress,
    val vmName: String,
    val projectName: String
)

/**
 * Data for sending a "request rejected" notification.
 */
public data class RequestRejectedNotification(
    val requestId: VmRequestId,
    val tenantId: TenantId,
    val requesterEmail: EmailAddress,
    val vmName: String,
    val projectName: String,
    val reason: String
)

/**
 * Data for sending a "provisioning failed" notification to the requester.
 * Contains user-friendly error information (AC-3.6.5).
 */
public data class ProvisioningFailedUserNotification(
    val requestId: VmRequestId,
    val tenantId: TenantId,
    val requesterEmail: EmailAddress,
    val vmName: String,
    val projectName: String,
    /** User-friendly error message suitable for display */
    val errorMessage: String,
    /** Machine-readable error code for categorization */
    val errorCode: String
)

/**
 * Data for sending a "provisioning failed" notification to admins.
 * Contains full technical details for troubleshooting (AC-3.6.5).
 */
public data class ProvisioningFailedAdminNotification(
    val requestId: VmRequestId,
    val tenantId: TenantId,
    val adminEmail: EmailAddress,
    val vmName: String,
    val projectName: String,
    /** User-friendly error message */
    val errorMessage: String,
    /** Machine-readable error code */
    val errorCode: String,
    /** Number of retry attempts before final failure */
    val retryCount: Int,
    /** Correlation ID for distributed tracing */
    val correlationId: CorrelationId,
    /** Requester's email for context */
    val requesterEmail: String
)

/**
 * Data for sending a "VM ready" notification to the requester (AC-3.8.1).
 * Sent when VM provisioning completes successfully.
 */
public data class VmReadyNotification(
    val requestId: VmRequestId,
    val tenantId: TenantId,
    val requesterEmail: EmailAddress,
    val vmName: String,
    val projectName: String,
    /** IP address of the provisioned VM, null if not yet assigned */
    val ipAddress: String?,
    /** Hostname of the provisioned VM */
    val hostname: String,
    /** Guest operating system (currently unused; reserved for future connection command generation) */
    val guestOs: String?,
    /** Time taken to provision the VM in minutes */
    val provisioningDurationMinutes: Long,
    /** Link to view VM details in the portal */
    val portalLink: String
) {
    init {
        require(vmName.trim().isNotBlank()) { "VM name must not be blank" }
        require(projectName.trim().isNotBlank()) { "Project name must not be blank" }
        require(hostname.trim().isNotBlank()) { "Hostname must not be blank" }
        require(provisioningDurationMinutes >= 0) { "Provisioning duration cannot be negative" }
        require(portalLink.trim().isNotBlank()) { "Portal link must not be blank" }
    }
}

/**
 * Interface for sending VM request notifications.
 *
 * Implementations send email notifications to users when their
 * VM requests change status (created, approved, rejected).
 *
 * ## Error Handling
 *
 * Notification failures are returned as [Result.Failure] but should not
 * fail the parent command. Callers should log the error and continue.
 * Failed notifications can be retried via dead-letter queue (future Epic).
 *
 * ## Usage
 *
 * ```kotlin
 * notificationSender.sendCreatedNotification(
 *     RequestCreatedNotification(
 *         requestId = aggregate.id,
 *         tenantId = tenantId,
 *         requesterEmail = EmailAddress.of("user@example.com"),
 *         vmName = "web-server-01",
 *         projectName = "Production"
 *     )
 * ).onFailure { error ->
 *     logger.error { "Notification failed: ${error.message}" }
 * }
 * ```
 */
public interface VmRequestNotificationSender {

    /**
     * Send notification when a VM request is created.
     */
    public suspend fun sendCreatedNotification(
        notification: RequestCreatedNotification
    ): Result<Unit, VmRequestNotificationError>

    /**
     * Send notification when a VM request is approved.
     */
    public suspend fun sendApprovedNotification(
        notification: RequestApprovedNotification
    ): Result<Unit, VmRequestNotificationError>

    /**
     * Send notification when a VM request is rejected.
     */
    public suspend fun sendRejectedNotification(
        notification: RequestRejectedNotification
    ): Result<Unit, VmRequestNotificationError>

    /**
     * Send notification to user when VM provisioning fails (AC-3.6.5).
     *
     * Contains user-friendly error summary and suggested actions.
     */
    public suspend fun sendProvisioningFailedUserNotification(
        notification: ProvisioningFailedUserNotification
    ): Result<Unit, VmRequestNotificationError>

    /**
     * Send notification to admin when VM provisioning fails (AC-3.6.5).
     *
     * Contains full technical details for troubleshooting.
     */
    public suspend fun sendProvisioningFailedAdminNotification(
        notification: ProvisioningFailedAdminNotification
    ): Result<Unit, VmRequestNotificationError>

    /**
     * Send notification to user when VM provisioning succeeds (AC-3.8.1).
     *
     * Contains VM details, connection instructions, and portal link.
     */
    public suspend fun sendVmReadyNotification(
        notification: VmReadyNotification
    ): Result<Unit, VmRequestNotificationError>
}

/**
 * No-op implementation for use when notifications are disabled.
 *
 * Logs at DEBUG level when notifications are skipped so administrators
 * can verify the configuration is intentional.
 */
public object NoOpVmRequestNotificationSender : VmRequestNotificationSender {
    private val logger = KotlinLogging.logger {}

    override suspend fun sendCreatedNotification(
        notification: RequestCreatedNotification
    ): Result<Unit, VmRequestNotificationError> {
        logger.debug {
            "Notifications disabled - skipping 'created' notification for request ${notification.requestId.value}"
        }
        return Result.Success(Unit)
    }

    override suspend fun sendApprovedNotification(
        notification: RequestApprovedNotification
    ): Result<Unit, VmRequestNotificationError> {
        logger.debug {
            "Notifications disabled - skipping 'approved' notification for request ${notification.requestId.value}"
        }
        return Result.Success(Unit)
    }

    override suspend fun sendRejectedNotification(
        notification: RequestRejectedNotification
    ): Result<Unit, VmRequestNotificationError> {
        logger.debug {
            "Notifications disabled - skipping 'rejected' notification for request ${notification.requestId.value}"
        }
        return Result.Success(Unit)
    }

    override suspend fun sendProvisioningFailedUserNotification(
        notification: ProvisioningFailedUserNotification
    ): Result<Unit, VmRequestNotificationError> {
        logger.debug {
            "Notifications disabled - skipping 'provisioning failed (user)' notification for request ${notification.requestId.value}"
        }
        return Result.Success(Unit)
    }

    override suspend fun sendProvisioningFailedAdminNotification(
        notification: ProvisioningFailedAdminNotification
    ): Result<Unit, VmRequestNotificationError> {
        logger.debug {
            "Notifications disabled - skipping 'provisioning failed (admin)' notification for request ${notification.requestId.value}"
        }
        return Result.Success(Unit)
    }

    override suspend fun sendVmReadyNotification(
        notification: VmReadyNotification
    ): Result<Unit, VmRequestNotificationError> {
        logger.debug {
            "Notifications disabled - skipping 'VM ready' notification for request ${notification.requestId.value}"
        }
        return Result.Success(Unit)
    }
}

/**
 * Logs notification errors with consistent formatting across all handlers.
 *
 * @param notificationError The notification error that occurred
 * @param requestId The VM request ID for context
 * @param correlationId The correlation ID for distributed tracing
 * @param action The action type (e.g., "Creation", "Approval", "Rejection")
 */
internal fun KLogger.logNotificationError(
    notificationError: VmRequestNotificationError,
    requestId: VmRequestId,
    correlationId: CorrelationId,
    action: String
) {
    when (notificationError) {
        is VmRequestNotificationError.SendFailure -> {
            error {
                "$action notification send failure for request ${requestId.value}: ${notificationError.message}. " +
                    "correlationId=${correlationId.value}"
            }
        }
        is VmRequestNotificationError.TemplateError -> {
            error {
                "$action notification template error for request ${requestId.value}: " +
                    "template=${notificationError.templateName}, message=${notificationError.message}. " +
                    "correlationId=${correlationId.value}"
            }
        }
    }
}
