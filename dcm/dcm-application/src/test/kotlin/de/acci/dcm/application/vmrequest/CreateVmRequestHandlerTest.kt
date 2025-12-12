package de.acci.dcm.application.vmrequest

import de.acci.dcm.domain.vmrequest.ProjectId
import de.acci.dcm.domain.vmrequest.VmName
import de.acci.dcm.domain.vmrequest.VmSize
import de.acci.dcm.domain.vmrequest.events.VmRequestCreated
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.eventsourcing.EventStoreError
import de.acci.eaf.eventsourcing.StoredEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("CreateVmRequestHandler")
class CreateVmRequestHandlerTest {

    private val eventStore = mockk<EventStore>()

    private fun createCommand(
        tenantId: TenantId = TenantId.generate(),
        requesterId: UserId = UserId.generate(),
        requesterEmail: String = "test@example.com",
        projectId: ProjectId = ProjectId.generate(),
        vmName: VmName = VmName.of("test-vm-01"),
        size: VmSize = VmSize.M,
        justification: String = "Valid justification for testing"
    ) = CreateVmRequestCommand(
        tenantId = tenantId,
        requesterId = requesterId,
        requesterEmail = requesterEmail,
        projectId = projectId,
        vmName = vmName,
        size = size,
        justification = justification
    )

    @Nested
    @DisplayName("handle()")
    inner class HandleTests {

        @Test
        @DisplayName("should create aggregate and persist events to store")
        fun `should create aggregate and persist events to store`() = runTest {
            // Given
            val command = createCommand()
            val eventsSlot = slot<List<DomainEvent>>()

            coEvery {
                eventStore.append(any(), capture(eventsSlot), any())
            } returns 1L.success()

            val handler = CreateVmRequestHandler(eventStore)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            coVerify(exactly = 1) {
                eventStore.append(any(), any(), eq(0L))
            }

            val events = eventsSlot.captured
            assertEquals(1, events.size)
            assertTrue(events[0] is VmRequestCreated)
        }

        @Test
        @DisplayName("should return created request ID on success")
        fun `should return created request ID on success`() = runTest {
            // Given
            val command = createCommand()

            coEvery {
                eventStore.append(any(), any(), any())
            } returns 1L.success()

            val handler = CreateVmRequestHandler(eventStore)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertTrue(success.value.requestId.value.toString().isNotBlank())
        }

        @Test
        @DisplayName("should propagate tenant context to aggregate")
        fun `should propagate tenant context to aggregate`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val command = createCommand(tenantId = tenantId)
            val eventsSlot = slot<List<DomainEvent>>()

            coEvery {
                eventStore.append(any(), capture(eventsSlot), any())
            } returns 1L.success()

            val handler = CreateVmRequestHandler(eventStore)

            // When
            handler.handle(command)

            // Then
            val event = eventsSlot.captured[0] as VmRequestCreated
            assertEquals(tenantId, event.metadata.tenantId)
        }

        @Test
        @DisplayName("should propagate user context to aggregate")
        fun `should propagate user context to aggregate`() = runTest {
            // Given
            val requesterId = UserId.generate()
            val command = createCommand(requesterId = requesterId)
            val eventsSlot = slot<List<DomainEvent>>()

            coEvery {
                eventStore.append(any(), capture(eventsSlot), any())
            } returns 1L.success()

            val handler = CreateVmRequestHandler(eventStore)

            // When
            handler.handle(command)

            // Then
            val event = eventsSlot.captured[0] as VmRequestCreated
            assertEquals(requesterId, event.metadata.userId)
        }

        @Test
        @DisplayName("should include provided correlation ID in metadata")
        fun `should include provided correlation ID in metadata`() = runTest {
            // Given
            val correlationId = CorrelationId.generate()
            val command = createCommand()
            val eventsSlot = slot<List<DomainEvent>>()

            coEvery {
                eventStore.append(any(), capture(eventsSlot), any())
            } returns 1L.success()

            val handler = CreateVmRequestHandler(eventStore)

            // When
            handler.handle(command, correlationId)

            // Then
            val event = eventsSlot.captured[0] as VmRequestCreated
            assertEquals(correlationId, event.metadata.correlationId)
        }

