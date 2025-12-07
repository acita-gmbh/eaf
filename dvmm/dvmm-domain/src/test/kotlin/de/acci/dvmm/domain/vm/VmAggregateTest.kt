package de.acci.dvmm.domain.vm

import de.acci.dvmm.domain.vm.events.VmProvisioningFailed
import de.acci.dvmm.domain.vm.events.VmProvisioningStarted
import de.acci.dvmm.domain.vm.events.VmProvisioned
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmName
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.EventMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("VmAggregate")
class VmAggregateTest {

    private val tenantId = TenantId.fromString("11111111-1111-1111-1111-111111111111")
    private val userId = UserId.fromString("22222222-2222-2222-2222-222222222222")
    private val metadata = EventMetadata.create(tenantId = tenantId, userId = userId)
    private val requestId = VmRequestId.fromString("33333333-3333-3333-3333-333333333333")
    private val projectId = ProjectId.fromString("44444444-4444-4444-4444-444444444444")
    private val vmName = VmName.of("test-vm-01")
    private val size = VmSize.L

    private fun createProvisioningAggregate(): VmAggregate = VmAggregate.startProvisioning(
        requestId = requestId,
        projectId = projectId,
        vmName = vmName,
        size = size,
        requesterId = userId,
        metadata = metadata
    )

    @Nested
    @DisplayName("startProvisioning")
    inner class StartProvisioning {

        @Test
        fun `should emit VmProvisioningStarted event`() {
            // When
            val aggregate = createProvisioningAggregate()

            // Then
            assertEquals(1, aggregate.uncommittedEvents.size)
            val event = aggregate.uncommittedEvents.first() as VmProvisioningStarted

            assertEquals(aggregate.id, event.aggregateId)
            assertEquals(requestId, event.requestId)
            assertEquals(projectId, event.projectId)
            assertEquals(vmName, event.vmName)
            assertEquals(size, event.size)
            assertEquals(userId, event.requesterId)
            assertEquals(metadata.tenantId, event.metadata.tenantId)
        }

        @Test
        fun `should set status to PROVISIONING`() {
            // When
            val aggregate = createProvisioningAggregate()

            // Then
            assertEquals(VmStatus.PROVISIONING, aggregate.status)
        }
    }

    @Nested
    @DisplayName("markFailed")
    inner class MarkFailed {

        @Test
        fun `should emit VmProvisioningFailed event`() {
            // Given
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()
            val reason = "VMware configuration missing"

            // When
            aggregate.markFailed(reason, metadata)

            // Then
            assertEquals(1, aggregate.uncommittedEvents.size)
            val event = aggregate.uncommittedEvents.first() as VmProvisioningFailed

            assertEquals(aggregate.id, event.aggregateId)
            assertEquals(requestId, event.requestId)
            assertEquals(reason, event.reason)
        }

        @Test
        fun `should set status to FAILED`() {
            // Given
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()

            // When
            aggregate.markFailed("Connection timeout", metadata)

            // Then
            assertEquals(VmStatus.FAILED, aggregate.status)
        }

        @Test
        fun `should store failure reason`() {
            // Given
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()
            val reason = "vSphere API error: ResourcePool not found"

            // When
            aggregate.markFailed(reason, metadata)

            // Then
            assertEquals(reason, aggregate.failureReason)
        }

        @Test
        fun `should throw when marking non-provisioning aggregate as failed`() {
            // Given
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()
            aggregate.markFailed("First failure", metadata)
            aggregate.clearUncommittedEvents()

            // When/Then - check() throws IllegalStateException for state validation
            assertThrows(IllegalStateException::class.java) {
                aggregate.markFailed("Second failure", metadata)
            }
        }
    }

    @Nested
    @DisplayName("markProvisioned")
    inner class MarkProvisioned {

        @Test
        fun `should emit VmProvisioned event`() {
            // Given
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()
            val vmwareVmId = VmwareVmId.of("vm-12345")
            val ipAddress = "10.0.0.100"
            val hostname = "test-vm-01.local"

            // When
            aggregate.markProvisioned(
                vmwareVmId = vmwareVmId,
                ipAddress = ipAddress,
                hostname = hostname,
                warningMessage = null,
                metadata = metadata
            )

            // Then
            assertEquals(1, aggregate.uncommittedEvents.size)
            val event = aggregate.uncommittedEvents.first() as VmProvisioned

            assertEquals(aggregate.id, event.aggregateId)
            assertEquals(requestId, event.requestId)
            assertEquals(vmwareVmId, event.vmwareVmId)
            assertEquals(ipAddress, event.ipAddress)
            assertEquals(hostname, event.hostname)
            assertEquals(null, event.warningMessage)
        }

        @Test
        fun `should set status to READY`() {
            // Given
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()

            // When
            aggregate.markProvisioned(
                vmwareVmId = VmwareVmId.of("vm-12345"),
                ipAddress = "10.0.0.100",
                hostname = "test-vm-01.local",
                warningMessage = null,
                metadata = metadata
            )

            // Then
            assertEquals(VmStatus.READY, aggregate.status)
        }

        @Test
        fun `should store vmwareVmId, ipAddress, and hostname`() {
            // Given
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()
            val vmwareVmId = VmwareVmId.of("vm-99999")
            val ipAddress = "192.168.1.50"
            val hostname = "prod-vm-01.local"

            // When
            aggregate.markProvisioned(
                vmwareVmId = vmwareVmId,
                ipAddress = ipAddress,
                hostname = hostname,
                warningMessage = null,
                metadata = metadata
            )

            // Then
            assertEquals(vmwareVmId, aggregate.vmwareVmId)
            assertEquals(ipAddress, aggregate.ipAddress)
            assertEquals(hostname, aggregate.hostname)
        }

        @Test
        fun `should handle null ipAddress with warning message`() {
            // Given - IP detection timed out scenario
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()
            val warningMessage = "VMware Tools timeout - IP detection pending"

            // When
            aggregate.markProvisioned(
                vmwareVmId = VmwareVmId.of("vm-12345"),
                ipAddress = null,
                hostname = "test-vm-01",
                warningMessage = warningMessage,
                metadata = metadata
            )

            // Then
            val event = aggregate.uncommittedEvents.first() as VmProvisioned
            assertEquals(null, event.ipAddress)
            assertEquals(warningMessage, event.warningMessage)
            assertEquals(VmStatus.READY, aggregate.status)
        }

        @Test
        fun `should throw when marking non-provisioning aggregate as provisioned`() {
            // Given - VM already failed
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()
            aggregate.markFailed("Initial failure", metadata)
            aggregate.clearUncommittedEvents()

            // When/Then
            assertThrows(IllegalStateException::class.java) {
                aggregate.markProvisioned(
                    vmwareVmId = VmwareVmId.of("vm-12345"),
                    ipAddress = "10.0.0.100",
                    hostname = "test-vm-01",
                    warningMessage = null,
                    metadata = metadata
                )
            }
        }
    }

