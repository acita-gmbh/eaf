package de.acci.dvmm.domain.vmrequest

import de.acci.dvmm.domain.vm.VmwareVmId
import de.acci.dvmm.domain.vmrequest.events.VmRequestApproved
import de.acci.dvmm.domain.vmrequest.events.VmRequestCancelled
import de.acci.dvmm.domain.vmrequest.events.VmRequestCreated
import de.acci.dvmm.domain.vmrequest.events.VmRequestProvisioningStarted
import de.acci.dvmm.domain.vmrequest.events.VmRequestReady
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.testing.fixtures.TestMetadataFactory
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("VmRequestAggregate")
class VmRequestAggregateTest {

    @Nested
    @DisplayName("create()")
    inner class CreateTests {

        @Test
        @DisplayName("should create aggregate with VmRequestCreated event")
        fun `should create aggregate with VmRequestCreated event`() {
            // Given
            val projectId = ProjectId.generate()
            val vmName = VmName.of("web-server-01")
            val size = VmSize.M
            val justification = "Test VM for development"
            val metadata = TestMetadataFactory.create()

            // When
            val aggregate = VmRequestAggregate.create(
                requesterId = metadata.userId,
                projectId = projectId,
                vmName = vmName,
                size = size,
                justification = justification,
                requesterEmail = "test@example.com",
                metadata = metadata
            )

            // Then
            assertEquals(1, aggregate.uncommittedEvents.size)
            assertTrue(aggregate.uncommittedEvents[0] is VmRequestCreated)
        }

        @Test
        @DisplayName("should set aggregate ID to new UUID")
        fun `should set aggregate ID to new UUID`() {
            // When
            val aggregate = createValidAggregate()

            // Then
            assertTrue(aggregate.id.value.toString().isNotBlank())
        }

        @Test
        @DisplayName("should set version to 1 after creation")
        fun `should set version to 1 after creation`() {
            // When
            val aggregate = createValidAggregate()

            // Then
            assertEquals(1, aggregate.version)
        }

        @Test
        @DisplayName("should set status to PENDING")
        fun `should set status to PENDING`() {
            // When
            val aggregate = createValidAggregate()

            // Then
            assertEquals(VmRequestStatus.PENDING, aggregate.status)
        }

        @Test
        @DisplayName("should capture tenant from metadata")
        fun `should capture tenant from metadata`() {
            // Given
            val tenantId = TenantId.generate()
            val metadata = TestMetadataFactory.create(tenantId = tenantId)

            // When
            val aggregate = VmRequestAggregate.create(
                requesterId = metadata.userId,
                projectId = ProjectId.generate(),
                vmName = VmName.of("test-vm-01"),
                size = VmSize.S,
                justification = "Capturing tenant test",
                requesterEmail = "test@example.com",
                metadata = metadata
            )

            // Then
            assertEquals(tenantId, aggregate.tenantId)
        }

        @Test
        @DisplayName("should capture requester from metadata")
        fun `should capture requester from metadata`() {
            // Given
            val userId = UserId.generate()
            val metadata = TestMetadataFactory.create(userId = userId)

            // When
            val aggregate = VmRequestAggregate.create(
                requesterId = userId,
                projectId = ProjectId.generate(),
                vmName = VmName.of("test-vm-01"),
                size = VmSize.S,
                justification = "Capturing requester test",
                requesterEmail = "test@example.com",
                metadata = metadata
            )

            // Then
            assertEquals(userId, aggregate.requesterId)
        }

        @Test
        @DisplayName("should reject justification shorter than 10 characters")
        fun `should reject justification shorter than 10 characters`() {
            // When/Then
            val exception = assertThrows<IllegalArgumentException> {
                VmRequestAggregate.create(
                    requesterId = UserId.generate(),
                    projectId = ProjectId.generate(),
                    vmName = VmName.of("test-vm-01"),
                    size = VmSize.S,
                    justification = "Short",
                    requesterEmail = "test@example.com",
                    metadata = TestMetadataFactory.create()
                )
            }

            assertTrue(exception.message?.contains("10 characters") == true)
        }

        @Test
        @DisplayName("should accept justification with exactly 10 characters")
        fun `should accept justification with exactly 10 characters`() {
            // Given
            val justification = "0123456789" // exactly 10 chars

            // When
            val aggregate = VmRequestAggregate.create(
                requesterId = UserId.generate(),
                projectId = ProjectId.generate(),
                vmName = VmName.of("test-vm-01"),
                size = VmSize.S,
                justification = justification,
                requesterEmail = "test@example.com",
                metadata = TestMetadataFactory.create()
            )

            // Then
            assertEquals(justification, aggregate.justification)
        }

        @Test
        @DisplayName("should capture all field values correctly")
        fun `should capture all field values correctly`() {
            // Given
            val projectId = ProjectId.generate()
            val vmName = VmName.of("database-prod")
            val size = VmSize.XL
            val justification = "Production database server"
            val metadata = TestMetadataFactory.create()

            // When
            val aggregate = VmRequestAggregate.create(
                requesterId = metadata.userId,
                projectId = projectId,
                vmName = vmName,
                size = size,
                justification = justification,
                requesterEmail = "test@example.com",
                metadata = metadata
            )

            // Then
            assertEquals(projectId, aggregate.projectId)
            assertEquals(vmName, aggregate.vmName)
            assertEquals(size, aggregate.size)
            assertEquals(justification, aggregate.justification)
        }
    }

