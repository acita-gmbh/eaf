package de.acci.dvmm.application.vmrequest

import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmRequestStatus
import de.acci.dvmm.domain.vmrequest.VmSize
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

@DisplayName("GetMyRequestsHandler")
class GetMyRequestsHandlerTest {

    private val readRepository = mockk<VmRequestReadRepository>()

    private fun createQuery(
        tenantId: TenantId = TenantId.generate(),
        userId: UserId = UserId.generate(),
        page: Int = 0,
        size: Int = 20
    ) = GetMyRequestsQuery(
        tenantId = tenantId,
        userId = userId,
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
        @DisplayName("should return paginated response from repository")
        fun `should return paginated response from repository`() = runTest {
            // Given
            val userId = UserId.generate()
            val tenantId = TenantId.generate()
            val query = createQuery(tenantId = tenantId, userId = userId)
            val summaries = listOf(
                createSummary(requesterId = userId),
                createSummary(requesterId = userId)
            )
            val expectedResponse = PagedResponse(
                items = summaries,
                page = 0,
                size = 20,
                totalElements = 2L
            )

            coEvery {
                readRepository.findByRequesterId(userId, query.pageRequest)
            } returns expectedResponse

            val handler = GetMyRequestsHandler(readRepository)

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertEquals(2, success.value.items.size)
            assertEquals(2L, success.value.totalElements)
        }

        @Test
        @DisplayName("should pass correct userId to repository")
        fun `should pass correct userId to repository`() = runTest {
            // Given
            val userId = UserId.generate()
            val query = createQuery(userId = userId)

            coEvery {
                readRepository.findByRequesterId(any(), any())
            } returns PagedResponse.empty()

            val handler = GetMyRequestsHandler(readRepository)

            // When
            handler.handle(query)

            // Then
            coVerify(exactly = 1) {
                readRepository.findByRequesterId(userId, query.pageRequest)
            }
        }

        @Test
        @DisplayName("should pass pagination parameters to repository")
        fun `should pass pagination parameters to repository`() = runTest {
            // Given
            val query = createQuery(page = 2, size = 10)

            coEvery {
                readRepository.findByRequesterId(any(), any())
            } returns PagedResponse.empty()

            val handler = GetMyRequestsHandler(readRepository)

            // When
            handler.handle(query)

            // Then
            coVerify(exactly = 1) {
                readRepository.findByRequesterId(
                    any(),
                    match { it.page == 2 && it.size == 10 }
                )
            }
        }

        @Test
        @DisplayName("should return empty response when user has no requests")
        fun `should return empty response when user has no requests`() = runTest {
            // Given
            val query = createQuery()

            coEvery {
                readRepository.findByRequesterId(any(), any())
            } returns PagedResponse.empty()

            val handler = GetMyRequestsHandler(readRepository)

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertTrue(success.value.items.isEmpty())
            assertEquals(0L, success.value.totalElements)
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
                readRepository.findByRequesterId(any(), any())
            } throws RuntimeException("Database connection failed")

            val handler = GetMyRequestsHandler(readRepository)

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Failure)
            val failure = result as Result.Failure
            assertTrue(failure.error is GetMyRequestsError.QueryFailure)
            assertTrue((failure.error as GetMyRequestsError.QueryFailure).message.contains("Database connection failed"))
        }
    }

    @Nested
    @DisplayName("response content")
    inner class ResponseContentTests {

        @Test
        @DisplayName("should preserve request status in response")
        fun `should preserve request status in response`() = runTest {
            // Given
            val userId = UserId.generate()
            val query = createQuery(userId = userId)
            val summaries = listOf(
                createSummary(requesterId = userId, status = VmRequestStatus.PENDING),
                createSummary(requesterId = userId, status = VmRequestStatus.APPROVED),
                createSummary(requesterId = userId, status = VmRequestStatus.CANCELLED)
            )
            val expectedResponse = PagedResponse(
                items = summaries,
                page = 0,
                size = 20,
                totalElements = 3L
            )

            coEvery {
                readRepository.findByRequesterId(userId, any())
            } returns expectedResponse

            val handler = GetMyRequestsHandler(readRepository)

            // When
            val result = handler.handle(query)

            // Then
            assertTrue(result is Result.Success)
            val success = result as Result.Success
            assertEquals(VmRequestStatus.PENDING, success.value.items[0].status)
            assertEquals(VmRequestStatus.APPROVED, success.value.items[1].status)
            assertEquals(VmRequestStatus.CANCELLED, success.value.items[2].status)
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
                readRepository.findByRequesterId(any(), any())
            } returns expectedResponse

            val handler = GetMyRequestsHandler(readRepository)

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
