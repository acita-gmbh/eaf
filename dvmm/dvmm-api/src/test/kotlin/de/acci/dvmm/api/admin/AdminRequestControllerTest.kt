package de.acci.dvmm.api.admin

import de.acci.dvmm.application.vmrequest.GetPendingRequestsError
import de.acci.dvmm.application.vmrequest.GetPendingRequestsHandler
import de.acci.dvmm.application.vmrequest.GetPendingRequestsQuery
import de.acci.dvmm.application.vmrequest.ProjectSummary
import de.acci.dvmm.application.vmrequest.VmRequestReadRepository
import de.acci.dvmm.application.vmrequest.VmRequestSummary
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmRequestStatus
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.PagedResponse
import de.acci.eaf.tenant.TenantContextElement
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for AdminRequestController.
 *
 * Story 2.9: Admin Approval Queue
 *
 * Tests controller behavior in isolation using MockK.
 * Integration tests with Spring Security are in AdminRequestControllerIntegrationTest.
 */
@DisplayName("AdminRequestController")
class AdminRequestControllerTest {

    private val getPendingRequestsHandler = mockk<GetPendingRequestsHandler>()
    private val readRepository = mockk<VmRequestReadRepository>()
    private lateinit var controller: AdminRequestController
    private val testTenantId = TenantId.generate()

    @BeforeEach
    fun setup() {
        controller = AdminRequestController(
            getPendingRequestsHandler = getPendingRequestsHandler,
            readRepository = readRepository
        )
    }

