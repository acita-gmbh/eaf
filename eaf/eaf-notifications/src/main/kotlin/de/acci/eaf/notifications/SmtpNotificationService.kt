package de.acci.eaf.notifications

import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.MessagingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper

private val logger = KotlinLogging.logger {}

/**
 * SMTP-based notification service using Spring's JavaMailSender.
 *
 * For MVP: Uses system-default SMTP configuration (spring.mail.* properties).
 * Future: Per-tenant SMTP configuration via TenantId lookup.
 *
 * @property mailSender Spring's JavaMailSender configured via properties
 * @property templateEngine Template engine for rendering email content
 * @property fromAddress Default sender address
 */
public class SmtpNotificationService(
    private val mailSender: JavaMailSender,
    private val templateEngine: TemplateEngine,
    private val fromAddress: EmailAddress
) : NotificationService {

    override suspend fun sendEmail(
        tenantId: TenantId,
        recipient: EmailAddress,
        subject: String,
        templateName: String,
        context: Map<String, Any>
    ): Result<Unit, NotificationError> {
        logger.debug {
            "Sending email: template=$templateName, to=$recipient, tenant=${tenantId.value}"
        }

        return when (val renderResult = templateEngine.render(templateName, context)) {
            is Result.Success -> sendMimeMessage(
                recipient = recipient,
                subject = subject,
                htmlContent = renderResult.value
            )
            is Result.Failure -> renderResult
        }
    }

    private suspend fun sendMimeMessage(
        recipient: EmailAddress,
        subject: String,
        htmlContent: String
    ): Result<Unit, NotificationError> = withContext(Dispatchers.IO) {
        try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            helper.setFrom(fromAddress.value)
            helper.setTo(recipient.value)
            helper.setSubject(subject)
            helper.setText(htmlContent, true)

            mailSender.send(message)

            logger.info { "Email sent successfully: to=$recipient, subject=$subject" }
            Unit.success()
        } catch (e: MailAuthenticationException) {
            logger.error(e) { "SMTP authentication failed" }
            NotificationError.ConnectionError(
                message = "SMTP authentication failed: ${e.message}"
            ).failure()
        } catch (e: MailSendException) {
            logger.error(e) { "Failed to send email to $recipient" }
            when {
                e.message?.contains("Invalid Addresses") == true ->
                    NotificationError.InvalidRecipient(
                        recipient = recipient,
                        message = "Invalid recipient address: ${e.message}"
                    ).failure()
                else ->
                    NotificationError.SendFailure(
                        message = "Failed to send email: ${e.message}"
                    ).failure()
            }
        } catch (e: MessagingException) {
            logger.error(e) { "Messaging error sending email: to=$recipient, subject=$subject" }
            NotificationError.SendFailure(
                message = "Messaging error: ${e.message}"
            ).failure()
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error sending email: to=$recipient, subject=$subject" }
            NotificationError.SendFailure(
                message = "Unexpected error: ${e.message}"
            ).failure()
        }
    }
}
