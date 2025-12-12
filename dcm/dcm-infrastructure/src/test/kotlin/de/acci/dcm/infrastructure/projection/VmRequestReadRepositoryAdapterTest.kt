package de.acci.dcm.infrastructure.projection

import de.acci.dcm.domain.vmrequest.VmRequestStatus
import de.acci.dcm.domain.vmrequest.VmSize
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.eventsourcing.projection.PagedResponse
import de.acci.dcm.infrastructure.jooq.`public`.tables.pojos.VmRequestsProjection
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

@DisplayName("VmRequestReadRepositoryAdapter")
class VmRequestReadRepositoryAdapterTest {

    private val projectionRepository = mockk<VmRequestProjectionRepository>()
    private lateinit var adapter: VmRequestReadRepositoryAdapter

    private val testTenantId = TenantId(UUID.randomUUID())
    private val testRequesterId = UserId(UUID.randomUUID())

    @BeforeEach
    fun setup() {
        adapter = VmRequestReadRepositoryAdapter(projectionRepository)
    }

    private fun createTestProjection(
        id: UUID = UUID.randomUUID(),
        tenantId: UUID = testTenantId.value,
        requesterId: UUID = testRequesterId.value,
        requesterName: String = "Test User",
        projectId: UUID = UUID.randomUUID(),
        projectName: String = "Test Project",
        vmName: String = "test-vm",
        size: String = "M",
        cpuCores: Int = 4,
        memoryGb: Int = 16,
        diskGb: Int = 100,
        justification: String = "Test justification",
        status: String = "PENDING",
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now()
    ): VmRequestsProjection {
        return VmRequestsProjection(
            id = id,
            tenantId = tenantId,
            requesterId = requesterId,
            requesterName = requesterName,
            projectId = projectId,
            projectName = projectName,
            vmName = vmName,
            size = size,
            cpuCores = cpuCores,
            memoryGb = memoryGb,
            diskGb = diskGb,
            justification = justification,
            status = status,
            approvedBy = null,
            approvedByName = null,
            rejectedBy = null,
            rejectedByName = null,
            rejectionReason = null,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = 1
        )
    }