    private fun createJwt(subject: String = UUID.randomUUID().toString()): Jwt {
        return Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject(subject)
            .claim("tenant_id", testTenantId.value.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }

    private suspend fun <T> withTenant(block: suspend () -> T): T {
        return withContext(TenantContextElement(testTenantId)) {
            block()
        }
    }

    private fun createTestSummary(
        id: VmRequestId = VmRequestId.generate(),
        projectId: ProjectId = ProjectId.generate(),
        projectName: String = "Test Project"
    ): VmRequestSummary {
        val now = Instant.now()
        return VmRequestSummary(
            id = id,
            tenantId = testTenantId,
            requesterId = UserId.generate(),
            requesterName = "Test User",
            projectId = projectId,
            projectName = projectName,
            vmName = "test-vm",
            size = VmSize.M,
            justification = "Test justification",
            status = VmRequestStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
    }

    @Nested
    @DisplayName("GET /api/admin/requests/pending")
    inner class GetPendingRequestsTests {

        @Test
        @DisplayName("should return 200 OK with paginated pending requests")
        fun `should return 200 OK with paginated pending requests`() = runTest {
            // Given
            val jwt = createJwt()
            val summary = createTestSummary()

            coEvery {
                getPendingRequestsHandler.handle(any())
            } returns PagedResponse(
                items = listOf(summary),
                page = 0,
                size = 25,
                totalElements = 1L
            ).success()

            // When
            val response = withTenant {
                controller.getPendingRequests(
                    projectId = null,
                    page = 0,
                    size = 25,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as PendingRequestsPageResponse
            assertEquals(1, body.items.size)
            assertEquals(summary.id.value.toString(), body.items[0].id)
            assertEquals(summary.requesterName, body.items[0].requesterName)
            assertEquals(summary.vmName, body.items[0].vmName)
            assertEquals(summary.projectName, body.items[0].projectName)
        }

        @Test
        @DisplayName("should return empty page when no pending requests")
        fun `should return empty page when no pending requests`() = runTest {
            // Given
            val jwt = createJwt()

            coEvery {
                getPendingRequestsHandler.handle(any())
            } returns PagedResponse<VmRequestSummary>(
                items = emptyList(),
                page = 0,
                size = 25,
                totalElements = 0L
            ).success()

            // When
            val response = withTenant {
                controller.getPendingRequests(
                    projectId = null,
                    page = 0,
                    size = 25,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as PendingRequestsPageResponse
            assertEquals(0, body.items.size)
            assertEquals(0L, body.totalElements)
        }

        @Test
        @DisplayName("should pass project filter to handler")
        fun `should pass project filter to handler`() = runTest {
            // Given
            val jwt = createJwt()
            val projectId = UUID.randomUUID()
            val querySlot = slot<GetPendingRequestsQuery>()

            coEvery {
                getPendingRequestsHandler.handle(capture(querySlot))
            } returns PagedResponse<VmRequestSummary>(
                items = emptyList(),
                page = 0,
                size = 25,
                totalElements = 0L
            ).success()

            // When
            withTenant {
                controller.getPendingRequests(
                    projectId = projectId.toString(),
                    page = 0,
                    size = 25,
                    jwt = jwt
                )
            }

            // Then
            val query = querySlot.captured
            assertNotNull(query.projectId)
            assertEquals(projectId, query.projectId?.value)
        }

        @Test
        @DisplayName("should return 400 for invalid project ID format")
        fun `should return 400 for invalid project ID format`() = runTest {
            // Given
            val jwt = createJwt()

            // When
            val response = withTenant {
                controller.getPendingRequests(
                    projectId = "not-a-valid-uuid",
                    page = 0,
                    size = 25,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, String>
            assertEquals("INVALID_PROJECT_ID", body["error"])
        }

        @Test
        @DisplayName("should coerce negative page to 0")
        fun `should coerce negative page to 0`() = runTest {
            // Given
            val jwt = createJwt()
            val querySlot = slot<GetPendingRequestsQuery>()

            coEvery {
                getPendingRequestsHandler.handle(capture(querySlot))
            } returns PagedResponse<VmRequestSummary>(
                items = emptyList(),
                page = 0,
                size = 25,
                totalElements = 0L
            ).success()

            // When
            withTenant {
                controller.getPendingRequests(
                    projectId = null,
                    page = -5,
                    size = 25,
                    jwt = jwt
                )
            }

            // Then
            val query = querySlot.captured
            assertEquals(0, query.pageRequest.page)
        }

        @Test
        @DisplayName("should coerce size greater than max to max (100)")
        fun `should coerce size greater than max to max`() = runTest {
            // Given
            val jwt = createJwt()
            val querySlot = slot<GetPendingRequestsQuery>()

            coEvery {
                getPendingRequestsHandler.handle(capture(querySlot))
            } returns PagedResponse<VmRequestSummary>(
                items = emptyList(),
                page = 0,
                size = 100,
                totalElements = 0L
            ).success()

            // When
            withTenant {
                controller.getPendingRequests(
                    projectId = null,
                    page = 0,
                    size = 200,
                    jwt = jwt
                )
            }

            // Then
            val query = querySlot.captured
            assertEquals(100, query.pageRequest.size)
        }

        @Test
        @DisplayName("should coerce size less than 1 to 1")
        fun `should coerce size less than 1 to 1`() = runTest {
            // Given
            val jwt = createJwt()
            val querySlot = slot<GetPendingRequestsQuery>()

            coEvery {
                getPendingRequestsHandler.handle(capture(querySlot))
            } returns PagedResponse<VmRequestSummary>(
                items = emptyList(),
                page = 0,
                size = 1,
                totalElements = 0L
            ).success()

            // When
            withTenant {
                controller.getPendingRequests(
                    projectId = null,
                    page = 0,
                    size = 0,
                    jwt = jwt
                )
            }

            // Then
            val query = querySlot.captured
            assertEquals(1, query.pageRequest.size)
        }

        @Test
        @DisplayName("should return 403 for forbidden error")
        fun `should return 403 for forbidden error`() = runTest {
            // Given
            val jwt = createJwt()

            coEvery {
                getPendingRequestsHandler.handle(any())
            } returns GetPendingRequestsError.Forbidden.failure()

            // When
            val response = withTenant {
                controller.getPendingRequests(
                    projectId = null,
                    page = 0,
                    size = 25,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, String>
            assertEquals("FORBIDDEN", body["error"])
        }

        @Test
        @DisplayName("should return 500 for query failure")
        fun `should return 500 for query failure`() = runTest {
            // Given
            val jwt = createJwt()

            coEvery {
                getPendingRequestsHandler.handle(any())
            } returns GetPendingRequestsError.QueryFailure(
                message = "Database error"
            ).failure()

            // When
            val response = withTenant {
                controller.getPendingRequests(
                    projectId = null,
                    page = 0,
                    size = 25,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, String>
            assertEquals("QUERY_FAILURE", body["error"])
        }

        @Test
        @DisplayName("should include VmSize details in response")
        fun `should include VmSize details in response`() = runTest {
            // Given
            val jwt = createJwt()
            val summary = createTestSummary()

            coEvery {
                getPendingRequestsHandler.handle(any())
            } returns PagedResponse(
                items = listOf(summary),
                page = 0,
                size = 25,
                totalElements = 1L
            ).success()

            // When
            val response = withTenant {
                controller.getPendingRequests(
                    projectId = null,
                    page = 0,
                    size = 25,
                    jwt = jwt
                )
            }

            // Then
            val body = response.body as PendingRequestsPageResponse
            val item = body.items[0]
            assertEquals("M", item.size.code)
            assertEquals(4, item.size.cpuCores)
            assertEquals(8, item.size.memoryGb)  // VmSize.M = 4 CPU, 8 GB RAM, 100 GB disk
            assertEquals(100, item.size.diskGb)
        }

        @Test
        @DisplayName("should include pagination metadata in response")
        fun `should include pagination metadata in response`() = runTest {
            // Given
            val jwt = createJwt()

            coEvery {
                getPendingRequestsHandler.handle(any())
            } returns PagedResponse<VmRequestSummary>(
                items = emptyList(),
                page = 2,
                size = 25,
                totalElements = 75L
            ).success()

            // When
            val response = withTenant {
                controller.getPendingRequests(
                    projectId = null,
                    page = 2,
                    size = 25,
                    jwt = jwt
                )
            }

            // Then
            val body = response.body as PendingRequestsPageResponse
            assertEquals(2, body.page)
            assertEquals(25, body.size)
            assertEquals(75L, body.totalElements)
            assertEquals(3, body.totalPages)
        }
    }

    @Nested
    @DisplayName("GET /api/admin/projects")
    inner class GetProjectsTests {

        @Test
        @DisplayName("should return 200 OK with list of projects")
        fun `should return 200 OK with list of projects`() = runTest {
            // Given
            val jwt = createJwt()
            val projectId = ProjectId.generate()
            val projects = listOf(
                ProjectSummary(id = projectId, name = "Alpha Project"),
                ProjectSummary(id = ProjectId.generate(), name = "Beta Project")
            )

            coEvery {
                readRepository.findDistinctProjects(testTenantId)
            } returns projects

            // When
            val response = withTenant {
                controller.getProjects(jwt = jwt)
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as List<*>
            assertEquals(2, body.size)
            val first = body[0] as ProjectResponse
            assertEquals(projectId.value.toString(), first.id)
            assertEquals("Alpha Project", first.name)
        }

        @Test
        @DisplayName("should return empty list when no projects")
        fun `should return empty list when no projects`() = runTest {
            // Given
            val jwt = createJwt()

            coEvery {
                readRepository.findDistinctProjects(testTenantId)
            } returns emptyList()

            // When
            val response = withTenant {
                controller.getProjects(jwt = jwt)
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as List<*>
            assertTrue(body.isEmpty())
        }

        @Test
        @DisplayName("should return 500 with error details on failure")
        fun `should return 500 with error details on failure`() = runTest {
            // Given
            val jwt = createJwt()

            coEvery {
                readRepository.findDistinctProjects(testTenantId)
            } throws RuntimeException("Database error")

            // When
            val response = withTenant {
                controller.getProjects(jwt = jwt)
            }

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, String>
            assertEquals("QUERY_FAILURE", body["error"])
            assertEquals("Failed to retrieve projects", body["message"])
        }

        @Test
        @DisplayName("should call repository with correct tenant ID")
        fun `should call repository with correct tenant ID`() = runTest {
            // Given
            val jwt = createJwt()

            coEvery {
                readRepository.findDistinctProjects(testTenantId)
            } returns emptyList()

            // When
            withTenant {
                controller.getProjects(jwt = jwt)
            }

            // Then
            coVerify(exactly = 1) {
                readRepository.findDistinctProjects(testTenantId)
            }
        }
    }
}
