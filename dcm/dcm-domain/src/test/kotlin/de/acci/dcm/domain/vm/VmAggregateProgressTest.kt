package de.acci.dcm.domain.vm

import de.acci.dcm.domain.vm.events.VmProvisioningProgressUpdated
import de.acci.dcm.domain.vmrequest.ProjectId
import de.acci.dcm.domain.vmrequest.VmName
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.dcm.domain.vmrequest.VmSize
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.EventMetadata
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("VmAggregate Progress")
class VmAggregateProgressTest {

    private val tenantId = TenantId.fromString("11111111-1111-1111-1111-111111111111")
    private val userId = UserId.fromString("22222222-2222-2222-2222-222222222222")
    private val metadata = EventMetadata.create(tenantId = tenantId, userId = userId)
    private val requestId = VmRequestId.fromString("33333333-3333-3333-3333-333333333333")
    private val projectId = ProjectId.fromString("44444444-4444-4444-4444-444444444444")
    private val vmName = VmName.of("test-vm-progress")
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
    @DisplayName("updateProgress")
    inner class UpdateProgress {

        @Test
        fun `should emit VmProvisioningProgressUpdated event`() {
            // Given
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()
            val stage = VmProvisioningStage.CLONING

            // When
            aggregate.updateProgress(stage, metadata)

            // Then
            assertEquals(1, aggregate.uncommittedEvents.size)
            val event = aggregate.uncommittedEvents.first() as VmProvisioningProgressUpdated

            assertEquals(aggregate.id, event.aggregateId)
            assertEquals(requestId, event.requestId)
            assertEquals(stage, event.currentStage)
            assertEquals("Provisioning stage updated to CLONING", event.details)
        }

        @Test
        fun `should update currentStage state`() {
            // Given
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()

            // When
            aggregate.updateProgress(VmProvisioningStage.CONFIGURING, metadata)

            // Then
            // Progress updates don't change the overall provisioning status
            assertEquals(VmStatus.PROVISIONING, aggregate.status)
        }
        
        @Test
        fun `should throw when updating progress for failed aggregate`() {
             // Given
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()
            @Suppress("DEPRECATION")
            aggregate.markFailed("Failed", Instant.now(), metadata)
            aggregate.clearUncommittedEvents()

            // When/Then
            assertThrows(IllegalStateException::class.java) {
                aggregate.updateProgress(VmProvisioningStage.POWERING_ON, metadata)
            }
        }

        @Test
        fun `should throw when updating progress for ready aggregate`() {
            // Given
            val aggregate = createProvisioningAggregate()
            aggregate.clearUncommittedEvents()
            aggregate.markProvisioned(
                vmwareVmId = VmwareVmId.of("vm-123"),
                ipAddress = "192.168.1.100",
                hostname = "test-vm",
                warningMessage = null,
                metadata = metadata
            )
            aggregate.clearUncommittedEvents()

            // When/Then
            assertThrows(IllegalStateException::class.java) {
                aggregate.updateProgress(VmProvisioningStage.CLONING, metadata)
            }
        }
    }
}
