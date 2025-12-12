package de.acci.dcm.application.vmrequest

import de.acci.dcm.domain.vmrequest.ProjectId
import de.acci.dcm.domain.vmrequest.VmRequestId
import de.acci.dcm.domain.vmrequest.VmRequestStatus
import de.acci.dcm.domain.vmrequest.VmSize
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.eventsourcing.projection.PagedResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests for GetPendingRequestsHandler.
 *
 * Story 2.9: Admin Approval Queue
 * Tests verify admin can retrieve pending requests for their tenant.
 */
@DisplayName("GetPendingRequestsHandler")
class GetPendingRequestsHandlerTest {

    private val readRepository = mockk<VmRequestReadRepository>()

    private fun createQuery(
        tenantId: TenantId = TenantId.generate(),
        projectId: ProjectId? = null,
        page: Int = 0,
        size: Int = 25
    ) = GetPendingRequestsQuery(
        tenantId = tenantId,
        projectId = projectId,
        pageRequest = PageRequest(page = page, size = size)
    )

    private fun createSummary(
        id: VmRequestId = VmRequestId.generate(),
        tenantId: TenantId = TenantId.generate(),
        requesterId: UserId = UserId.generate(),
        status: VmRequestStatus = VmRequestStatus.PENDING
    ) = VmRequestSummary(
        id = id,
        tenantId = tenantId,
        requesterId = requesterId,
        requesterName = "Test User",
        requesterEmail = "test@example.com",
        projectId = ProjectId.generate(),
        projectName = "Test Project",
        vmName = "test-vm-01",
        size = VmSize.M,
        justification = "Test justification for testing",
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Nested
    @DisplayName("handle()")
    inner class HandleTests {

        @Test
        @DisplayName("should return pending requests for tenant")
        fun `should return pending requests for tenant`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val query = createQuery(tenantId = tenantId)
            val summaries = listOf(
                createSummary(tenantId = tenantId, status = VmRequestStatus.PENDING),
                createSummary(tenantId = tenantId, status = VmRequestStatus.PENDING)
            )
            val expectedResponse = PagedResponse(
                items = summaries,
                page = 0,
                size = 25,
                totalElements = 2L
            )

            coEvery {
                readRepository.findPendingByTenantId(
                    tenantId = tenantId,
                    projectId = null,
                    pageRequest = query.pageRequest
                )
            } returns expectedResponse

            val handler = GetPendingRequestsHandler(readRepository)

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertEquals(2, success.value.items.size)
            assertEquals(2L, success.value.totalElements)
        }

        @Test
        @DisplayName("should return empty response when no pending requests")
        fun `should return empty response when no pending requests`() = runTest {
            // Given
            val query = createQuery()

            coEvery {
                readRepository.findPendingByTenantId(any(), any(), any())
            } returns PagedResponse.empty()

            val handler = GetPendingRequestsHandler(readRepository)

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertTrue(success.value.items.isEmpty())
            assertEquals(0L, success.value.totalElements)
        }

        @Test
        @DisplayName("should filter by project when projectId provided")
        fun `should filter by project when projectId provided`() = runTest {
            // Given
            val tenantId = TenantId.generate()
            val projectId = ProjectId.generate()
            val query = createQuery(tenantId = tenantId, projectId = projectId)

            coEvery {
                readRepository.findPendingByTenantId(any(), any(), any())
            } returns PagedResponse.empty()

            val handler = GetPendingRequestsHandler(readRepository)

            // When
            handler.handle(query)

            // Then
            coVerify(exactly = 1) {
                readRepository.findPendingByTenantId(
                    tenantId = tenantId,
                    projectId = projectId,
                    pageRequest = any()
                )
            }
        }

        @Test
        @DisplayName("should pass pagination parameters to repository")
        fun `should pass pagination parameters to repository`() = runTest {
            // Given
            val query = createQuery(page = 2, size = 10)

            coEvery {
                readRepository.findPendingByTenantId(any(), any(), any())
            } returns PagedResponse.empty()

            val handler = GetPendingRequestsHandler(readRepository)

            // When
            handler.handle(query)

            // Then
            coVerify(exactly = 1) {
                readRepository.findPendingByTenantId(
                    tenantId = any(),
                    projectId = any(),
                    pageRequest = match { it.page == 2 && it.size == 10 }
                )
            }
        }

        @Test
        @DisplayName("should cap page size at 100")
        fun `should cap page size at 100`() = runTest {
            // Given: Query with exactly MAX_PAGE_SIZE (100) should work
            val query = createQuery(size = GetPendingRequestsQuery.MAX_PAGE_SIZE)

            coEvery {
                readRepository.findPendingByTenantId(any(), any(), any())
            } returns PagedResponse.empty()

            val handler = GetPendingRequestsHandler(readRepository)

            // When
            handler.handle(query)

            // Then: Verify the max size is passed correctly
            coVerify(exactly = 1) {
                readRepository.findPendingByTenantId(
                    tenantId = any(),
                    projectId = any(),
                    pageRequest = match { it.size == GetPendingRequestsQuery.MAX_PAGE_SIZE }
                )
            }

            // Verify that requesting more than max throws (handled by query validation)
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                createQuery(size = GetPendingRequestsQuery.MAX_PAGE_SIZE + 1)
            }
        }
    }

    @Nested
    @DisplayName("error handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("should return QueryFailure when repository throws exception")
        fun `should return QueryFailure when repository throws exception`() = runTest {
            // Given
            val query = createQuery()

            coEvery {
                readRepository.findPendingByTenantId(any(), any(), any())
            } throws RuntimeException("Database connection failed")

            val handler = GetPendingRequestsHandler(readRepository)

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is GetPendingRequestsError.QueryFailure)
            assertTrue((failure.error as GetPendingRequestsError.QueryFailure).message.contains("Database connection failed"))
        }
    }

    @Nested
    @DisplayName("response content")
    inner class ResponseContentTests {

        @Test
        @DisplayName("should only return PENDING status requests")
        fun `should only return PENDING status requests`() = runTest {
            // Given
            val query = createQuery()
            val summaries = listOf(
                createSummary(status = VmRequestStatus.PENDING),
                createSummary(status = VmRequestStatus.PENDING)
            )
            val expectedResponse = PagedResponse(
                items = summaries,
                page = 0,
                size = 25,
                totalElements = 2L
            )

            coEvery {
                readRepository.findPendingByTenantId(any(), any(), any())
            } returns expectedResponse

            val handler = GetPendingRequestsHandler(readRepository)

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            success.value.items.forEach { summary ->
                assertEquals(VmRequestStatus.PENDING, summary.status)
            }
        }

        @Test
        @DisplayName("should return correct pagination metadata")
        fun `should return correct pagination metadata`() = runTest {
            // Given
            val query = createQuery(page = 1, size = 10)
            val summaries = (1..10).map { createSummary() }
            val expectedResponse = PagedResponse(
                items = summaries,
                page = 1,
                size = 10,
                totalElements = 25L
            )

            coEvery {
                readRepository.findPendingByTenantId(any(), any(), any())
            } returns expectedResponse

            val handler = GetPendingRequestsHandler(readRepository)

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertEquals(1, success.value.page)
            assertEquals(10, success.value.size)
            assertEquals(25L, success.value.totalElements)
            assertEquals(3, success.value.totalPages)
            assertTrue(success.value.hasNext)
            assertTrue(success.value.hasPrevious)
        }
    }
}
