package de.acci.dcm.infrastructure.notification

import de.acci.dcm.application.vmrequest.RequestApprovedNotification
import de.acci.dcm.application.vmrequest.RequestCreatedNotification
import de.acci.dcm.application.vmrequest.RequestRejectedNotification
import de.acci.dcm.application.vmrequest.VmReadyNotification
import de.acci.dcm.application.vmrequest.VmRequestNotificationError
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.notifications.EmailAddress
import de.acci.eaf.notifications.NotificationError
import de.acci.eaf.notifications.NotificationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("VmRequestNotificationSenderAdapter")
class VmRequestNotificationSenderAdapterTest {

    private lateinit var notificationService: NotificationService
    private lateinit var adapter: VmRequestNotificationSenderAdapter

    private val testTenantId = TenantId(UUID.randomUUID())
    private val testRequestId = VmRequestId.generate()
    private val testEmail = EmailAddress.of("user@example.com")
    private val testVmName = "web-server-01"
    private val testProjectName = "acme-project"

    @BeforeEach
    fun setUp() {
        notificationService = mockk()
        adapter = VmRequestNotificationSenderAdapter(notificationService)
    }

    @Nested
    @DisplayName("sendCreatedNotification")
    inner class SendCreatedNotification {

        @Test
        fun `returns success when notification service succeeds`() = runTest {
            // Given
            val notification = RequestCreatedNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName
            )
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = any()
                )
            } returns Unit.success()

            // When
            val result = adapter.sendCreatedNotification(notification)

            // Then
            assertTrue(result is Result.Success)
        }

        @Test
        fun `uses correct template and subject`() = runTest {
            // Given
            val notification = RequestCreatedNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName
            )
            val templateSlot = slot<String>()
            val subjectSlot = slot<String>()
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = capture(subjectSlot),
                    templateName = capture(templateSlot),
                    context = any()
                )
            } returns Unit.success()

            // When
            adapter.sendCreatedNotification(notification)

            // Then
            assertEquals("vm-request-created", templateSlot.captured)
            assertEquals("VM Request Created: $testVmName", subjectSlot.captured)
        }

        @Test
        fun `passes correct context to notification service`() = runTest {
            // Given
            val notification = RequestCreatedNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName
            )
            val contextSlot = slot<Map<String, Any>>()
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = capture(contextSlot)
                )
            } returns Unit.success()

            // When
            adapter.sendCreatedNotification(notification)

            // Then
            assertEquals(testRequestId.value.toString(), contextSlot.captured["requestId"])
            assertEquals(testVmName, contextSlot.captured["vmName"])
            assertEquals(testProjectName, contextSlot.captured["projectName"])
        }

        @Test
        fun `maps TemplateError from notification service`() = runTest {
            // Given
            val notification = RequestCreatedNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName
            )
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = any()
                )
            } returns NotificationError.TemplateError(
                templateName = "vm-request-created",
                message = "Template not found"
            ).failure()

            // When
            val result = adapter.sendCreatedNotification(notification)

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is VmRequestNotificationError.TemplateError)
            assertEquals("vm-request-created", (error as VmRequestNotificationError.TemplateError).templateName)
        }

        @Test
        fun `maps ConnectionError from notification service`() = runTest {
            // Given
            val notification = RequestCreatedNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName
            )
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = any()
                )
            } returns NotificationError.ConnectionError("SMTP connection refused").failure()

            // When
            val result = adapter.sendCreatedNotification(notification)

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is VmRequestNotificationError.SendFailure)
            assertTrue((error as VmRequestNotificationError.SendFailure).message.contains("SMTP connection"))
        }
    }

    @Nested
    @DisplayName("sendApprovedNotification")
    inner class SendApprovedNotification {

        @Test
        fun `returns success when notification service succeeds`() = runTest {
            // Given
            val notification = RequestApprovedNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName
            )
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = any()
                )
            } returns Unit.success()

            // When
            val result = adapter.sendApprovedNotification(notification)

            // Then
            assertTrue(result is Result.Success)
        }

        @Test
        fun `uses correct template and subject`() = runTest {
            // Given
            val notification = RequestApprovedNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName
            )
            val templateSlot = slot<String>()
            val subjectSlot = slot<String>()
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = capture(subjectSlot),
                    templateName = capture(templateSlot),
                    context = any()
                )
            } returns Unit.success()

            // When
            adapter.sendApprovedNotification(notification)

            // Then
            assertEquals("vm-request-approved", templateSlot.captured)
            assertEquals("VM Request Approved: $testVmName", subjectSlot.captured)
        }

    }

    @Nested
    @DisplayName("sendRejectedNotification")
    inner class SendRejectedNotification {

        @Test
        fun `returns success when notification service succeeds`() = runTest {
            // Given
            val notification = RequestRejectedNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName,
                reason = "Insufficient resources"
            )
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = any()
                )
            } returns Unit.success()

            // When
            val result = adapter.sendRejectedNotification(notification)

            // Then
            assertTrue(result is Result.Success)
        }

        @Test
        fun `uses correct template and subject`() = runTest {
            // Given
            val notification = RequestRejectedNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName,
                reason = "Insufficient resources"
            )
            val templateSlot = slot<String>()
            val subjectSlot = slot<String>()
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = capture(subjectSlot),
                    templateName = capture(templateSlot),
                    context = any()
                )
            } returns Unit.success()

            // When
            adapter.sendRejectedNotification(notification)

            // Then
            assertEquals("vm-request-rejected", templateSlot.captured)
            assertEquals("VM Request Rejected: $testVmName", subjectSlot.captured)
        }

        @Test
        fun `includes reason in context`() = runTest {
            // Given
            val rejectionReason = "Budget exceeded for this project"
            val notification = RequestRejectedNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName,
                reason = rejectionReason
            )
            val contextSlot = slot<Map<String, Any>>()
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = capture(contextSlot)
                )
            } returns Unit.success()

            // When
            adapter.sendRejectedNotification(notification)

            // Then
            assertEquals(rejectionReason, contextSlot.captured["reason"])
        }

    }

    @Nested
    @DisplayName("Error Mapping")
    inner class ErrorMapping {

        @Test
        fun `maps SendFailure from notification service`() = runTest {
            // Given
            val notification = RequestCreatedNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName
            )
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = any()
                )
            } returns NotificationError.SendFailure("Mail server rejected message").failure()

            // When
            val result = adapter.sendCreatedNotification(notification)

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is VmRequestNotificationError.SendFailure)
            assertEquals("Mail server rejected message", (error as VmRequestNotificationError.SendFailure).message)
        }

        @Test
        fun `maps InvalidRecipient from notification service`() = runTest {
            // Given
            val notification = RequestApprovedNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName
            )
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = any()
                )
            } returns NotificationError.InvalidRecipient(
                recipient = testEmail,
                message = "Recipient mailbox does not exist"
            ).failure()

            // When
            val result = adapter.sendApprovedNotification(notification)

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is VmRequestNotificationError.SendFailure)
            assertTrue((error as VmRequestNotificationError.SendFailure).message.contains("Invalid recipient"))
        }

        @Test
        fun `maps ConfigurationMissing from notification service`() = runTest {
            // Given
            val notification = RequestRejectedNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName,
                reason = "Test"
            )
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = any()
                )
            } returns NotificationError.ConfigurationMissing("SMTP host not configured").failure()

            // When
            val result = adapter.sendRejectedNotification(notification)

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is VmRequestNotificationError.SendFailure)
            assertEquals("SMTP host not configured", (error as VmRequestNotificationError.SendFailure).message)
        }
    }

    @Nested
    @DisplayName("sendVmReadyNotification (AC-3.8.1)")
    inner class SendVmReadyNotification {

        @Test
        fun `returns success when notification service succeeds`() = runTest {
            // Given
            val notification = VmReadyNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName,
                ipAddress = "192.168.1.100",
                hostname = "MYPR-web-server-01",
                provisioningDurationMinutes = 5,
                portalLink = "https://dcm.example.com/requests/${testRequestId.value}"
            )
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = any()
                )
            } returns Unit.success()

            // When
            val result = adapter.sendVmReadyNotification(notification)

            // Then
            assertTrue(result is Result.Success)
        }

        @Test
        fun `uses correct template and subject`() = runTest {
            // Given
            val notification = VmReadyNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName,
                ipAddress = "192.168.1.100",
                hostname = "MYPR-web-server-01",
                provisioningDurationMinutes = 5,
                portalLink = "https://dcm.example.com/requests/${testRequestId.value}"
            )
            val templateSlot = slot<String>()
            val subjectSlot = slot<String>()
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = capture(subjectSlot),
                    templateName = capture(templateSlot),
                    context = any()
                )
            } returns Unit.success()

            // When
            adapter.sendVmReadyNotification(notification)

            // Then
            assertEquals("vm-ready", templateSlot.captured)
            assertEquals("[DCM] VM ready: $testVmName", subjectSlot.captured)
        }

        @Test
        fun `passes correct context to notification service including requestId`() = runTest {
            // Given
            val notification = VmReadyNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName,
                ipAddress = "192.168.1.100",
                hostname = "MYPR-web-server-01",
                provisioningDurationMinutes = 5,
                portalLink = "https://dcm.example.com/requests/${testRequestId.value}"
            )
            val contextSlot = slot<Map<String, Any>>()
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = capture(contextSlot)
                )
            } returns Unit.success()

            // When
            adapter.sendVmReadyNotification(notification)

            // Then
            assertEquals(testRequestId.value.toString(), contextSlot.captured["requestId"])
            assertEquals(testVmName, contextSlot.captured["vmName"])
            assertEquals(testProjectName, contextSlot.captured["projectName"])
            assertEquals("192.168.1.100", contextSlot.captured["ipAddress"])
            assertEquals("MYPR-web-server-01", contextSlot.captured["hostname"])
            assertEquals(5L, contextSlot.captured["provisioningDuration"])
            assertEquals("https://dcm.example.com/requests/${testRequestId.value}", contextSlot.captured["portalLink"])
        }

        @Test
        fun `includes both SSH and RDP connection commands`() = runTest {
            // Given - email shows both commands so user can choose based on their OS
            val notification = VmReadyNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName,
                ipAddress = "192.168.1.100",
                hostname = "MYPR-web-server-01",
                provisioningDurationMinutes = 5,
                portalLink = "https://dcm.example.com/requests/${testRequestId.value}"
            )
            val contextSlot = slot<Map<String, Any>>()
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = capture(contextSlot)
                )
            } returns Unit.success()

            // When
            adapter.sendVmReadyNotification(notification)

            // Then - both commands provided for user to choose
            assertEquals("ssh <username>@192.168.1.100", contextSlot.captured["sshCommand"])
            assertEquals("mstsc /v:192.168.1.100", contextSlot.captured["rdpCommand"])
        }

        @Test
        fun `handles pending IP address in connection commands`() = runTest {
            // Given
            val notification = VmReadyNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName,
                ipAddress = null,  // IP not yet assigned
                hostname = "MYPR-web-server-01",
                provisioningDurationMinutes = 5,
                portalLink = "https://dcm.example.com/requests/${testRequestId.value}"
            )
            val contextSlot = slot<Map<String, Any>>()
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = capture(contextSlot)
                )
            } returns Unit.success()

            // When
            adapter.sendVmReadyNotification(notification)

            // Then - uses hostname as fallback when IP is pending
            assertEquals("ssh <username>@MYPR-web-server-01", contextSlot.captured["sshCommand"])
            assertEquals("mstsc /v:MYPR-web-server-01", contextSlot.captured["rdpCommand"])
            assertEquals("Pending assignment", contextSlot.captured["ipAddress"])
        }

        @Test
        fun `maps TemplateError from notification service`() = runTest {
            // Given
            val notification = VmReadyNotification(
                requestId = testRequestId,
                tenantId = testTenantId,
                requesterEmail = testEmail,
                vmName = testVmName,
                projectName = testProjectName,
                ipAddress = "192.168.1.100",
                hostname = "MYPR-web-server-01",
                provisioningDurationMinutes = 5,
                portalLink = "https://dcm.example.com/requests/${testRequestId.value}"
            )
            coEvery {
                notificationService.sendEmail(
                    tenantId = any(),
                    recipient = any(),
                    subject = any(),
                    templateName = any(),
                    context = any()
                )
            } returns NotificationError.TemplateError(
                templateName = "vm-ready",
                message = "Template not found"
            ).failure()

            // When
            val result = adapter.sendVmReadyNotification(notification)

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is VmRequestNotificationError.TemplateError)
            assertEquals("vm-ready", (error as VmRequestNotificationError.TemplateError).templateName)
        }
    }
}
