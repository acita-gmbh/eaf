package de.acci.dcm.infrastructure.projection

import de.acci.dcm.application.vmrequest.NewVmRequestProjection
import de.acci.dcm.application.vmrequest.VmRequestStatusUpdate
import de.acci.dcm.domain.vmrequest.ProjectId
import de.acci.dcm.domain.vmrequest.VmName
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.dcm.domain.vmrequest.VmRequestStatus
import de.acci.dcm.domain.vmrequest.VmSize
import de.acci.dcm.infrastructure.jooq.`public`.tables.pojos.VmRequestsProjection
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.ProjectionError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("VmRequestProjectionUpdaterAdapter")
class VmRequestProjectionUpdaterAdapterTest {

    private val projectionRepository = mockk<VmRequestProjectionRepository>()
    private lateinit var adapter: VmRequestProjectionUpdaterAdapter

    private val testTenantId = TenantId(UUID.randomUUID())
    private val testRequesterId = UserId(UUID.randomUUID())
    private val testProjectId = ProjectId.generate()
    private val testVmRequestId = VmRequestId.generate()

    @BeforeEach
    fun setup() {
        adapter = VmRequestProjectionUpdaterAdapter(projectionRepository)
    }

    @Nested
    @DisplayName("insert")
    inner class Insert {

        @Test
        fun `inserts projection with correct field mappings and returns success`() = runTest {
            // Given: A new projection to insert
            val projection = NewVmRequestProjection(
                id = testVmRequestId,
                tenantId = testTenantId,
                requesterId = testRequesterId,
                requesterName = "John Doe",
                projectId = testProjectId,
                projectName = "Project Alpha",
                vmName = VmName.create("web-server-01").getOrThrow(),
                size = VmSize.L,
                justification = "Production web server",
                status = VmRequestStatus.PENDING,
                createdAt = Instant.now(),
                version = 1
            )

            val capturedProjection = slot<VmRequestsProjection>()
            coEvery { projectionRepository.insert(capture(capturedProjection)) } returns Unit

            // When: Insert the projection
            val result = adapter.insert(projection)

            // Then: Returns success
            assertTrue(result is Result.Success)

            // And: Repository was called with correct mapping
            coVerify(exactly = 1) { projectionRepository.insert(any()) }

            val inserted = capturedProjection.captured
            assertEquals(testVmRequestId.value, inserted.id)
            assertEquals(testTenantId.value, inserted.tenantId)
            assertEquals(testRequesterId.value, inserted.requesterId)
            assertEquals("John Doe", inserted.requesterName)
            assertEquals(testProjectId.value, inserted.projectId)
            assertEquals("Project Alpha", inserted.projectName)
            assertEquals("web-server-01", inserted.vmName)
            assertEquals("L", inserted.size)
            assertEquals(VmSize.L.cpuCores, inserted.cpuCores)
            assertEquals(VmSize.L.memoryGb, inserted.memoryGb)
            assertEquals(VmSize.L.diskGb, inserted.diskGb)
            assertEquals("Production web server", inserted.justification)
            assertEquals("PENDING", inserted.status)
            assertEquals(1, inserted.version)
            assertNull(inserted.approvedBy)
            assertNull(inserted.approvedByName)
            assertNull(inserted.rejectedBy)
            assertNull(inserted.rejectedByName)
            assertNull(inserted.rejectionReason)
        }

        @Test
        fun `maps VmSize S correctly`() = runTest {
            val projection = NewVmRequestProjection(
                id = testVmRequestId,
                tenantId = testTenantId,
                requesterId = testRequesterId,
                requesterName = "Test User",
                projectId = testProjectId,
                projectName = "Test Project",
                vmName = VmName.create("small-vm").getOrThrow(),
                size = VmSize.S,
                justification = "Small VM",
                status = VmRequestStatus.PENDING,
                createdAt = Instant.now(),
                version = 1
            )

            val capturedProjection = slot<VmRequestsProjection>()
            coEvery { projectionRepository.insert(capture(capturedProjection)) } returns Unit

            adapter.insert(projection)

            val inserted = capturedProjection.captured
            assertEquals("S", inserted.size)
            assertEquals(VmSize.S.cpuCores, inserted.cpuCores)
            assertEquals(VmSize.S.memoryGb, inserted.memoryGb)
            assertEquals(VmSize.S.diskGb, inserted.diskGb)
        }

        @Test
        fun `maps VmSize XL correctly`() = runTest {
            val projection = NewVmRequestProjection(
                id = testVmRequestId,
                tenantId = testTenantId,
                requesterId = testRequesterId,
                requesterName = "Test User",
                projectId = testProjectId,
                projectName = "Test Project",
                vmName = VmName.create("extra-large-vm").getOrThrow(),
                size = VmSize.XL,
                justification = "Extra large VM",
                status = VmRequestStatus.PENDING,
                createdAt = Instant.now(),
                version = 1
            )

            val capturedProjection = slot<VmRequestsProjection>()
            coEvery { projectionRepository.insert(capture(capturedProjection)) } returns Unit

            adapter.insert(projection)

            val inserted = capturedProjection.captured
            assertEquals("XL", inserted.size)
            assertEquals(VmSize.XL.cpuCores, inserted.cpuCores)
            assertEquals(VmSize.XL.memoryGb, inserted.memoryGb)
            assertEquals(VmSize.XL.diskGb, inserted.diskGb)
        }

        @Test
        fun `returns DatabaseError on insert failure`() = runTest {
            // Given: Insert will fail
            val projection = NewVmRequestProjection(
                id = testVmRequestId,
                tenantId = testTenantId,
                requesterId = testRequesterId,
                requesterName = "Test User",
                projectId = testProjectId,
                projectName = "Test Project",
                vmName = VmName.create("test-vm").getOrThrow(),
                size = VmSize.M,
                justification = "Test",
                status = VmRequestStatus.PENDING,
                createdAt = Instant.now(),
                version = 1
            )
            coEvery { projectionRepository.insert(any()) } throws RuntimeException("Database error")

            // When: Insert
            val result = adapter.insert(projection)

            // Then: Returns failure with DatabaseError
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is ProjectionError.DatabaseError)
            assertEquals(testVmRequestId.value.toString(), (error as ProjectionError.DatabaseError).aggregateId)
            assertTrue(error.message.contains("Database error"))
            coVerify(exactly = 1) { projectionRepository.insert(any()) }
        }
    }

    @Nested
    @DisplayName("updateStatus")
    inner class UpdateStatus {

        @Test
        fun `updates to CANCELLED status and returns success`() = runTest {
            // Given: A status update to CANCELLED
            val update = VmRequestStatusUpdate(
                id = testVmRequestId,
                status = VmRequestStatus.CANCELLED,
                version = 2
            )
            coEvery {
                projectionRepository.updateStatus(
                    id = testVmRequestId.value,
                    status = "CANCELLED",
                    approvedBy = null,
                    approvedByName = null,
                    rejectedBy = null,
                    rejectedByName = null,
                    rejectionReason = null,
                    version = 2
                )
            } returns 1

            // When: Update status
            val result = adapter.updateStatus(update)

            // Then: Returns success
            assertTrue(result is Result.Success)

            // And: Repository was called with correct parameters
            coVerify(exactly = 1) {
                projectionRepository.updateStatus(
                    id = testVmRequestId.value,
                    status = "CANCELLED",
                    approvedBy = null,
                    approvedByName = null,
                    rejectedBy = null,
                    rejectedByName = null,
                    rejectionReason = null,
                    version = 2
                )
            }
        }

        @Test
        fun `updates to APPROVED status with approver details`() = runTest {
            // Given: A status update to APPROVED
            val approverId = UserId(UUID.randomUUID())
            val update = VmRequestStatusUpdate(
                id = testVmRequestId,
                status = VmRequestStatus.APPROVED,
                approvedBy = approverId,
                approvedByName = "Jane Admin",
                version = 2
            )
            coEvery {
                projectionRepository.updateStatus(
                    id = testVmRequestId.value,
                    status = "APPROVED",
                    approvedBy = approverId.value,
                    approvedByName = "Jane Admin",
                    rejectedBy = null,
                    rejectedByName = null,
                    rejectionReason = null,
                    version = 2
                )
            } returns 1

            // When: Update status
            adapter.updateStatus(update)

            // Then: Repository was called with approver details
            coVerify(exactly = 1) {
                projectionRepository.updateStatus(
                    id = testVmRequestId.value,
                    status = "APPROVED",
                    approvedBy = approverId.value,
                    approvedByName = "Jane Admin",
                    rejectedBy = null,
                    rejectedByName = null,
                    rejectionReason = null,
                    version = 2
                )
            }
        }

        @Test
        fun `updates to REJECTED status with rejector details and reason`() = runTest {
            // Given: A status update to REJECTED
            val rejectorId = UserId(UUID.randomUUID())
            val update = VmRequestStatusUpdate(
                id = testVmRequestId,
                status = VmRequestStatus.REJECTED,
                rejectedBy = rejectorId,
                rejectedByName = "Bob Reviewer",
                rejectionReason = "Insufficient justification",
                version = 2
            )
            coEvery {
                projectionRepository.updateStatus(
                    id = testVmRequestId.value,
                    status = "REJECTED",
                    approvedBy = null,
                    approvedByName = null,
                    rejectedBy = rejectorId.value,
                    rejectedByName = "Bob Reviewer",
                    rejectionReason = "Insufficient justification",
                    version = 2
                )
            } returns 1

            // When: Update status
            adapter.updateStatus(update)

            // Then: Repository was called with rejector details
            coVerify(exactly = 1) {
                projectionRepository.updateStatus(
                    id = testVmRequestId.value,
                    status = "REJECTED",
                    approvedBy = null,
                    approvedByName = null,
                    rejectedBy = rejectorId.value,
                    rejectedByName = "Bob Reviewer",
                    rejectionReason = "Insufficient justification",
                    version = 2
                )
            }
        }

        @Test
        fun `returns NotFound when projection not found`() = runTest {
            // Given: Update returns 0 rows
            val update = VmRequestStatusUpdate(
                id = testVmRequestId,
                status = VmRequestStatus.CANCELLED,
                version = 2
            )
            coEvery {
                projectionRepository.updateStatus(any(), any(), any(), any(), any(), any(), any(), any())
            } returns 0

            // When: Update status
            val result = adapter.updateStatus(update)

            // Then: Returns failure with NotFound
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is ProjectionError.NotFound)
            assertEquals(testVmRequestId.value.toString(), (error as ProjectionError.NotFound).aggregateId)
            coVerify(exactly = 1) { projectionRepository.updateStatus(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `returns DatabaseError on update failure`() = runTest {
            // Given: Update will fail
            val update = VmRequestStatusUpdate(
                id = testVmRequestId,
                status = VmRequestStatus.CANCELLED,
                version = 2
            )
            coEvery {
                projectionRepository.updateStatus(any(), any(), any(), any(), any(), any(), any(), any())
            } throws RuntimeException("Database error")

            // When: Update
            val result = adapter.updateStatus(update)

            // Then: Returns failure with DatabaseError
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is ProjectionError.DatabaseError)
            assertEquals(testVmRequestId.value.toString(), (error as ProjectionError.DatabaseError).aggregateId)
            assertTrue(error.message.contains("Database error"))
            coVerify(exactly = 1) { projectionRepository.updateStatus(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `updates to all valid statuses`() = runTest {
            // Given: All possible status values
            val statuses = listOf(
                VmRequestStatus.PENDING,
                VmRequestStatus.APPROVED,
                VmRequestStatus.REJECTED,
                VmRequestStatus.CANCELLED,
                VmRequestStatus.PROVISIONING,
                VmRequestStatus.READY,
                VmRequestStatus.FAILED
            )

            statuses.forEach { status ->
                val update = VmRequestStatusUpdate(
                    id = VmRequestId.generate(),
                    status = status,
                    version = 2
                )
                coEvery {
                    projectionRepository.updateStatus(any(), eq(status.name), any(), any(), any(), any(), any(), any())
                } returns 1

                // When: Update status
                adapter.updateStatus(update)

                // Then: Correct status string passed
                coVerify {
                    projectionRepository.updateStatus(any(), eq(status.name), any(), any(), any(), any(), any(), any())
                }
            }
        }
    }
}
