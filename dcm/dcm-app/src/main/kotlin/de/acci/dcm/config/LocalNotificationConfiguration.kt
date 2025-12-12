package de.acci.dcm.config

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

    /**
     * Fallback NotificationService that logs email content instead of sending.
     *
     * Only created when no real NotificationService bean exists (i.e., no SMTP configured).
     */
    @Bean
    @ConditionalOnMissingBean(NotificationService::class)
    public fun loggingNotificationService(): NotificationService = LoggingNotificationService()

    /**
     * NotificationService implementation that logs email content for local development.
     *
     * Useful for verifying email content and template rendering without SMTP.
     * Logs a startup warning and then logs each email that would be sent.
     */
    private class LoggingNotificationService : NotificationService {
        private val logger = KotlinLogging.logger {}

        init {
            logger.warn {
                "No SMTP configured - using logging-only NotificationService. " +
                    "Emails will NOT be sent. Configure spring.mail.* to enable real email sending."
            }
        }

        override suspend fun sendEmail(
            tenantId: TenantId,
            recipient: EmailAddress,
            subject: String,
            templateName: String,
            context: Map<String, Any>
        ): Result<Unit, NotificationError> {
            val sanitizedContext = sanitizeContext(context)
            logger.info {
                """
                |╔════════════════════════════════════════════════════════════════════
                |║ [LOCAL] Email would be sent (SMTP not configured)
                |╠════════════════════════════════════════════════════════════════════
                |║ To: ${recipient.value}
                |║ Subject: $subject
                |║ Template: $templateName
                |║ Tenant: ${tenantId.value}
                |║ Context: $sanitizedContext
                |╚════════════════════════════════════════════════════════════════════
                """.trimMargin()
            }
            return Unit.success()
        }

        /**
         * Sanitize context map by redacting values for sensitive keys.
         *
         * Filters out keys containing common sensitive patterns (password, token, secret, etc.)
         * to prevent accidental exposure in logs, even in local/CI environments.
         */
        private fun sanitizeContext(context: Map<String, Any>): Map<String, Any> {
            return context.mapValues { (key, value) ->
                if (SENSITIVE_KEY_PATTERNS.any { pattern -> key.lowercase().contains(pattern) }) {
                    "[REDACTED]"
                } else {
                    value
                }
            }
        }

        private companion object {
            /** Patterns for sensitive keys that should be redacted from logs */
            val SENSITIVE_KEY_PATTERNS = listOf(
                "password",
                "token",
                "secret",
                "credential",
                "apikey",
                "api_key"
            )
        }
    }
}
