package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.notifications.EmailAddress
import io.github.oshai.kotlinlogging.KLogger

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
}

/**
 * No-op implementation for use when notifications are disabled.
 *
 * Logs at DEBUG level when notifications are skipped so administrators
 * can verify the configuration is intentional.
 */
public object NoOpVmRequestNotificationSender : VmRequestNotificationSender {
    private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}

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
}

/**
 * Logs notification errors with consistent formatting across all handlers.
 *
 * @param error The notification error that occurred
 * @param requestId The VM request ID for context
 * @param correlationId The correlation ID for distributed tracing
 * @param action The action type (e.g., "Creation", "Approval", "Rejection")
 */
internal fun KLogger.logNotificationError(
    error: VmRequestNotificationError,
    requestId: VmRequestId,
    correlationId: CorrelationId,
    action: String
) {
    when (error) {
        is VmRequestNotificationError.SendFailure -> {
            error {
                "$action notification send failure for request ${requestId.value}: ${error.message}. " +
                    "correlationId=${correlationId.value}"
            }
        }
        is VmRequestNotificationError.TemplateError -> {
            error {
                "$action notification template error for request ${requestId.value}: " +
                    "template=${error.templateName}, message=${error.message}. " +
                    "correlationId=${correlationId.value}"
            }
        }
    }
}
