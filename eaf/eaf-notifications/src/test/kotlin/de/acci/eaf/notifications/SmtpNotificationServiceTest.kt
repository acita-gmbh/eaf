package de.acci.eaf.notifications

import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender

class SmtpNotificationServiceTest {

    private val mailSender = mockk<JavaMailSender>()
    private val templateEngine = mockk<TemplateEngine>()
    private val fromAddress = EmailAddress.of("noreply@example.com")
    private val service = SmtpNotificationService(mailSender, templateEngine, fromAddress)

    private val tenantId = TenantId.generate()
    private val recipient = EmailAddress.of("user@example.com")
    private val subject = "Test Subject"
    private val templateName = "test-template"
    private val context = mapOf("key" to "value")

    private val mimeMessage = mockk<MimeMessage>(relaxed = true)

    @BeforeEach
    fun setup() {
        every { mailSender.createMimeMessage() } returns mimeMessage
    }

    @Test
    fun `sendEmail returns success when email is sent`() = runTest {
        val renderedHtml = "<html>Hello</html>"

        every { templateEngine.render(templateName, context) } returns renderedHtml.success()
        every { mailSender.send(any<MimeMessage>()) } returns Unit

        val result = service.sendEmail(
            tenantId = tenantId,
            recipient = recipient,
            subject = subject,
            templateName = templateName,
            context = context
        )

        assertTrue(result is Result.Success)
        verify { mailSender.send(any<MimeMessage>()) }
    }

    @Test
    fun `sendEmail returns failure when template rendering fails`() = runTest {
        val templateError = NotificationError.TemplateError(
            templateName = templateName,
            message = "Template not found"
        )

        every { templateEngine.render(templateName, context) } returns templateError.failure()

        val result = service.sendEmail(
            tenantId = tenantId,
            recipient = recipient,
            subject = subject,
            templateName = templateName,
            context = context
        )

        assertTrue(result is Result.Failure)
        assertEquals(templateError, (result as Result.Failure).error)
        verify(exactly = 0) { mailSender.send(any<MimeMessage>()) }
    }

    @Test
    fun `sendEmail returns ConnectionError on authentication failure`() = runTest {
        every { templateEngine.render(templateName, context) } returns "<html></html>".success()
        every { mailSender.send(any<MimeMessage>()) } throws MailAuthenticationException("Invalid credentials")

        val result = service.sendEmail(
            tenantId = tenantId,
            recipient = recipient,
            subject = subject,
            templateName = templateName,
            context = context
        )

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is NotificationError.ConnectionError)
        assertTrue(error.message.contains("authentication"))
    }

    @Test
    fun `sendEmail returns InvalidRecipient on invalid address error`() = runTest {
        every { templateEngine.render(templateName, context) } returns "<html></html>".success()
        every { mailSender.send(any<MimeMessage>()) } throws MailSendException("Invalid Addresses: user@bad.com")

        val result = service.sendEmail(
            tenantId = tenantId,
            recipient = recipient,
            subject = subject,
            templateName = templateName,
            context = context
        )

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is NotificationError.InvalidRecipient)
    }

    @Test
    fun `sendEmail returns SendFailure on generic mail error`() = runTest {
        every { templateEngine.render(templateName, context) } returns "<html></html>".success()
        every { mailSender.send(any<MimeMessage>()) } throws MailSendException("Connection timeout")

        val result = service.sendEmail(
            tenantId = tenantId,
            recipient = recipient,
            subject = subject,
            templateName = templateName,
            context = context
        )

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is NotificationError.SendFailure)
    }

    @Test
    fun `sendEmail sets correct message properties`() = runTest {
        val renderedHtml = "<html>Test Content</html>"
        val messageSlot = slot<MimeMessage>()

        every { templateEngine.render(templateName, context) } returns renderedHtml.success()
        every { mailSender.send(capture(messageSlot)) } returns Unit

        service.sendEmail(
            tenantId = tenantId,
            recipient = recipient,
            subject = subject,
            templateName = templateName,
            context = context
        )

        verify { mailSender.send(any<MimeMessage>()) }
    }
}