    @Nested
    @DisplayName("reconstitute()")
    inner class ReconstituteTests {

        @Test
        @DisplayName("should reconstitute state from events")
        fun `should reconstitute state from events`() {
            // Given
            val requestId = VmRequestId.generate()
            val projectId = ProjectId.generate()
            val vmName = VmName.of("reconstituted-vm")
            val size = VmSize.L
            val justification = "Reconstitution test justification"
            val metadata = TestMetadataFactory.create()

            val events = listOf(
                VmRequestCreated(
                    aggregateId = requestId,
                    projectId = projectId,
                    vmName = vmName,
                    size = size,
                    justification = justification,
                    requesterEmail = "test@example.com",
                    metadata = metadata
                )
            )

            // When
            val aggregate = VmRequestAggregate.reconstitute(requestId, events)

            // Then
            assertEquals(requestId, aggregate.id)
            assertEquals(projectId, aggregate.projectId)
            assertEquals(vmName, aggregate.vmName)
            assertEquals(size, aggregate.size)
            assertEquals(justification, aggregate.justification)
            assertEquals(VmRequestStatus.PENDING, aggregate.status)
        }

        @Test
        @DisplayName("should have empty uncommittedEvents after reconstitution")
        fun `should have empty uncommittedEvents after reconstitution`() {
            // Given
            val aggregate = createValidAggregate()
            val events = aggregate.uncommittedEvents.toList()
            aggregate.clearUncommittedEvents()

            // When
            val reconstituted = VmRequestAggregate.reconstitute(aggregate.id, events)

            // Then
            assertTrue(reconstituted.uncommittedEvents.isEmpty())
        }

        @Test
        @DisplayName("should set version to event count after reconstitution")
        fun `should set version to event count after reconstitution`() {
            // Given
            val aggregate = createValidAggregate()
            val events = aggregate.uncommittedEvents.toList()

            // When
            val reconstituted = VmRequestAggregate.reconstitute(aggregate.id, events)

            // Then
            assertEquals(1, reconstituted.version)
        }

        @Test
        @DisplayName("should handle empty events list")
        fun `should handle empty events list`() {
            // Given
            val id = VmRequestId.generate()

            // When
            val aggregate = VmRequestAggregate.reconstitute(id, emptyList())

            // Then
            assertEquals(id, aggregate.id)
            assertEquals(0, aggregate.version)
        }

        @Test
        @DisplayName("should reconstitute to READY state from full event history")
        fun `should reconstitute to READY state from full event history`() {
            // Given: Complete event history from PENDING -> APPROVED -> PROVISIONING -> READY
            val requestId = VmRequestId.generate()
            val projectId = ProjectId.generate()
            val vmName = VmName.of("ready-vm-test")
            val metadata = TestMetadataFactory.create()
            val adminId = UserId.generate()
            val vmwareVmId = VmwareVmId.of("vm-12345")
            val ipAddress = "10.0.0.100"
            val hostname = "ready-vm-test.local"
            val provisionedAt = Instant.now()

            val events = listOf(
                VmRequestCreated(
                    aggregateId = requestId,
                    projectId = projectId,
                    vmName = vmName,
                    size = VmSize.L,
                    justification = "Reconstitution READY test",
                    requesterEmail = "test@example.com",
                    metadata = metadata
                ),
                VmRequestApproved(
                    aggregateId = requestId,
                    vmName = vmName,
                    projectId = projectId,
                    requesterId = metadata.userId,
                    requesterEmail = "test@example.com",
                    metadata = TestMetadataFactory.create(userId = adminId)
                ),
                VmRequestProvisioningStarted(
                    aggregateId = requestId,
                    metadata = TestMetadataFactory.create()
                ),
                VmRequestReady(
                    aggregateId = requestId,
                    vmwareVmId = vmwareVmId,
                    ipAddress = ipAddress,
                    hostname = hostname,
                    provisionedAt = provisionedAt,
                    warningMessage = null,
                    metadata = TestMetadataFactory.create()
                )
            )

            // When: Reconstitute from event history
            val aggregate = VmRequestAggregate.reconstitute(requestId, events)

            // Then: Aggregate is in READY state with all properties preserved
            assertEquals(requestId, aggregate.id)
            assertEquals(VmRequestStatus.READY, aggregate.status)
            assertEquals(projectId, aggregate.projectId)
            assertEquals(vmName, aggregate.vmName)
            assertEquals(VmSize.L, aggregate.size)
            assertEquals(4, aggregate.version)
            assertTrue(aggregate.uncommittedEvents.isEmpty())
        }

        @Test
        @DisplayName("should reconstitute READY state with null IP and warning")
        fun `should reconstitute READY state with null IP and warning`() {
            // Given: Events with VMware Tools timeout scenario
            val requestId = VmRequestId.generate()
            val projectId = ProjectId.generate()
            val vmName = VmName.of("ready-warning-vm")
            val metadata = TestMetadataFactory.create()
            val warningMessage = "VMware Tools timeout - IP detection pending"

            val events = listOf(
                VmRequestCreated(
                    aggregateId = requestId,
                    projectId = projectId,
                    vmName = vmName,
                    size = VmSize.M,
                    justification = "Reconstitution warning test",
                    requesterEmail = "test@example.com",
                    metadata = metadata
                ),
                VmRequestApproved(
                    aggregateId = requestId,
                    vmName = vmName,
                    projectId = projectId,
                    requesterId = metadata.userId,
                    requesterEmail = "test@example.com",
                    metadata = TestMetadataFactory.create(userId = UserId.generate())
                ),
                VmRequestProvisioningStarted(
                    aggregateId = requestId,
                    metadata = TestMetadataFactory.create()
                ),
                VmRequestReady(
                    aggregateId = requestId,
                    vmwareVmId = VmwareVmId.of("vm-67890"),
                    ipAddress = null,
                    hostname = "ready-warning-vm",
                    provisionedAt = Instant.now(),
                    warningMessage = warningMessage,
                    metadata = TestMetadataFactory.create()
                )
            )

            // When: Reconstitute
            val aggregate = VmRequestAggregate.reconstitute(requestId, events)

            // Then: READY state achieved even without IP
            assertEquals(VmRequestStatus.READY, aggregate.status)
            assertEquals(4, aggregate.version)
        }
    }

