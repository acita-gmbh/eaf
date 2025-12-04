package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId

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
    val requesterEmail: String,
    val vmName: String,
    val projectName: String
)

/**
 * Data for sending a "request approved" notification.
 */
public data class RequestApprovedNotification(
    val requestId: VmRequestId,
    val tenantId: TenantId,
    val requesterEmail: String,
    val vmName: String,
    val projectName: String
)

/**
 * Data for sending a "request rejected" notification.
 */
public data class RequestRejectedNotification(
    val requestId: VmRequestId,
    val tenantId: TenantId,
    val requesterEmail: String,
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
 *         requesterEmail = "user@example.com",
 *         vmName = "web-server-01",
 *         projectName = "Production"
 *     )
 * ).onFailure { error ->
 *     logger.warn { "Notification failed: ${error.message}" }
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
 */
public object NoOpVmRequestNotificationSender : VmRequestNotificationSender {
    override suspend fun sendCreatedNotification(
        notification: RequestCreatedNotification
    ): Result<Unit, VmRequestNotificationError> = Result.Success(Unit)

    override suspend fun sendApprovedNotification(
        notification: RequestApprovedNotification
    ): Result<Unit, VmRequestNotificationError> = Result.Success(Unit)

    override suspend fun sendRejectedNotification(
        notification: RequestRejectedNotification
    ): Result<Unit, VmRequestNotificationError> = Result.Success(Unit)
}
