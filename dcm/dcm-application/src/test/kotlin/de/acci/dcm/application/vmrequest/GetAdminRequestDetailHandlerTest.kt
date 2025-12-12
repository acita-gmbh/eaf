package de.acci.dcm.application.vmrequest

import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for GetAdminRequestDetailHandler.
 *
 * Story 2.10: Request Detail View (Admin)
 *
 * Key differences from GetRequestDetailHandler:
 * - No requester authorization check (admin can view all requests in tenant)
 * - Includes requester info (name, email, role)
 * - Includes requester history (up to 5 recent requests)
 */
@DisplayName("GetAdminRequestDetailHandler")
class GetAdminRequestDetailHandlerTest {

    private lateinit var requestRepository: AdminRequestDetailRepository
    private lateinit var timelineRepository: TimelineEventReadRepository
    private lateinit var handler: GetAdminRequestDetailHandler

    private val testTenantId = TenantId(UUID.randomUUID())
    private val testRequestId = VmRequestId(UUID.randomUUID())
    private val testRequesterId = UserId(UUID.randomUUID())
    private val testTimestamp = Instant.now()

    @BeforeEach
    fun setup() {
        requestRepository = mockk()
        timelineRepository = mockk()
        handler = GetAdminRequestDetailHandler(requestRepository, timelineRepository)
    }

    @Nested
    @DisplayName("when request exists and admin has access")
    inner class WhenRequestExistsAndAdminHasAccess {

        @Test
        fun `returns full request details with requester info`() = runBlocking {
            // Given - AC 1, 2, 3: Admin sees complete request details
            val projection = AdminRequestDetailProjection(
                id = testRequestId,
                vmName = "web-server-01",
                size = "MEDIUM",
                cpuCores = 4,
                memoryGb = 16,
                diskGb = 100,
                justification = "Production deployment for critical service",
                status = "PENDING",
                projectName = "E-Commerce Platform",
                requesterId = testRequesterId,
                requesterName = "John Doe",
                requesterEmail = "john.doe@example.com",
                requesterRole = "developer",
                createdAt = testTimestamp,
                version = 1L
            )
            val timelineEvents = listOf(
                TimelineEventItem(
                    eventType = TimelineEventType.CREATED,
                    actorName = "John Doe",
                    details = null,
                    occurredAt = testTimestamp
                )
            )
            val recentRequests = listOf(
                VmRequestHistorySummary(
                    id = VmRequestId(UUID.randomUUID()),
                    vmName = "db-server-01",
                    status = "APPROVED",
                    createdAt = testTimestamp.minusSeconds(86400)
                )
            )

            coEvery { requestRepository.findById(testRequestId) } returns projection
            coEvery { timelineRepository.findByRequestId(testRequestId) } returns timelineEvents
            coEvery {
                requestRepository.findRecentByRequesterId(
                    requesterId = testRequesterId,
                    excludeRequestId = testRequestId,
                    limit = 5
                )
            } returns recentRequests

            // When
            val query = GetAdminRequestDetailQuery(
                tenantId = testTenantId,
                requestId = testRequestId
            )
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Success)
            val detail = (result as Result.Success).value

            // AC 2: Requester info displayed
            assertEquals("John Doe", detail.requester.name)
            assertEquals("john.doe@example.com", detail.requester.email)
            assertEquals("developer", detail.requester.role)

            // AC 3: Request details displayed
            assertEquals("web-server-01", detail.vmName)
            assertEquals("MEDIUM", detail.size.code)
            assertEquals(4, detail.size.cpuCores)
            assertEquals(16, detail.size.memoryGb)
            assertEquals("Production deployment for critical service", detail.justification)
            assertEquals("E-Commerce Platform", detail.projectName)

            // AC 5: Timeline included
            assertEquals(1, detail.timeline.size)
            assertEquals(TimelineEventType.CREATED, detail.timeline[0].eventType)

            // AC 6: Requester history included
            assertEquals(1, detail.requesterHistory.size)
            assertEquals("db-server-01", detail.requesterHistory[0].vmName)
        }

        @Test
        fun `returns request with empty requester history when no other requests`() = runBlocking {
            // Given - AC 6: Edge case - first request from this user
            val projection = createTestProjection()
            val timelineEvents = listOf(
                TimelineEventItem(
                    eventType = TimelineEventType.CREATED,
                    actorName = "John Doe",
                    details = null,
                    occurredAt = testTimestamp
                )
            )

            coEvery { requestRepository.findById(testRequestId) } returns projection
            coEvery { timelineRepository.findByRequestId(testRequestId) } returns timelineEvents
            coEvery {
                requestRepository.findRecentByRequesterId(
                    requesterId = testRequesterId,
                    excludeRequestId = testRequestId,
                    limit = 5
                )
            } returns emptyList()

            // When
            val result = handler.handle(
                GetAdminRequestDetailQuery(
                    tenantId = testTenantId,
                    requestId = testRequestId
                )
            )

            // Then
            assertTrue(result is Result.Success)
            val detail = (result as Result.Success).value
            assertTrue(detail.requesterHistory.isEmpty())
        }

