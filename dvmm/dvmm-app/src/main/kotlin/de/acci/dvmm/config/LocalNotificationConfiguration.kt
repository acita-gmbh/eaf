package de.acci.dvmm.config

import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.notifications.EmailAddress
import de.acci.eaf.notifications.NotificationError
import de.acci.eaf.notifications.NotificationService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Fallback notification configuration for local development and CI environments.
 *
 * ## Why This Exists
 *
 * The EAF `NotificationService` is auto-configured only when both `JavaMailSender` and
 * `ITemplateEngine` beans exist (i.e., when SMTP is configured). In local development
 * and CI environments without SMTP, no `NotificationService` bean is created.
 *
 * However, `VmRequestNotificationSenderAdapter` (a `@Component`) requires `NotificationService`,
 * causing application startup failure with:
 * ```
 * No qualifying bean of type 'de.acci.eaf.notifications.NotificationService' available
 * ```
 *
 * This configuration provides a fallback logging-only implementation that is only used
 * when no real `NotificationService` is configured (via `@ConditionalOnMissingBean`).
 *
 * ## In Production
 *
 * Configure SMTP settings to enable the real `NotificationService`:
 * ```yaml
 * spring:
 *   mail:
 *     host: smtp.example.com
 *     port: 587
 *     username: notifications@example.com
 *     password: ${SMTP_PASSWORD}
 * ```
 *
 * @see de.acci.eaf.notifications.EafNotificationAutoConfiguration
 */
@Configuration
public class LocalNotificationConfiguration {

    private val logger = KotlinLogging.logger {}

    /**
     * Fallback NotificationService that logs email content instead of sending.
     *
     * Only created when no real NotificationService bean exists (i.e., no SMTP configured).
     */
    @Bean
    @ConditionalOnMissingBean(NotificationService::class)
    public fun loggingNotificationService(): NotificationService {
        logger.warn {
            "No SMTP configured - using logging-only NotificationService. " +
                "Emails will NOT be sent. Configure spring.mail.* to enable real email sending."
        }
        return LoggingNotificationService()
    }

    /**
     * NotificationService implementation that logs email content for local development.
     *
     * Useful for verifying email content and template rendering without SMTP.
     */
    private class LoggingNotificationService : NotificationService {
        private val logger = KotlinLogging.logger {}

        override suspend fun sendEmail(
            tenantId: TenantId,
            recipient: EmailAddress,
            subject: String,
            templateName: String,
            context: Map<String, Any>
        ): Result<Unit, NotificationError> {
            logger.info {
                """
                |╔════════════════════════════════════════════════════════════════════
                |║ [LOCAL] Email would be sent (SMTP not configured)
                |╠════════════════════════════════════════════════════════════════════
                |║ To: ${recipient.value}
                |║ Subject: $subject
                |║ Template: $templateName
                |║ Tenant: ${tenantId.value}
                |║ Context: $context
                |╚════════════════════════════════════════════════════════════════════
                """.trimMargin()
            }
            return Unit.success()
        }
    }
}