    @Nested
    @DisplayName("VmRequestCreated event data")
    inner class EventDataTests {

        @Test
        @DisplayName("event should contain correct aggregate type")
        fun `event should contain correct aggregate type`() {
            // Given
            val aggregate = createValidAggregate()
            val event = aggregate.uncommittedEvents[0] as VmRequestCreated

            // Then
            assertEquals("VmRequest", event.aggregateType)
        }

        @Test
        @DisplayName("event should contain aggregate ID")
        fun `event should contain aggregate ID`() {
            // Given
            val aggregate = createValidAggregate()
            val event = aggregate.uncommittedEvents[0] as VmRequestCreated

            // Then
            assertEquals(aggregate.id, event.aggregateId)
        }

        @Test
        @DisplayName("event should contain metadata with tenant context")
        fun `event should contain metadata with tenant context`() {
            // Given
            val tenantId = TenantId.generate()
            val userId = UserId.generate()
            val metadata = TestMetadataFactory.create(tenantId = tenantId, userId = userId)

            val aggregate = VmRequestAggregate.create(
                requesterId = userId,
                projectId = ProjectId.generate(),
                vmName = VmName.of("test-vm-01"),
                size = VmSize.S,
                justification = "Event metadata test",
                requesterEmail = "test@example.com",
                metadata = metadata
            )

            val event = aggregate.uncommittedEvents[0] as VmRequestCreated

            // Then
            assertEquals(tenantId, event.metadata.tenantId)
            assertEquals(userId, event.metadata.userId)
        }
    }

