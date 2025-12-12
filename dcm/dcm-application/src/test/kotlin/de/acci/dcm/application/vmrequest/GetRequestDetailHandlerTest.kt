package de.acci.dcm.application.vmrequest

import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("GetRequestDetailHandler")
class GetRequestDetailHandlerTest {

    private lateinit var requestRepository: VmRequestDetailRepository
    private lateinit var timelineRepository: TimelineEventReadRepository
    private lateinit var handler: GetRequestDetailHandler

    private val testTenantId = TenantId(UUID.randomUUID())
    private val testUserId = UserId(UUID.randomUUID())
    private val testRequestId = VmRequestId(UUID.randomUUID())
    private val testTimestamp = Instant.now()

    @BeforeEach
    fun setup() {
        requestRepository = mockk()
        timelineRepository = mockk()
        handler = GetRequestDetailHandler(requestRepository, timelineRepository)
    }

    @Nested
    @DisplayName("when request exists")
    inner class WhenRequestExists {

        @Test
        fun `returns request detail with timeline`() = runBlocking {
            // Given
            val projection = VmRequestDetailProjection(
                id = testRequestId,
                requesterId = testUserId,
                vmName = "web-server-01",
                size = "MEDIUM",
                cpuCores = 4,
                memoryGb = 16,
                diskGb = 100,
                justification = "Production deployment",
                status = "PENDING",
                projectName = "E-Commerce Platform",
                requesterName = "John Doe",
                createdAt = testTimestamp
            )
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

            // When
            val query = GetRequestDetailQuery(
                tenantId = testTenantId,
                requestId = testRequestId,
                userId = testUserId
            )
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Success)
            val detail = (result as Result.Success).value
            assertEquals("web-server-01", detail.vmName)
            assertEquals("MEDIUM", detail.size)
            assertEquals(4, detail.cpuCores)
            assertEquals(16, detail.memoryGb)
            assertEquals(100, detail.diskGb)
            assertEquals("Production deployment", detail.justification)
            assertEquals("PENDING", detail.status)
            assertEquals("E-Commerce Platform", detail.projectName)
            assertEquals("John Doe", detail.requesterName)
            assertEquals(1, detail.timeline.size)
            assertEquals(TimelineEventType.CREATED, detail.timeline[0].eventType)
        }

        @Test
        fun `returns request with multiple timeline events in chronological order`() = runBlocking {
            // Given
            val projection = VmRequestDetailProjection(
                id = testRequestId,
                requesterId = testUserId,
                vmName = "db-server-01",
                size = "LARGE",
                cpuCores = 8,
                memoryGb = 32,
                diskGb = 500,
                justification = "Database migration",
                status = "CANCELLED",
                projectName = "Data Platform",
                requesterName = "Jane Smith",
                createdAt = testTimestamp
            )
            val timelineEvents = listOf(
                TimelineEventItem(
                    eventType = TimelineEventType.CREATED,
                    actorName = "Jane Smith",
                    details = null,
                    occurredAt = testTimestamp
                ),
                TimelineEventItem(
                    eventType = TimelineEventType.CANCELLED,
                    actorName = "Jane Smith",
                    details = """{"reason":"No longer needed"}""",
                    occurredAt = testTimestamp.plusSeconds(3600)
                )
            )

            coEvery { requestRepository.findById(testRequestId) } returns projection
            coEvery { timelineRepository.findByRequestId(testRequestId) } returns timelineEvents

            // When
            val result = handler.handle(
                GetRequestDetailQuery(
                    tenantId = testTenantId,
                    requestId = testRequestId,
                    userId = testUserId
                )
            )

            // Then
            assertTrue(result is Result.Success)
            val detail = (result as Result.Success).value
            assertEquals(2, detail.timeline.size)
            assertEquals(TimelineEventType.CREATED, detail.timeline[0].eventType)
            assertEquals(TimelineEventType.CANCELLED, detail.timeline[1].eventType)
            assertNotNull(detail.timeline[1].details)
        }
    }

    @Nested
    @DisplayName("when user is not authorized")
    inner class WhenUserIsNotAuthorized {

        @Test
        fun `returns Forbidden error when accessing another user's request`() = runBlocking {
            // Given
            val otherUserId = UserId(UUID.randomUUID())
            val projection = VmRequestDetailProjection(
                id = testRequestId,
                requesterId = otherUserId, // Different from testUserId
                vmName = "web-server-01",
                size = "MEDIUM",
                cpuCores = 4,
                memoryGb = 16,
                diskGb = 100,
                justification = "Production deployment",
                status = "PENDING",
                projectName = "E-Commerce Platform",
                requesterName = "Other User",
                createdAt = testTimestamp
            )

            coEvery { requestRepository.findById(testRequestId) } returns projection

            // When
            val result = handler.handle(
                GetRequestDetailQuery(
                    tenantId = testTenantId,
                    requestId = testRequestId,
                    userId = testUserId // Different from projection.requesterId
                )
            )

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is GetRequestDetailError.Forbidden)
        }
    }

    @Nested
    @DisplayName("when request does not exist")
    inner class WhenRequestDoesNotExist {

        @Test
        fun `returns NotFound error`() = runBlocking {
            // Given
            coEvery { requestRepository.findById(testRequestId) } returns null

            // When
            val result = handler.handle(
                GetRequestDetailQuery(
                    tenantId = testTenantId,
                    requestId = testRequestId,
                    userId = testUserId
                )
            )

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is GetRequestDetailError.NotFound)
            assertEquals(testRequestId, (error as GetRequestDetailError.NotFound).requestId)
        }
    }

    @Nested
    @DisplayName("when repository throws exception")
    inner class WhenRepositoryThrowsException {

        @Test
        fun `returns QueryFailure error when request repository fails`() = runBlocking {
            // Given
            coEvery { requestRepository.findById(testRequestId) } throws RuntimeException("Database connection failed")

            // When
            val result = handler.handle(
                GetRequestDetailQuery(
                    tenantId = testTenantId,
                    requestId = testRequestId,
                    userId = testUserId
                )
            )

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is GetRequestDetailError.QueryFailure)
            assertTrue((error as GetRequestDetailError.QueryFailure).message.contains("Database connection failed"))
        }

        @Test
        fun `returns QueryFailure error when timeline repository fails after auth`() = runBlocking {
            // Given - request exists and user is authorized, but timeline query fails
            val projection = VmRequestDetailProjection(
                id = testRequestId,
                requesterId = testUserId, // Same as query userId = authorized
                vmName = "web-server-01",
                size = "MEDIUM",
                cpuCores = 4,
                memoryGb = 16,
                diskGb = 100,
                justification = "Production deployment",
                status = "PENDING",
                projectName = "E-Commerce Platform",
                requesterName = "John Doe",
                createdAt = testTimestamp
            )

            coEvery { requestRepository.findById(testRequestId) } returns projection
            coEvery { timelineRepository.findByRequestId(testRequestId) } throws RuntimeException("Timeline query failed")

            // When
            val result = handler.handle(
                GetRequestDetailQuery(
                    tenantId = testTenantId,
                    requestId = testRequestId,
                    userId = testUserId
                )
            )

            // Then
            assertTrue(result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(error is GetRequestDetailError.QueryFailure)
            assertTrue((error as GetRequestDetailError.QueryFailure).message.contains("Timeline query failed"))
        }
    }
}
