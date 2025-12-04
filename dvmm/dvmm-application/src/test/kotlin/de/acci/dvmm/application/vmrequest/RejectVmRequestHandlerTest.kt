package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmRequestStatus
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.dvmm.domain.vmrequest.events.VmRequestApproved
import de.acci.dvmm.domain.vmrequest.events.VmRequestCreated
import de.acci.dvmm.domain.vmrequest.events.VmRequestRejected
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.CorrelationId
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.DomainEvent
import de.acci.eaf.eventsourcing.EventMetadata
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.eventsourcing.EventStoreError
import de.acci.eaf.eventsourcing.StoredEvent
import de.acci.eaf.testing.fixtures.TestMetadataFactory
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
import java.time.Instant
import java.util.UUID

@DisplayName("RejectVmRequestHandler")
class RejectVmRequestHandlerTest {

    private val eventStore = mockk<EventStore>()
    private val eventDeserializer = mockk<VmRequestEventDeserializer>()
    private val projectionUpdater = mockk<VmRequestProjectionUpdater>()
    private val timelineUpdater = mockk<TimelineEventProjectionUpdater>()

    private fun createCommand(
        tenantId: TenantId = TenantId.generate(),
        requestId: VmRequestId = VmRequestId.generate(),
        adminId: UserId = UserId.generate(),
        version: Long = 1L,
        reason: String = "This request does not meet resource allocation policies"
    ) = RejectVmRequestCommand(
        tenantId = tenantId,
        requestId = requestId,
        adminId = adminId,
        version = version,
        reason = reason
    )

    private fun createStoredEvent(
        aggregateId: VmRequestId,
        eventType: String,
        payload: String,
        metadata: EventMetadata,
        version: Long
    ) = StoredEvent(
        id = UUID.randomUUID(),
        aggregateId = aggregateId.value,
        aggregateType = "VmRequest",
        eventType = eventType,
        payload = payload,
        metadata = metadata,
        version = version,
        createdAt = Instant.now()
    )

    private fun createCreatedEvent(
        aggregateId: VmRequestId,
        metadata: EventMetadata,
        projectId: ProjectId = ProjectId.generate(),
        requesterEmail: String = "test@example.com"
    ) = VmRequestCreated(
        aggregateId = aggregateId,
        projectId = projectId,
        vmName = VmName.of("test-vm-01"),
        size = VmSize.M,
        justification = "Test justification for testing",
        requesterEmail = requesterEmail,
        metadata = metadata
    )

    @Nested
    @DisplayName("handle()")
    inner class HandleTests {

        @Test
        @DisplayName("should reject pending request and persist VmRequestRejected event")
        fun `should reject pending request and persist VmRequestRejected event`() = runTest {
            // Given - requester and admin are different users
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()  // Different from requester
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val rejectionReason = "Resources not available for this project allocation"
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 1L,
                reason = rejectionReason
            )
            val eventsSlot = slot<List<DomainEvent>>()

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery {
                eventStore.append(requestId.value, capture(eventsSlot), eq(1L))
            } returns 2L.success()

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            assertEquals(1, eventsSlot.captured.size)
            assertTrue(eventsSlot.captured[0] is VmRequestRejected)

            val rejectedEvent = eventsSlot.captured[0] as VmRequestRejected
            assertEquals(requestId, rejectedEvent.aggregateId)
            assertEquals(rejectionReason, rejectedEvent.reason)
            assertEquals(adminId, rejectedEvent.metadata.userId)
        }

