package de.acci.dvmm.infrastructure.notification

import de.acci.dvmm.application.vmrequest.RequestApprovedNotification
import de.acci.dvmm.application.vmrequest.RequestCreatedNotification
import de.acci.dvmm.application.vmrequest.RequestRejectedNotification
import de.acci.dvmm.application.vmrequest.VmRequestNotificationError
import de.acci.dvmm.application.vmrequest.VmRequestNotificationSender
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.notifications.EmailAddress
import de.acci.eaf.notifications.NotificationError
import de.acci.eaf.notifications.NotificationService
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Infrastructure adapter that sends VM request notifications using the EAF notification service.
 *
 * Maps application-layer notification requests to infrastructure-layer email sending,
 * using Thymeleaf templates for email body rendering.
 *
 * ## Templates
 *
 * Templates must be placed in `classpath:/templates/email/` directory:
 * - `vm-request-created.html` - Sent when user creates a request
 * - `vm-request-approved.html` - Sent when admin approves a request
 * - `vm-request-rejected.html` - Sent when admin rejects a request
 *
 * ## Template Variables
 *
 * All templates receive:
 * - `requestId` - The VM request ID
 * - `vmName` - The requested VM name
 * - `projectName` - The project name
 *
 * Rejection template additionally receives:
 * - `reason` - The rejection reason
 *
 * ## Error Handling
 *
 * Email failures are converted to [VmRequestNotificationError] and returned.
 * Callers should log errors but not fail the parent command.
 */
public class VmRequestNotificationSenderAdapter(
    private val notificationService: NotificationService
) : VmRequestNotificationSender {

    private val logger = KotlinLogging.logger {}

    override suspend fun sendCreatedNotification(
        notification: RequestCreatedNotification
    ): Result<Unit, VmRequestNotificationError> {
        val recipient = EmailAddress.ofOrNull(notification.requesterEmail)
            ?: return VmRequestNotificationError.SendFailure(
                message = "Invalid email address: ${notification.requesterEmail}"
            ).failure()

        val context = mapOf(
            "requestId" to notification.requestId.value.toString(),
            "vmName" to notification.vmName,
            "projectName" to notification.projectName
        )

        logger.debug {
            "Sending request created notification: " +
                "requestId=${notification.requestId.value}, " +
                "to=${recipient.value}"
        }

        return notificationService.sendEmail(
            tenantId = notification.tenantId,
            recipient = recipient,
            subject = "VM Request Created: ${notification.vmName}",
            templateName = TEMPLATE_CREATED,
            context = context
        ).mapToNotificationError(TEMPLATE_CREATED)
    }

    override suspend fun sendApprovedNotification(
        notification: RequestApprovedNotification
    ): Result<Unit, VmRequestNotificationError> {
        val recipient = EmailAddress.ofOrNull(notification.requesterEmail)
            ?: return VmRequestNotificationError.SendFailure(
                message = "Invalid email address: ${notification.requesterEmail}"
            ).failure()

        val context = mapOf(
            "requestId" to notification.requestId.value.toString(),
            "vmName" to notification.vmName,
            "projectName" to notification.projectName
        )

        logger.debug {
            "Sending request approved notification: " +
                "requestId=${notification.requestId.value}, " +
                "to=${recipient.value}"
        }

        return notificationService.sendEmail(
            tenantId = notification.tenantId,
            recipient = recipient,
            subject = "VM Request Approved: ${notification.vmName}",
            templateName = TEMPLATE_APPROVED,
            context = context
        ).mapToNotificationError(TEMPLATE_APPROVED)
    }

    override suspend fun sendRejectedNotification(
        notification: RequestRejectedNotification
    ): Result<Unit, VmRequestNotificationError> {
        val recipient = EmailAddress.ofOrNull(notification.requesterEmail)
            ?: return VmRequestNotificationError.SendFailure(
                message = "Invalid email address: ${notification.requesterEmail}"
            ).failure()

        val context = mapOf(
            "requestId" to notification.requestId.value.toString(),
            "vmName" to notification.vmName,
            "projectName" to notification.projectName,
            "reason" to notification.reason
        )

        logger.debug {
            "Sending request rejected notification: " +
                "requestId=${notification.requestId.value}, " +
                "to=${recipient.value}"
        }

        return notificationService.sendEmail(
            tenantId = notification.tenantId,
            recipient = recipient,
            subject = "VM Request Rejected: ${notification.vmName}",
            templateName = TEMPLATE_REJECTED,
            context = context
        ).mapToNotificationError(TEMPLATE_REJECTED)
    }

    /**
     * Map NotificationService errors to VmRequestNotificationError.
     */
    private fun Result<Unit, NotificationError>.mapToNotificationError(
        templateName: String
    ): Result<Unit, VmRequestNotificationError> {
        return when (this) {
            is Result.Success -> Unit.success()
            is Result.Failure -> when (val err = error) {
                is NotificationError.TemplateError -> VmRequestNotificationError.TemplateError(
                    templateName = templateName,
                    message = err.message
                ).failure()
                is NotificationError.ConnectionError -> VmRequestNotificationError.SendFailure(
                    message = "SMTP connection failed: ${err.message}"
                ).failure()
                is NotificationError.InvalidRecipient -> VmRequestNotificationError.SendFailure(
                    message = "Invalid recipient: ${err.message}"
                ).failure()
                is NotificationError.SendFailure -> VmRequestNotificationError.SendFailure(
                    message = err.message
                ).failure()
                is NotificationError.ConfigurationMissing -> VmRequestNotificationError.SendFailure(
                    message = err.message
                ).failure()
            }
        }
    }

    private companion object {
        const val TEMPLATE_CREATED = "vm-request-created"
        const val TEMPLATE_APPROVED = "vm-request-approved"
        const val TEMPLATE_REJECTED = "vm-request-rejected"
    }
}
