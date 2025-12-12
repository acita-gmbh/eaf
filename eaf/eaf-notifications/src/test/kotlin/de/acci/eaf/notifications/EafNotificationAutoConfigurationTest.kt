package de.acci.eaf.notifications

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.BeanCreationException
import org.springframework.mail.javamail.JavaMailSender
import org.thymeleaf.ITemplateEngine

@DisplayName("EafNotificationAutoConfiguration")
class EafNotificationAutoConfigurationTest {

    private val autoConfig = EafNotificationAutoConfiguration()
    private val mockMailSender: JavaMailSender = mockk()
    private val mockThymeleafEngine: ITemplateEngine = mockk()

    @Nested
    @DisplayName("templateEngine bean")
    inner class TemplateEngineBean {

        @Test
        fun `creates ThymeleafEmailTemplateEngine wrapper`() {
            // When
            val result = autoConfig.templateEngine(mockThymeleafEngine)

            // Then
            assertNotNull(result)
            assertTrue(result is ThymeleafEmailTemplateEngine)
        }
    }

    @Nested
    @DisplayName("notificationService bean")
    inner class NotificationServiceBean {

        @Test
        fun `creates SmtpNotificationService with valid email`() {
            // Given
            val mockTemplateEngine: TemplateEngine = mockk()
            val properties = EafNotificationProperties(
                fromAddress = "valid@example.com",
                enabled = true
            )

            // When
            val result = autoConfig.notificationService(
                mailSender = mockMailSender,
                templateEngine = mockTemplateEngine,
                properties = properties
            )

            // Then
            assertNotNull(result)
            assertTrue(result is SmtpNotificationService)
        }

        @Test
        fun `throws BeanCreationException for invalid email address`() {
            // Given
            val mockTemplateEngine: TemplateEngine = mockk()
            val properties = EafNotificationProperties(
                fromAddress = "not-a-valid-email",
                enabled = true
            )

            // When/Then - Spring convention is to throw BeanCreationException for config issues
            val exception = assertThrows<BeanCreationException> {
                autoConfig.notificationService(
                    mailSender = mockMailSender,
                    templateEngine = mockTemplateEngine,
                    properties = properties
                )
            }

            // Verify error message contains the invalid address and guidance
            assertTrue(exception.message!!.contains("eaf.notifications.from-address"))
            assertTrue(exception.message!!.contains("not-a-valid-email"))
            assertTrue(exception.message!!.contains("valid email address"))
            assertNotNull(exception.cause)
        }

        @Test
        fun `throws BeanCreationException for empty email address`() {
            // Given
            val mockTemplateEngine: TemplateEngine = mockk()
            val properties = EafNotificationProperties(
                fromAddress = "",
                enabled = true
            )

            // When/Then - Spring convention is to throw BeanCreationException for config issues
            val exception = assertThrows<BeanCreationException> {
                autoConfig.notificationService(
                    mailSender = mockMailSender,
                    templateEngine = mockTemplateEngine,
                    properties = properties
                )
            }

            assertTrue(exception.message!!.contains("eaf.notifications.from-address"))
        }
    }

    @Nested
    @DisplayName("EafNotificationProperties")
    inner class PropertiesTests {

        @Test
        fun `has sensible defaults`() {
            // When
            val properties = EafNotificationProperties()

            // Then
            assertEquals("noreply@example.com", properties.fromAddress)
            assertTrue(properties.enabled)
        }

        @Test
        fun `allows custom values`() {
            // When
            val properties = EafNotificationProperties(
                fromAddress = "custom@company.com",
                enabled = false
            )

            // Then
            assertEquals("custom@company.com", properties.fromAddress)
            assertEquals(false, properties.enabled)
        }
    }
}
