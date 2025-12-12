package de.acci.dcm

import de.acci.eaf.notifications.NotificationService
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Shared test configuration that provides mocks for notification infrastructure.
 *
 * This configuration is activated for the "test" profile and provides mock beans
 * for components that depend on external services (SMTP, etc.) that aren't available
 * during tests.
 *
 * ## Why This Exists
 *
 * `VmRequestNotificationSenderAdapter` is a `@Component` that depends on `NotificationService`.
 * `NotificationService` is auto-configured by `EafNotificationAutoConfiguration` only when
 * both `JavaMailSender` and `ITemplateEngine` beans exist. In tests without mail configuration,
 * these beans don't exist, so we need to provide a mock.
 *
 * ## Usage
 *
 * Import this configuration in test classes:
 * ```kotlin
 * @SpringBootTest(
 *     classes = [DcmApplication::class, TestNotificationConfiguration::class],
 *     webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
 * )
 * ```
 *
 * Or use `@Import(TestNotificationConfiguration::class)`.
 */
@TestConfiguration
public class TestNotificationConfiguration {

    /**
     * Mock NotificationService - required by VmRequestNotificationSenderAdapter.
     *
     * Since tests don't have SMTP configured, the real NotificationService bean
     * isn't created by auto-configuration. This mock allows the application context
     * to load successfully.
     *
     * Marked @Primary to override any auto-configured bean if present.
     */
    @Bean
    @Primary
    public fun notificationService(): NotificationService = mockk(relaxed = true)
}