        @Test
        @DisplayName("should capture all command fields in event")
        fun `should capture all command fields in event`() = runTest {
            // Given
            val projectId = ProjectId.generate()
            val vmName = VmName.of("web-server-prod")
            val size = VmSize.XL
            val justification = "Production web server for customer portal"
            val command = createCommand(
                projectId = projectId,
                vmName = vmName,
                size = size,
                justification = justification
            )
            val eventsSlot = slot<List<DomainEvent>>()

            coEvery {
                eventStore.append(any(), capture(eventsSlot), any())
            } returns 1L.success()

            val handler = CreateVmRequestHandler(eventStore)

            // When
            handler.handle(command)

            // Then
            val event = eventsSlot.captured[0] as VmRequestCreated
            assertEquals(projectId, event.projectId)
            assertEquals(vmName, event.vmName)
            assertEquals(size, event.size)
            assertEquals(justification, event.justification)
        }
    }

    @Nested
    @DisplayName("quota validation")
    inner class QuotaValidationTests {

        @Test
        @DisplayName("should fail with QuotaExceeded when quota check fails")
        fun `should fail with QuotaExceeded when quota check fails`() = runTest {
            // Given
            val command = createCommand()
            val quotaChecker = mockk<QuotaChecker>()

            coEvery {
                quotaChecker.checkQuota(any(), any())
            } returns CreateVmRequestError.QuotaExceeded(
                available = 0,
                requested = 1
            ).failure()

            val handler = CreateVmRequestHandler(eventStore, quotaChecker)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CreateVmRequestError.QuotaExceeded)
            val quotaError = failure.error as CreateVmRequestError.QuotaExceeded
            assertEquals(0, quotaError.available)
            assertEquals(1, quotaError.requested)
        }

        @Test
        @DisplayName("should not persist events when quota check fails")
        fun `should not persist events when quota check fails`() = runTest {
            // Given
            val command = createCommand()
            val quotaChecker = mockk<QuotaChecker>()

            coEvery {
                quotaChecker.checkQuota(any(), any())
            } returns CreateVmRequestError.QuotaExceeded(
                available = 0,
                requested = 1
            ).failure()

            val handler = CreateVmRequestHandler(eventStore, quotaChecker)

            // When
            handler.handle(command)

            // Then
            coVerify(exactly = 0) {
                eventStore.append(any(), any(), any())
            }
        }

        @Test
        @DisplayName("should proceed when quota is available")
        fun `should proceed when quota is available`() = runTest {
            // Given
            val command = createCommand()
            val quotaChecker = mockk<QuotaChecker>()

            coEvery {
                quotaChecker.checkQuota(any(), any())
            } returns Unit.success()

            coEvery {
                eventStore.append(any(), any(), any())
            } returns 1L.success()

            val handler = CreateVmRequestHandler(eventStore, quotaChecker)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            coVerify(exactly = 1) {
                eventStore.append(any(), any(), any())
            }
        }
    }

    @Nested
    @DisplayName("error handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("should return ConcurrencyConflict on event store conflict")
        fun `should return ConcurrencyConflict on event store conflict`() = runTest {
            // Given
            val command = createCommand()
            val aggregateId = UUID.randomUUID()

            coEvery {
                eventStore.append(any(), any(), any())
            } returns EventStoreError.ConcurrencyConflict(
                aggregateId = aggregateId,
                expectedVersion = 0,
                actualVersion = 1
            ).failure()

            val handler = CreateVmRequestHandler(eventStore)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CreateVmRequestError.ConcurrencyConflict)
        }

        @Test
        @DisplayName("should return PersistenceFailure when event store throws exception")
        fun `should return PersistenceFailure when event store throws exception`() = runTest {
            // Given
            val command = createCommand()

            coEvery {
                eventStore.append(any(), any(), any())
            } throws RuntimeException("Database connection failed")

            val handler = CreateVmRequestHandler(eventStore)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CreateVmRequestError.PersistenceFailure)
            val persistenceError = failure.error as CreateVmRequestError.PersistenceFailure
            assertTrue(persistenceError.message.contains("Database connection failed"))
        }
    }

    @Nested
    @DisplayName("AlwaysAvailableQuotaChecker")
    inner class AlwaysAvailableQuotaCheckerTests {

        @Test
        @DisplayName("should always return success")
        fun `should always return success`() = runTest {
            // When
            val result = AlwaysAvailableQuotaChecker.checkQuota(
                tenantId = TenantId.generate(),
                projectId = ProjectId.generate()
            )

            // Then
            assertTrue(result is Result.Success)
        }
    }

    @Nested
    @DisplayName("notification integration")
    inner class NotificationTests {

        @Test
        @DisplayName("should succeed but skip notification when requester email is invalid")
        fun `should succeed but skip notification when requester email is invalid`() = runTest {
            // Given - command with invalid email that fails EmailAddress.of()
            val command = createCommand(requesterEmail = "not-a-valid-email")
            val notificationSender = mockk<VmRequestNotificationSender>()

            coEvery {
                eventStore.append(any(), any(), any())
            } returns 1L.success()

            val handler = CreateVmRequestHandler(
                eventStore = eventStore,
                notificationSender = notificationSender
            )

            // When
            val result = handler.handle(command)

            // Then - command succeeds but notification is NOT sent
            assertTrue(result is Result.Success)
            coVerify(exactly = 0) { notificationSender.sendCreatedNotification(any()) }
        }

        @Test
        @DisplayName("should send notification when requester email is valid")
        fun `should send notification when requester email is valid`() = runTest {
            // Given
            val command = createCommand(requesterEmail = "valid@example.com")
            val notificationSender = mockk<VmRequestNotificationSender>()

            coEvery {
                eventStore.append(any(), any(), any())
            } returns 1L.success()

            coEvery {
                notificationSender.sendCreatedNotification(any())
            } returns Unit.success()

            val handler = CreateVmRequestHandler(
                eventStore = eventStore,
                notificationSender = notificationSender
            )

            // When
            val result = handler.handle(command)

            // Then - command succeeds and notification IS sent
            assertTrue(result is Result.Success)
            coVerify(exactly = 1) { notificationSender.sendCreatedNotification(any()) }
        }

        @Test
        @DisplayName("should succeed even when notification fails")
        fun `should succeed even when notification fails`() = runTest {
            // Given - notification will fail but command should still succeed
            val command = createCommand(requesterEmail = "valid@example.com")
            val notificationSender = mockk<VmRequestNotificationSender>()

            coEvery {
                eventStore.append(any(), any(), any())
            } returns 1L.success()

            coEvery {
                notificationSender.sendCreatedNotification(any())
            } returns VmRequestNotificationError.SendFailure("SMTP server unreachable").failure()

            val handler = CreateVmRequestHandler(
                eventStore = eventStore,
                notificationSender = notificationSender
            )

            // When
            val result = handler.handle(command)

            // Then - command still succeeds (fire-and-forget pattern)
            assertTrue(result is Result.Success)
        }
    }
}
