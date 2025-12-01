package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestAggregate
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmRequestStatus
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.dvmm.domain.vmrequest.events.VmRequestCancelled
import de.acci.dvmm.domain.vmrequest.events.VmRequestCreated
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

@DisplayName("CancelVmRequestHandler")
class CancelVmRequestHandlerTest {

    private val eventStore = mockk<EventStore>()
    private val eventDeserializer = mockk<VmRequestEventDeserializer>()
    private val projectionUpdater = mockk<VmRequestProjectionUpdater>()

    private fun createCommand(
        tenantId: TenantId = TenantId.generate(),
        requestId: VmRequestId = VmRequestId.generate(),
        userId: UserId = UserId.generate(),
        reason: String? = "No longer needed"
    ) = CancelVmRequestCommand(
        tenantId = tenantId,
        requestId = requestId,
        userId = userId,
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
        projectId: ProjectId = ProjectId.generate()
    ) = VmRequestCreated(
        aggregateId = aggregateId,
        projectId = projectId,
        vmName = VmName.of("test-vm-01"),
        size = VmSize.M,
        justification = "Test justification for testing",
        metadata = metadata
    )

    @Nested
    @DisplayName("handle()")
    inner class HandleTests {

        @Test
        @DisplayName("should cancel pending request and persist VmRequestCancelled event")
        fun `should cancel pending request and persist VmRequestCancelled event`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val userId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)
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
                userId = userId,
                reason = "Project cancelled"
            )
            val eventsSlot = slot<List<DomainEvent>>()

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery {
                eventStore.append(requestId.value, capture(eventsSlot), eq(1L))
            } returns 2L.success()

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            assertEquals(1, eventsSlot.captured.size)
            assertTrue(eventsSlot.captured[0] is VmRequestCancelled)

            val cancelledEvent = eventsSlot.captured[0] as VmRequestCancelled
            assertEquals(requestId, cancelledEvent.aggregateId)
            assertEquals("Project cancelled", cancelledEvent.reason)
        }

        @Test
        @DisplayName("should return request ID on successful cancellation")
        fun `should return request ID on successful cancellation`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val userId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)
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
                userId = userId
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery { eventStore.append(any(), any(), any()) } returns 2L.success()

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer)

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
            val userId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)
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
                userId = userId
            )
            val correlationId = CorrelationId.generate()
            val eventsSlot = slot<List<DomainEvent>>()

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery { eventStore.append(any(), capture(eventsSlot), any()) } returns 2L.success()

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer)

            // When
            handler.handle(command, correlationId)

            // Then
            val event = eventsSlot.captured[0] as VmRequestCancelled
            assertEquals(tenantId, event.metadata.tenantId)
            assertEquals(userId, event.metadata.userId)
            assertEquals(correlationId, event.metadata.correlationId)
        }

        @Test
        @DisplayName("should support null cancellation reason")
        fun `should support null cancellation reason`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val userId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)
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
                userId = userId,
                reason = null
            )
            val eventsSlot = slot<List<DomainEvent>>()

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery { eventStore.append(any(), capture(eventsSlot), any()) } returns 2L.success()

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            val event = eventsSlot.captured[0] as VmRequestCancelled
            assertEquals(null, event.reason)
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

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CancelVmRequestError.NotFound)
        }

        @Test
        @DisplayName("should not call append when aggregate not found")
        fun `should not call append when aggregate not found`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val command = createCommand(requestId = requestId)

            coEvery { eventStore.load(requestId.value) } returns emptyList()

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer)

            // When
            handler.handle(command)

            // Then
            coVerify(exactly = 0) { eventStore.append(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("authorization - ownership check")
    inner class AuthorizationTests {

        @Test
        @DisplayName("should return Forbidden when user is not the original requester")
        fun `should return Forbidden when user is not the original requester`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val originalRequester = UserId.generate()
            val differentUser = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = originalRequester)
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
                userId = differentUser  // Different user trying to cancel
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CancelVmRequestError.Forbidden)
        }

        @Test
        @DisplayName("should not call append when user is not authorized")
        fun `should not call append when user is not authorized`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val originalRequester = UserId.generate()
            val differentUser = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = originalRequester)
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
                userId = differentUser
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer)

            // When
            handler.handle(command)

            // Then
            coVerify(exactly = 0) { eventStore.append(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("invalid state and idempotent scenarios")
    inner class InvalidStateTests {

        // Note: InvalidState error for APPROVED/REJECTED/PROVISIONING/READY/FAILED states
        // is thoroughly tested in VmRequestAggregateCancelTest.kt and VmRequestControllerTest.kt.
        // The handler correctly propagates InvalidStateException as CancelVmRequestError.InvalidState.
        // These tests focus on the CANCELLED state idempotent behavior.

        @Test
        @DisplayName("should return success when cancelling already-cancelled request (idempotent)")
        fun `should return success when cancelling already-cancelled request (idempotent)`() = runTest {
            // Given - simulate an aggregate that was already cancelled
            val requestId = VmRequestId.generate()
            val userId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val cancelledEvent = VmRequestCancelled(
                aggregateId = requestId,
                reason = "Already cancelled",
                metadata = originalMetadata
            )
            val storedCreated = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val storedCancelled = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCancelled",
                payload = "{}",
                metadata = originalMetadata,
                version = 2
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                userId = userId
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedCreated, storedCancelled)
            coEvery { eventDeserializer.deserialize(storedCreated) } returns createdEvent
            coEvery { eventDeserializer.deserialize(storedCancelled) } returns cancelledEvent

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then - idempotent behavior: already cancelled returns success
            assertTrue(result is Result.Success)
        }

        @Test
        @DisplayName("should not emit event when cancelling already cancelled request (idempotent)")
        fun `should not emit event when cancelling already cancelled request`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val userId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val cancelledEvent = VmRequestCancelled(
                aggregateId = requestId,
                reason = "Already cancelled",
                metadata = originalMetadata
            )
            val storedCreated = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val storedCancelled = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCancelled",
                payload = "{}",
                metadata = originalMetadata,
                version = 2
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                userId = userId
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedCreated, storedCancelled)
            coEvery { eventDeserializer.deserialize(storedCreated) } returns createdEvent
            coEvery { eventDeserializer.deserialize(storedCancelled) } returns cancelledEvent

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer)

            // When
            handler.handle(command)

            // Then - no new events should be appended
            coVerify(exactly = 0) { eventStore.append(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("error handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("should return ConcurrencyConflict on event store conflict")
        fun `should return ConcurrencyConflict on event store conflict`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val userId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)
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
                userId = userId
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

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CancelVmRequestError.ConcurrencyConflict)
        }

        @Test
        @DisplayName("should return PersistenceFailure when event store throws exception")
        fun `should return PersistenceFailure when event store throws exception`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val userId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)
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
                userId = userId
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery {
                eventStore.append(any(), any(), any())
            } throws RuntimeException("Database connection failed")

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CancelVmRequestError.PersistenceFailure)
        }

        @Test
        @DisplayName("should return PersistenceFailure when event store load throws exception")
        fun `should return PersistenceFailure when event store load throws exception`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val command = createCommand(requestId = requestId)

            coEvery {
                eventStore.load(requestId.value)
            } throws RuntimeException("Database connection failed")

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is CancelVmRequestError.PersistenceFailure)
        }
    }

    @Nested
    @DisplayName("projection updater integration")
    inner class ProjectionUpdaterTests {

        @Test
        @DisplayName("should call projection updater with correct status after successful cancellation")
        fun `should call projection updater with correct status after successful cancellation`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val userId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)
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
                userId = userId,
                reason = "Project cancelled"
            )
            val updateSlot = slot<VmRequestStatusUpdate>()

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent
            coEvery { eventStore.append(any(), any(), any()) } returns 2L.success()
            coEvery { projectionUpdater.updateStatus(capture(updateSlot)) } returns Unit

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer, projectionUpdater)

            // When
            val result = handler.handle(command)

            // Then
            assertTrue(result is Result.Success)
            coVerify(exactly = 1) { projectionUpdater.updateStatus(any()) }

            val capturedUpdate = updateSlot.captured
            assertEquals(requestId, capturedUpdate.id)
            assertEquals(VmRequestStatus.CANCELLED, capturedUpdate.status)
            assertEquals(2, capturedUpdate.version)
        }

        @Test
        @DisplayName("should not call projection updater when aggregate not found")
        fun `should not call projection updater when aggregate not found`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val command = createCommand(requestId = requestId)

            coEvery { eventStore.load(requestId.value) } returns emptyList()

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer, projectionUpdater)

            // When
            handler.handle(command)

            // Then
            coVerify(exactly = 0) { projectionUpdater.updateStatus(any()) }
        }

        @Test
        @DisplayName("should not call projection updater when authorization fails")
        fun `should not call projection updater when authorization fails`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val originalRequester = UserId.generate()
            val differentUser = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = originalRequester)
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
                userId = differentUser  // Different user trying to cancel
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedEvent)
            coEvery { eventDeserializer.deserialize(storedEvent) } returns createdEvent

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer, projectionUpdater)

            // When
            handler.handle(command)

            // Then
            coVerify(exactly = 0) { projectionUpdater.updateStatus(any()) }
        }

        @Test
        @DisplayName("should not call projection updater when persistence fails")
        fun `should not call projection updater when persistence fails`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val userId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)
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
                userId = userId
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

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer, projectionUpdater)

            // When
            handler.handle(command)

            // Then
            coVerify(exactly = 0) { projectionUpdater.updateStatus(any()) }
        }

        @Test
        @DisplayName("should not call projection updater for idempotent already-cancelled request")
        fun `should not call projection updater for idempotent already-cancelled request`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val userId = UserId.generate()
            val tenantId = TenantId.generate()
            val originalMetadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)
            val createdEvent = createCreatedEvent(
                aggregateId = requestId,
                metadata = originalMetadata
            )
            val cancelledEvent = VmRequestCancelled(
                aggregateId = requestId,
                reason = "Already cancelled",
                metadata = originalMetadata
            )
            val storedCreated = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCreated",
                payload = "{}",
                metadata = originalMetadata,
                version = 1
            )
            val storedCancelled = createStoredEvent(
                aggregateId = requestId,
                eventType = "VmRequestCancelled",
                payload = "{}",
                metadata = originalMetadata,
                version = 2
            )
            val command = createCommand(
                tenantId = tenantId,
                requestId = requestId,
                userId = userId
            )

            coEvery { eventStore.load(requestId.value) } returns listOf(storedCreated, storedCancelled)
            coEvery { eventDeserializer.deserialize(storedCreated) } returns createdEvent
            coEvery { eventDeserializer.deserialize(storedCancelled) } returns cancelledEvent

            val handler = CancelVmRequestHandler(eventStore, eventDeserializer, projectionUpdater)

            // When
            handler.handle(command)

            // Then - Idempotent: projection already updated, don't update again
            coVerify(exactly = 0) { projectionUpdater.updateStatus(any()) }
        }
    }
}