        @Test
        @DisplayName("should return request ID on successful rejection")
        fun `should return request ID on successful rejection`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 1L
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery { eventStore.append(any(), any(), any()) } returns 2L.success()

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertEquals(requestId, success.value.requestId)
        }

        @Test
        @DisplayName("should propagate metadata from command")
        fun `should propagate metadata from command`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 1L
            )
            val correlationId = CorrelationId.generate()
            val eventsSlot = slot<List<DomainEvent>>()

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery { eventStore.append(any(), capture(eventsSlot), any()) } returns 2L.success()

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            handler.handle(command, correlationId)

            // Then
            val event = eventsSlot.captured[0] as VmRequestRejected
            assertEquals(tenantId, event.metadata.tenantId)
            assertEquals(adminId, event.metadata.userId)
            assertEquals(correlationId, event.metadata.correlationId)
        }
    }

    @Nested
    @DisplayName("not found scenarios")
    inner class NotFoundTests {

        @Test
        @DisplayName("should return NotFound when aggregate does not exist")
        fun `should return NotFound when aggregate does not exist`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val command = createCommand(requestId = requestId)

            coEvery { eventStore.load(requestId.value) } returns emptyList()

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is RejectVmRequestError.NotFound)
        }

        @Test
        @DisplayName("should not call append when aggregate not found")
        fun `should not call append when aggregate not found`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val command = createCommand(requestId = requestId)

            coEvery { eventStore.load(requestId.value) } returns emptyList()

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            handler.handle(command)

            // Then
            coVerify(exactly = 0) { eventStore.append(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("separation of duties - self-rejection check")
    inner class SelfRejectionTests {

        @Test
        @DisplayName("should return Forbidden when admin tries to reject own request")
        fun `should return Forbidden when admin tries to reject own request`() = runTest {
            // Given - admin is the same as requester
            val requestId = VmRequestId.generate()
            val sameUserId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = sameUserId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = sameUserId,  // Same user trying to reject own request
                version = 1L
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is RejectVmRequestError.Forbidden)
        }

        @Test
        @DisplayName("should not call append when self-rejection is attempted")
        fun `should not call append when self-rejection is attempted`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val sameUserId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = sameUserId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = sameUserId,
                version = 1L
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            handler.handle(command)

            // Then
            coVerify(exactly = 0) { eventStore.append(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("invalid state scenarios")
    inner class InvalidStateTests {

        @Test
        @DisplayName("should return InvalidState when rejecting already-approved request")
        fun `should return InvalidState when rejecting already-approved request`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val approvedEvent = VmRequestApproved(
                aggregateId = requestId,
                vmName = VmName.of("test-vm-01"),
                projectId = ProjectId.generate(),
                requesterId = requesterId,
                requesterEmail = "test@example.com",
                metadata = TestMetadataFactory.create(tenantId = tenantId, userId = adminId)
            )
            val storedCreated = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val storedApproved = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestApproved",
                payload = "{}",
                metadata = originalMetadata,
                version = 2
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 2L
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedCreated, storedApproved)
            coEvery { eventDeserializer.deserialize(storedCreated) } returns createdEvent
            coEvery { eventDeserializer.deserialize(storedApproved) } returns approvedEvent

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is RejectVmRequestError.InvalidState)
            assertEquals("APPROVED", (failure.error as RejectVmRequestError.InvalidState).currentState)
        }

        @Test
        @DisplayName("should return InvalidState when rejecting already-rejected request")
        fun `should return InvalidState when rejecting already-rejected request`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val rejectedEvent = VmRequestRejected(
                aggregateId = requestId,
                reason = "Previous rejection",
                vmName = VmName.of("test-vm-01"),
                projectId = ProjectId.generate(),
                requesterId = requesterId,
                requesterEmail = "test@example.com",
                metadata = TestMetadataFactory.create(tenantId = tenantId, userId = adminId)
            )
            val storedCreated = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val storedRejected = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestRejected",
                payload = "{}",
                metadata = originalMetadata,
                version = 2
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 2L
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedCreated, storedRejected)
            coEvery { eventDeserializer.deserialize(storedCreated) } returns createdEvent
            coEvery { eventDeserializer.deserialize(storedRejected) } returns rejectedEvent

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is RejectVmRequestError.InvalidState)
            assertEquals("REJECTED", (failure.error as RejectVmRequestError.InvalidState).currentState)
        }
    }

    @Nested
    @DisplayName("invalid reason validation")
    inner class InvalidReasonTests {

        @Test
        @DisplayName("should return InvalidReason when reason is too short")
        fun `should return InvalidReason when reason is too short`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 1L,
                reason = "short"  // Less than 10 characters
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is RejectVmRequestError.InvalidReason)
        }

        @Test
        @DisplayName("should return InvalidReason when reason is too long")
        fun `should return InvalidReason when reason is too long`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 1L,
                reason = "a".repeat(501)  // More than 500 characters
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is RejectVmRequestError.InvalidReason)
        }

        @Test
        @DisplayName("should not call append when reason validation fails")
        fun `should not call append when reason validation fails`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 1L,
                reason = "short"
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            handler.handle(command)

            // Then
            coVerify(exactly = 0) { eventStore.append(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("optimistic locking")
    inner class OptimisticLockingTests {

        @Test
        @DisplayName("should return ConcurrencyConflict when version mismatch")
        fun `should return ConcurrencyConflict when version mismatch`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 5L  // Wrong version - actual is 1
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is RejectVmRequestError.ConcurrencyConflict)
        }

        @Test
        @DisplayName("should return ConcurrencyConflict on event store conflict")
        fun `should return ConcurrencyConflict on event store conflict`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 1L
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery {
                eventStore.append(any(), any(), any())
            } returns EventStoreError.ConcurrencyConflict(
                aggregateId = requestId.value,
                expectedVersion = 1,
                actualVersion = 2
            ).failure()

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is RejectVmRequestError.ConcurrencyConflict)
        }
    }

    @Nested
    @DisplayName("error handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("should return PersistenceFailure when event store load throws exception")
        fun `should return PersistenceFailure when event store load throws exception`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val command = createCommand(requestId = requestId)

            coEvery {
                eventStore.load(requestId.value)
            } throws RuntimeException("Database connection failed")

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is RejectVmRequestError.PersistenceFailure)
        }

        @Test
        @DisplayName("should return PersistenceFailure when event store append throws exception")
        fun `should return PersistenceFailure when event store append throws exception`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 1L
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery {
                eventStore.append(any(), any(), any())
            } throws RuntimeException("Database connection failed")

            val handler = RejectVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is RejectVmRequestError.PersistenceFailure)
        }
    }

    @Nested
    @DisplayName("projection updater integration")
    inner class ProjectionUpdaterTests {

        @Test
        @DisplayName("should call projection updater with correct status after successful rejection")
        fun `should call projection updater with correct status after successful rejection`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val rejectionReason = "Insufficient resources available"
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 1L,
                reason = rejectionReason
            )
            val updateSlot = slot<VmRequestStatusUpdate>()

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery { eventStore.append(any(), any(), any()) } returns 2L.success()
            coEvery { projectionUpdater.updateStatus(capture(updateSlot)) } returns Unit.success()
            coEvery { timelineUpdater.addTimelineEvent(any()) } returns Unit.success()

            val handler = RejectVmRequestHandler(
                eventStore,
                eventDeserializer,
                projectionUpdater,
                timelineUpdater
            )

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            coVerify(exactly = 1) { projectionUpdater.updateStatus(any()) }

            val capturedUpdate = updateSlot.captured
            assertEquals(requestId, capturedUpdate.id)
            assertEquals(VmRequestStatus.REJECTED, capturedUpdate.status)
            assertEquals(2, capturedUpdate.version)
            assertEquals(adminId, capturedUpdate.rejectedBy)
            assertEquals(rejectionReason, capturedUpdate.rejectionReason)
        }

        @Test
        @DisplayName("should not call projection updater when aggregate not found")
        fun `should not call projection updater when aggregate not found`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val command = createCommand(requestId = requestId)

            coEvery { eventStore.load(requestId.value) } returns emptyList()

            val handler = RejectVmRequestHandler(
                eventStore,
                eventDeserializer,
                projectionUpdater,
                timelineUpdater
            )

            // When
            handler.handle(command)

            // Then
            coVerify(exactly = 0) { projectionUpdater.updateStatus(any()) }
            coVerify(exactly = 0) { timelineUpdater.addTimelineEvent(any()) }
        }
    }

    @Nested
    @DisplayName("notification integration")
    inner class NotificationTests {

        @Test
        @DisplayName("should succeed but skip notification when requester email is invalid")
        fun `should succeed but skip notification when requester email is invalid`() = runTest {
            // Given - aggregate with invalid email stored in events
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata,
                requesterEmail = "not-a-valid-email"  // Invalid email
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 1L
            )
            val notificationSender = mockk<VmRequestNotificationSender>()

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery { eventStore.append(any(), any(), any()) } returns 2L.success()

            val handler = RejectVmRequestHandler(
                eventStore = eventStore,
                eventDeserializer = eventDeserializer,
                notificationSender = notificationSender
            )

            // When
            val result = handler.handle(command)

            // Then - command succeeds but notification is NOT sent
            assertTrue(result is Result.Success)
            coVerify(exactly = 0) { notificationSender.sendRejectedNotification(any()) }
        }

        @Test
        @DisplayName("should send notification when requester email is valid")
        fun `should send notification when requester email is valid`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata,
                requesterEmail = "valid@example.com"
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 1L
            )
            val notificationSender = mockk<VmRequestNotificationSender>()

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery { eventStore.append(any(), any(), any()) } returns 2L.success()
            coEvery { notificationSender.sendRejectedNotification(any()) } returns Unit.success()

            val handler = RejectVmRequestHandler(
                eventStore = eventStore,
                eventDeserializer = eventDeserializer,
                notificationSender = notificationSender
            )

            // When
            val result = handler.handle(command)

            // Then - command succeeds and notification IS sent
            assertTrue(result is Result.Success)
            coVerify(exactly = 1) { notificationSender.sendRejectedNotification(any()) }
        }

        @Test
        @DisplayName("should succeed even when notification fails")
        fun `should succeed even when notification fails`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val adminId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = requesterId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata,
                requesterEmail = "valid@example.com"
            )
            val storedEvent = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                adminId = adminId,
                version = 1L
            )
            val notificationSender = mockk<VmRequestNotificationSender>()

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery { eventStore.append(any(), any(), any()) } returns 2L.success()
            coEvery {
                notificationSender.sendRejectedNotification(any())
            } returns VmRequestNotificationError.SendFailure("SMTP server unreachable").failure()

            val handler = RejectVmRequestHandler(
                eventStore = eventStore,
                eventDeserializer = eventDeserializer,
                notificationSender = notificationSender
            )

            // When
            val result = handler.handle(command)

            // Then - command still succeeds (fire-and-forget pattern)
            assertTrue(result is Result.Success)
        }
    }
}