    @Nested
    @DisplayName("findByRequesterId")
    inner class FindByRequesterId {

        @Test
        fun `maps projection to VmRequestSummary correctly`() = runBlocking {
            // Given: A projection in the repository
            val projection = createTestProjection(
                vmName = "web-server-01",
                size = "L",
                cpuCores = 8,
                memoryGb = 32,
                diskGb = 200,
                status = "APPROVED"
            )
            val pageRequest = PageRequest(page = 0, size = 10)
            coEvery {
                projectionRepository.findByRequesterId(testRequesterId.value, pageRequest)
            } returns PagedResponse(
                items = listOf(projection),
                page = 0,
                size = 10,
                totalElements = 1
            )

            // When: Find by requester ID
            val result = adapter.findByRequesterId(testRequesterId, pageRequest)

            // Then: Summary is correctly mapped
            assertEquals(1, result.items.size)
            val summary = result.items[0]
            assertEquals(projection.id, summary.id.value)
            assertEquals(projection.tenantId, summary.tenantId.value)
            assertEquals(projection.requesterId, summary.requesterId.value)
            assertEquals(projection.requesterName, summary.requesterName)
            assertEquals(projection.projectId, summary.projectId.value)
            assertEquals(projection.projectName, summary.projectName)
            assertEquals(projection.vmName, summary.vmName)
            assertEquals(VmSize.L, summary.size)
            // Resource specs derived from VmSize
            assertEquals(VmSize.L.cpuCores, summary.size.cpuCores)
            assertEquals(VmSize.L.memoryGb, summary.size.memoryGb)
            assertEquals(VmSize.L.diskGb, summary.size.diskGb)
            assertEquals(projection.justification, summary.justification)
            assertEquals(VmRequestStatus.APPROVED, summary.status)
        }

        @Test
        fun `maps all VmSize values correctly`() = runBlocking {
            // Given: Projections with all size values
            val sizes = listOf("S", "M", "L", "XL")
            val projections = sizes.mapIndexed { index, size ->
                createTestProjection(
                    id = UUID.randomUUID(),
                    size = size,
                    cpuCores = VmSize.valueOf(size).cpuCores,
                    memoryGb = VmSize.valueOf(size).memoryGb,
                    diskGb = VmSize.valueOf(size).diskGb
                )
            }
            val pageRequest = PageRequest(page = 0, size = 10)
            coEvery {
                projectionRepository.findByRequesterId(testRequesterId.value, pageRequest)
            } returns PagedResponse(
                items = projections,
                page = 0,
                size = 10,
                totalElements = 4
            )

            // When: Find by requester ID
            val result = adapter.findByRequesterId(testRequesterId, pageRequest)

            // Then: All sizes are mapped correctly
            assertEquals(4, result.items.size)
            val mappedSizes = result.items.map { it.size }
            assertEquals(listOf(VmSize.S, VmSize.M, VmSize.L, VmSize.XL), mappedSizes)
        }

        @Test
        fun `maps all VmRequestStatus values correctly`() = runBlocking {
            // Given: Projections with various statuses
            val statuses = listOf("PENDING", "APPROVED", "REJECTED", "CANCELLED", "PROVISIONING", "READY", "FAILED")
            val projections = statuses.map { status ->
                createTestProjection(id = UUID.randomUUID(), status = status)
            }
            val pageRequest = PageRequest(page = 0, size = 20)
            coEvery {
                projectionRepository.findByRequesterId(testRequesterId.value, pageRequest)
            } returns PagedResponse(
                items = projections,
                page = 0,
                size = 20,
                totalElements = statuses.size.toLong()
            )

            // When: Find by requester ID
            val result = adapter.findByRequesterId(testRequesterId, pageRequest)

            // Then: All statuses mapped correctly
            val mappedStatuses = result.items.map { it.status }
            assertEquals(
                listOf(
                    VmRequestStatus.PENDING,
                    VmRequestStatus.APPROVED,
                    VmRequestStatus.REJECTED,
                    VmRequestStatus.CANCELLED,
                    VmRequestStatus.PROVISIONING,
                    VmRequestStatus.READY,
                    VmRequestStatus.FAILED
                ),
                mappedStatuses
            )
        }

        @Test
        fun `returns empty list when no projections found`() = runBlocking {
            // Given: No projections in repository
            val pageRequest = PageRequest(page = 0, size = 10)
            coEvery {
                projectionRepository.findByRequesterId(testRequesterId.value, pageRequest)
            } returns PagedResponse(
                items = emptyList(),
                page = 0,
                size = 10,
                totalElements = 0
            )

            // When: Find by requester ID
            val result = adapter.findByRequesterId(testRequesterId, pageRequest)

            // Then: Empty result returned
            assertEquals(0, result.items.size)
            assertEquals(0, result.totalElements)
        }

        @Test
        fun `preserves pagination metadata`() = runBlocking {
            // Given: Paginated results
            val projections = listOf(createTestProjection())
            val pageRequest = PageRequest(page = 2, size = 25)
            coEvery {
                projectionRepository.findByRequesterId(testRequesterId.value, pageRequest)
            } returns PagedResponse(
                items = projections,
                page = 2,
                size = 25,
                totalElements = 75
            )

            // When: Find by requester ID
            val result = adapter.findByRequesterId(testRequesterId, pageRequest)

            // Then: Pagination metadata preserved
            assertEquals(2, result.page)
            assertEquals(25, result.size)
            assertEquals(75, result.totalElements)
        }

        @Test
        fun `converts timestamps from OffsetDateTime to Instant`() = runBlocking {
            // Given: A projection with specific timestamps
            val createdAt = OffsetDateTime.parse("2025-01-15T10:30:00Z")
            val updatedAt = OffsetDateTime.parse("2025-01-15T14:45:00Z")
            val projection = createTestProjection(createdAt = createdAt, updatedAt = updatedAt)
            val pageRequest = PageRequest(page = 0, size = 10)
            coEvery {
                projectionRepository.findByRequesterId(testRequesterId.value, pageRequest)
            } returns PagedResponse(
                items = listOf(projection),
                page = 0,
                size = 10,
                totalElements = 1
            )

            // When: Find by requester ID
            val result = adapter.findByRequesterId(testRequesterId, pageRequest)

            // Then: Timestamps converted to Instant
            val summary = result.items[0]
            assertEquals(createdAt.toInstant(), summary.createdAt)
            assertEquals(updatedAt.toInstant(), summary.updatedAt)
        }
    }
}
