package de.acci.eaf.notifications

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.mail.javamail.JavaMailSender
import org.thymeleaf.ITemplateEngine

/**
 * Configuration properties for EAF notifications.
 */
@ConfigurationProperties(prefix = "eaf.notifications")
public data class EafNotificationProperties(
    /** Sender email address for notifications. */
    val fromAddress: String = "noreply@example.com",

    /** Whether email notifications are enabled. */
    val enabled: Boolean = true
)

/**
 * Auto-configuration for EAF notification services.
 *
 * Requires:
 * - JavaMailSender bean (from spring-boot-starter-mail)
 * - Thymeleaf ITemplateEngine bean (from spring-boot-starter-thymeleaf)
 *
 * Can be disabled with `eaf.notifications.enabled=false`.
 */
@AutoConfiguration
@EnableConfigurationProperties(EafNotificationProperties::class)
@ConditionalOnProperty(
    prefix = "eaf.notifications",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
public class EafNotificationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ITemplateEngine::class)
    public fun templateEngine(thymeleafEngine: ITemplateEngine): TemplateEngine =
        ThymeleafEmailTemplateEngine(thymeleafEngine)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JavaMailSender::class, TemplateEngine::class)
    public fun notificationService(
        mailSender: JavaMailSender,
        templateEngine: TemplateEngine,
        properties: EafNotificationProperties
    ): NotificationService {
        val fromAddress = try {
            EmailAddress.of(properties.fromAddress)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException(
                "Invalid email address configured for 'eaf.notifications.from-address': " +
                    "'${properties.fromAddress}'. Please provide a valid email address.",
                e
            )
        }
        return SmtpNotificationService(
            mailSender = mailSender,
            templateEngine = templateEngine,
            fromAddress = fromAddress
        )
    }
}