    @Nested
    @DisplayName("reconstitute")
    inner class Reconstitute {

        @Test
        fun `should reconstitute from VmProvisioningStarted`() {
            // Given
            val vmId = VmId.generate()
            val event = VmProvisioningStarted(
                aggregateId = vmId,
                requestId = requestId,
                projectId = projectId,
                vmName = vmName,
                size = size,
                requesterId = userId,
                metadata = metadata
            )

            // When
            val aggregate = VmAggregate.reconstitute(vmId, listOf(event))

            // Then
            assertEquals(vmId, aggregate.id)
            assertEquals(VmStatus.PROVISIONING, aggregate.status)
            assertEquals(requestId, aggregate.requestId)
        }

        @Test
        fun `should reconstitute from VmProvisioningStarted and VmProvisioningFailed`() {
            // Given
            val vmId = VmId.generate()
            val startedEvent = VmProvisioningStarted(
                aggregateId = vmId,
                requestId = requestId,
                projectId = projectId,
                vmName = vmName,
                size = size,
                requesterId = userId,
                metadata = metadata
            )
            val failedEvent = VmProvisioningFailed(
                aggregateId = vmId,
                requestId = requestId,
                reason = "Config missing",
                metadata = metadata
            )

            // When
            val aggregate = VmAggregate.reconstitute(vmId, listOf(startedEvent, failedEvent))

            // Then
            assertEquals(vmId, aggregate.id)
            assertEquals(VmStatus.FAILED, aggregate.status)
            assertEquals("Config missing", aggregate.failureReason)
        }

        @Test
        fun `should reconstitute from VmProvisioningStarted and VmProvisioned`() {
            // Given
            val vmId = VmId.generate()
            val vmwareVmId = VmwareVmId.of("vm-12345")
            val ipAddress = "10.0.0.100"
            val hostname = "test-vm-01.local"

            val startedEvent = VmProvisioningStarted(
                aggregateId = vmId,
                requestId = requestId,
                projectId = projectId,
                vmName = vmName,
                size = size,
                requesterId = userId,
                metadata = metadata
            )
            val provisionedEvent = VmProvisioned(
                aggregateId = vmId,
                requestId = requestId,
                vmwareVmId = vmwareVmId,
                ipAddress = ipAddress,
                hostname = hostname,
                warningMessage = null,
                metadata = metadata
            )

            // When
            val aggregate = VmAggregate.reconstitute(vmId, listOf(startedEvent, provisionedEvent))

            // Then
            assertEquals(vmId, aggregate.id)
            assertEquals(VmStatus.READY, aggregate.status)
            assertEquals(vmwareVmId, aggregate.vmwareVmId)
            assertEquals(ipAddress, aggregate.ipAddress)
            assertEquals(hostname, aggregate.hostname)
        }

        @Test
        fun `should reconstitute with null ipAddress when VMware Tools timed out`() {
            // Given
            val vmId = VmId.generate()
            val vmwareVmId = VmwareVmId.of("vm-12345")
            val hostname = "test-vm-01"
            val warningMessage = "VMware Tools timeout - IP detection pending"

            val startedEvent = VmProvisioningStarted(
                aggregateId = vmId,
                requestId = requestId,
                projectId = projectId,
                vmName = vmName,
                size = size,
                requesterId = userId,
                metadata = metadata
            )
            val provisionedEvent = VmProvisioned(
                aggregateId = vmId,
                requestId = requestId,
                vmwareVmId = vmwareVmId,
                ipAddress = null,
                hostname = hostname,
                warningMessage = warningMessage,
                metadata = metadata
            )

            // When
            val aggregate = VmAggregate.reconstitute(vmId, listOf(startedEvent, provisionedEvent))

            // Then
            assertEquals(vmId, aggregate.id)
            assertEquals(VmStatus.READY, aggregate.status)
            assertEquals(vmwareVmId, aggregate.vmwareVmId)
            assertEquals(null, aggregate.ipAddress)
            assertEquals(hostname, aggregate.hostname)
        }
    }
}