    @Nested
    @DisplayName("cancel()")
    inner class CancelTests {

        @Test
        @DisplayName("should reject reason exceeding max length")
        fun `should reject reason exceeding max length`() {
            // Given
            val aggregate = createValidAggregate()
            aggregate.clearUncommittedEvents()
            val tooLongReason = "a".repeat(VmRequestCancelled.MAX_REASON_LENGTH + 1)

            // When/Then
            val exception = assertThrows<IllegalArgumentException> {
                aggregate.cancel(
                    reason = tooLongReason,
                    metadata = TestMetadataFactory.create()
                )
            }

            assertTrue(exception.message!!.contains("${VmRequestCancelled.MAX_REASON_LENGTH}"))
        }

        @Test
        @DisplayName("should accept reason at max length")
        fun `should accept reason at max length`() {
            // Given
            val aggregate = createValidAggregate()
            aggregate.clearUncommittedEvents()
            val maxLengthReason = "a".repeat(VmRequestCancelled.MAX_REASON_LENGTH)

            // When
            aggregate.cancel(
                reason = maxLengthReason,
                metadata = TestMetadataFactory.create()
            )

            // Then
            assertEquals(VmRequestStatus.CANCELLED, aggregate.status)
            val event = aggregate.uncommittedEvents[0] as VmRequestCancelled
            assertEquals(maxLengthReason, event.reason)
        }

        @Test
        @DisplayName("should accept null reason")
        fun `should accept null reason`() {
            // Given
            val aggregate = createValidAggregate()
            aggregate.clearUncommittedEvents()

            // When
            aggregate.cancel(
                reason = null,
                metadata = TestMetadataFactory.create()
            )

            // Then
            assertEquals(VmRequestStatus.CANCELLED, aggregate.status)
            val event = aggregate.uncommittedEvents[0] as VmRequestCancelled
            assertEquals(null, event.reason)
        }
    }

    @Nested
    @DisplayName("markProvisioning()")
    inner class MarkProvisioningTests {

        @Test
        @DisplayName("should transition from APPROVED to PROVISIONING")
        fun `should transition from APPROVED to PROVISIONING`() {
            // Given
            val aggregate = createApprovedAggregate()

            // When
            aggregate.markProvisioning(TestMetadataFactory.create())

            // Then
            assertEquals(VmRequestStatus.PROVISIONING, aggregate.status)
        }

        @Test
        @DisplayName("should throw InvalidStateException when not APPROVED")
        fun `should throw InvalidStateException when not APPROVED`() {
            val aggregate = createValidAggregate() // PENDING
            assertThrows<de.acci.dvmm.domain.exceptions.InvalidStateException> {
                aggregate.markProvisioning(TestMetadataFactory.create())
            }
        }
    }