        @Test
        fun `limits requester history to 5 items and excludes current request`() = runBlocking {
            // Given - AC 6: Max 5 recent requests excluding current
            val projection = createTestProjection()
            val recentRequests = (1..5).map { i ->
                VmRequestHistorySummary(
                    id = VmRequestId(UUID.randomUUID()),
                    vmName = "server-$i",
                    status = "APPROVED",
                    createdAt = testTimestamp.minusSeconds(i * 86400L)
                )
            }

            coEvery { requestRepository.findById(testRequestId) } returns projection
            coEvery { timelineRepository.findByRequestId(testRequestId) } returns emptyList()
            coEvery {
                requestRepository.findRecentByRequesterId(
                    requesterId = testRequesterId,
                    excludeRequestId = testRequestId,
                    limit = 5
                )
            } returns recentRequests

            // When
            val result = handler.handle(
                GetAdminRequestDetailQuery(
                    tenantId = testTenantId,
                    requestId = testRequestId
                )
            )

            // Then
            assertTrue(result is Result.Success)
            val detail = (result as Result.Success).value
            assertEquals(5, detail.requesterHistory.size)
            // Verify current request is not in history
            assertTrue(detail.requesterHistory.none { it.id == testRequestId })
        }

        @Test
        fun `returns request with multiple timeline events in chronological order`() = runBlocking {
            // Given - AC 5: Timeline with multiple events
            val projection = createTestProjection().copy(status = "APPROVED")
            val timelineEvents = listOf(
                TimelineEventItem(
                    eventType = TimelineEventType.CREATED,
                    actorName = "John Doe",
                    details = null,
                    occurredAt = testTimestamp
                ),
                TimelineEventItem(
                    eventType = TimelineEventType.APPROVED,
                    actorName = "Admin User",
                    details = null,
                    occurredAt = testTimestamp.plusSeconds(3600)
                )
            )

            coEvery { requestRepository.findById(testRequestId) } returns projection
            coEvery { timelineRepository.findByRequestId(testRequestId) } returns timelineEvents
            coEvery {
                requestRepository.findRecentByRequesterId(any(), any(), any())
            } returns emptyList()

            // When
            val result = handler.handle(
                GetAdminRequestDetailQuery(
                    tenantId = testTenantId,
                    requestId = testRequestId
                )
            )

            // Then
            assertTrue(result is Result.Success)
            val detail = (result as Result.Success).value
            assertEquals(2, detail.timeline.size)
            assertEquals(TimelineEventType.CREATED, detail.timeline[0].eventType)
            assertEquals(TimelineEventType.APPROVED, detail.timeline[1].eventType)
        }
    }

    @Nested
    @DisplayName("when request does not exist")
    inner class WhenRequestDoesNotExist {

        @Test
        fun `returns NotFound error for non-existent request ID`() = runBlocking {
            // Given - AC 10: Error handling for invalid ID
            coEvery { requestRepository.findById(testRequestId) } returns null

            // When
            val result = handler.handle(
                GetAdminRequestDetailQuery(
                    tenantId = testTenantId,
                    requestId = testRequestId
                )
            )

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is GetAdminRequestDetailError.NotFound)
        }
    }

    @Nested
    @DisplayName("when request is from different tenant")
    inner class WhenRequestIsFromDifferentTenant {

        @Test
        fun `returns NotFound to prevent tenant enumeration`() = runBlocking {
            // Given - AC 10: RLS will typically prevent this, but handler has defense-in-depth
            // RLS filter means findById returns null for other tenant's requests
            coEvery { requestRepository.findById(testRequestId) } returns null

            // When
            val result = handler.handle(
                GetAdminRequestDetailQuery(
                    tenantId = testTenantId,
                    requestId = testRequestId
                )
            )

            // Then - Returns NotFound (not Forbidden) to prevent tenant enumeration
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is GetAdminRequestDetailError.NotFound)
        }
    }

    @Nested
    @DisplayName("when repository throws exception")
    inner class WhenRepositoryThrowsException {

        @Test
        fun `returns QueryFailure error when request repository fails`() = runBlocking {
            // Given
            coEvery { requestRepository.findById(testRequestId) } throws RuntimeException("Database error")

            // When
            val result = handler.handle(
                GetAdminRequestDetailQuery(
                    tenantId = testTenantId,
                    requestId = testRequestId
                )
            )

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is GetAdminRequestDetailError.QueryFailure)
            assertTrue((error as GetAdminRequestDetailError.QueryFailure).message.contains("Database error"))
        }

        @Test
        fun `returns QueryFailure error when timeline repository fails`() = runBlocking {
            // Given
            val projection = createTestProjection()
            coEvery { requestRepository.findById(testRequestId) } returns projection
            coEvery { timelineRepository.findByRequestId(testRequestId) } throws RuntimeException("Timeline query failed")

            // When
            val result = handler.handle(
                GetAdminRequestDetailQuery(
                    tenantId = testTenantId,
                    requestId = testRequestId
                )
            )

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is GetAdminRequestDetailError.QueryFailure)
        }

        @Test
        fun `returns QueryFailure error when history repository fails`() = runBlocking {
            // Given
            val projection = createTestProjection()
            coEvery { requestRepository.findById(testRequestId) } returns projection
            coEvery { timelineRepository.findByRequestId(testRequestId) } returns emptyList()
            coEvery {
                requestRepository.findRecentByRequesterId(any(), any(), any())
            } throws RuntimeException("History query failed")

            // When
            val result = handler.handle(
                GetAdminRequestDetailQuery(
                    tenantId = testTenantId,
                    requestId = testRequestId
                )
            )

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is GetAdminRequestDetailError.QueryFailure)
        }
    }

    private fun createTestProjection() = AdminRequestDetailProjection(
        id = testRequestId,
        vmName = "web-server-01",
        size = "MEDIUM",
        cpuCores = 4,
        memoryGb = 16,
        diskGb = 100,
        justification = "Production deployment",
        status = "PENDING",
        projectName = "E-Commerce Platform",
        requesterId = testRequesterId,
        requesterName = "John Doe",
        requesterEmail = "john.doe@example.com",
        requesterRole = "developer",
        createdAt = testTimestamp,
        version = 1L
    )
}