    @Nested
    @DisplayName("markReady()")
    inner class MarkReadyTests {

        @Test
        @DisplayName("should emit VmRequestReady event")
        fun `should emit VmRequestReady event`() {
            // Given
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()
            val vmwareVmId = VmwareVmId.of("vm-12345")
            val ipAddress = "10.0.0.100"
            val hostname = "test-vm-01.local"

            // When
            aggregate.markReady(
                vmwareVmId = vmwareVmId,
                ipAddress = ipAddress,
                hostname = hostname,
                provisionedAt = Instant.now(),
                warningMessage = null,
                metadata = TestMetadataFactory.create()
            )

            // Then
            assertEquals(1, aggregate.uncommittedEvents.size)
            val event = aggregate.uncommittedEvents.first() as VmRequestReady

            assertEquals(aggregate.id, event.aggregateId)
            assertEquals(vmwareVmId, event.vmwareVmId)
            assertEquals(ipAddress, event.ipAddress)
            assertEquals(hostname, event.hostname)
            assertEquals(null, event.warningMessage)
        }

        @Test
        @DisplayName("should transition from PROVISIONING to READY")
        fun `should transition from PROVISIONING to READY`() {
            // Given
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()

            // When
            aggregate.markReady(
                vmwareVmId = VmwareVmId.of("vm-12345"),
                ipAddress = "10.0.0.100",
                hostname = "test-vm-01.local",
                provisionedAt = Instant.now(),
                warningMessage = null,
                metadata = TestMetadataFactory.create()
            )

            // Then
            assertEquals(VmRequestStatus.READY, aggregate.status)
        }

        @Test
        @DisplayName("should handle null ipAddress with warning message")
        fun `should handle null ipAddress with warning message`() {
            // Given - IP detection timed out scenario
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()
            val warningMessage = "VMware Tools timeout - IP detection pending"

            // When
            aggregate.markReady(
                vmwareVmId = VmwareVmId.of("vm-12345"),
                ipAddress = null,
                hostname = "test-vm-01",
                provisionedAt = Instant.now(),
                warningMessage = warningMessage,
                metadata = TestMetadataFactory.create()
            )

            // Then
            val event = aggregate.uncommittedEvents.first() as VmRequestReady
            assertEquals(null, event.ipAddress)
            assertEquals(warningMessage, event.warningMessage)
            assertEquals(VmRequestStatus.READY, aggregate.status)
        }

        @Test
        @DisplayName("should throw InvalidStateException when not PROVISIONING")
        fun `should throw InvalidStateException when not PROVISIONING`() {
            // Given - APPROVED state (not yet PROVISIONING)
            val aggregate = createApprovedAggregate()
            aggregate.clearUncommittedEvents()

            // When/Then
            assertThrows<de.acci.dvmm.domain.exceptions.InvalidStateException> {
                aggregate.markReady(
                    vmwareVmId = VmwareVmId.of("vm-12345"),
                    ipAddress = "10.0.0.100",
                    hostname = "test-vm-01",
                    provisionedAt = Instant.now(),
                    warningMessage = null,
                    metadata = TestMetadataFactory.create()
                )
            }
        }
    }

    private fun createValidAggregate(): VmRequestAggregate {
        return VmRequestAggregate.create(
            requesterId = UserId.generate(),
            projectId = ProjectId.generate(),
            vmName = VmName.of("test-vm-01"),
            size = VmSize.M,
            justification = "Valid justification for testing",
            requesterEmail = "test@example.com",
            metadata = TestMetadataFactory.create()
        )
    }

    private fun createApprovedAggregate(): VmRequestAggregate {
        val aggregate = createValidAggregate()
        aggregate.approve(UserId.generate(), TestMetadataFactory.create())
        return aggregate
    }

    private fun createProvisioningAggregate(): VmRequestAggregate {
        val aggregate = createApprovedAggregate()
        aggregate.markProvisioning(TestMetadataFactory.create())
        return aggregate
    }
}
