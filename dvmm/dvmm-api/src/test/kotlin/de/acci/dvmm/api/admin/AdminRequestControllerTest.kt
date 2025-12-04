package de.acci.dvmm.api.admin

import de.acci.dvmm.application.vmrequest.AdminRequestDetail
import de.acci.dvmm.application.vmrequest.GetAdminRequestDetailError
import de.acci.dvmm.application.vmrequest.GetAdminRequestDetailHandler
import de.acci.dvmm.application.vmrequest.GetAdminRequestDetailQuery
import de.acci.dvmm.application.vmrequest.GetPendingRequestsError
import de.acci.dvmm.application.vmrequest.GetPendingRequestsHandler
import de.acci.dvmm.application.vmrequest.GetPendingRequestsQuery
import de.acci.dvmm.application.vmrequest.ProjectSummary
import de.acci.dvmm.application.vmrequest.RequesterInfo
import de.acci.dvmm.application.vmrequest.TimelineEventItem
import de.acci.dvmm.application.vmrequest.VmRequestReadRepository
import de.acci.dvmm.application.vmrequest.VmRequestSummary
import de.acci.dvmm.application.vmrequest.TimelineEventType
import de.acci.dvmm.application.vmrequest.VmSizeInfo
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
 * Story 2.10: Request Detail View (Admin)
 *
 * Tests controller behavior in isolation using MockK.
 * Integration tests with Spring Security are in AdminRequestControllerIntegrationTest.
 */
@DisplayName("AdminRequestController")
class AdminRequestControllerTest {

    private val getPendingRequestsHandler = mockk<GetPendingRequestsHandler>()
    private val getAdminRequestDetailHandler = mockk<GetAdminRequestDetailHandler>()
    private val approveVmRequestHandler = mockk<de.acci.dvmm.application.vmrequest.ApproveVmRequestHandler>()
    private val rejectVmRequestHandler = mockk<de.acci.dvmm.application.vmrequest.RejectVmRequestHandler>()
    private val readRepository = mockk<VmRequestReadRepository>()
    private lateinit var controller: AdminRequestController
    private val testTenantId = TenantId.generate()

    @BeforeEach
    fun setup() {
        controller = AdminRequestController(
            getPendingRequestsHandler = getPendingRequestsHandler,
            getAdminRequestDetailHandler = getAdminRequestDetailHandler,
            approveVmRequestHandler = approveVmRequestHandler,
            rejectVmRequestHandler = rejectVmRequestHandler,
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

    @Nested
    @DisplayName("GET /api/admin/requests/{id}")
    inner class GetRequestDetailTests {

        private fun createTestDetail(
            id: VmRequestId = VmRequestId.generate(),
            requesterId: UserId = UserId.generate()
        ): AdminRequestDetail {
            val now = Instant.now()
            return AdminRequestDetail(
                id = id,
                vmName = "web-server-01",
                size = VmSizeInfo(
                    code = "M",
                    cpuCores = 4,
                    memoryGb = 8,
                    diskGb = 100
                ),
                justification = "Production web server",
                status = "PENDING",
                projectName = "Alpha Project",
                requester = RequesterInfo(
                    id = requesterId,
                    name = "John Doe",
                    email = "john.doe@example.com",
                    role = "developer"
                ),
                timeline = listOf(
                    TimelineEventItem(
                        eventType = TimelineEventType.CREATED,
                        actorName = "John Doe",
                        details = null,
                        occurredAt = now.minusSeconds(3600)
                    )
                ),
                requesterHistory = emptyList(),
                createdAt = now.minusSeconds(3600),
                version = 1L
            )
        }

        @Test
        @DisplayName("should return 200 OK with request details")
        fun `should return 200 OK with request details`() = runTest {
            // Given: AC 1 - Page loads with correct request details
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val detail = createTestDetail(id = requestId)

            coEvery {
                getAdminRequestDetailHandler.handle(any())
            } returns detail.success()

            // When
            val response = withTenant {
                controller.getRequestDetail(
                    id = requestId.value.toString(),
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as AdminRequestDetailResponse
            assertEquals(requestId.value.toString(), body.id)
            assertEquals("web-server-01", body.vmName)
            assertEquals("PENDING", body.status)
            assertEquals("Alpha Project", body.projectName)
        }

        @Test
        @DisplayName("should include requester info in response")
        fun `should include requester info in response`() = runTest {
            // Given: AC 2 - Requester Information displayed
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val requesterId = UserId.generate()
            val detail = createTestDetail(id = requestId, requesterId = requesterId)

            coEvery {
                getAdminRequestDetailHandler.handle(any())
            } returns detail.success()

            // When
            val response = withTenant {
                controller.getRequestDetail(
                    id = requestId.value.toString(),
                    jwt = jwt
                )
            }

            // Then: Requester info included
            val body = response.body as AdminRequestDetailResponse
            assertEquals(requesterId.value.toString(), body.requester.id)
            assertEquals("John Doe", body.requester.name)
            assertEquals("john.doe@example.com", body.requester.email)
            assertEquals("developer", body.requester.role)
        }

        @Test
        @DisplayName("should include VM size details in response")
        fun `should include VM size details in response`() = runTest {
            // Given: AC 3 - Request Details displayed
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val detail = createTestDetail(id = requestId)

            coEvery {
                getAdminRequestDetailHandler.handle(any())
            } returns detail.success()

            // When
            val response = withTenant {
                controller.getRequestDetail(
                    id = requestId.value.toString(),
                    jwt = jwt
                )
            }

            // Then: VM size details included
            val body = response.body as AdminRequestDetailResponse
            assertEquals("M", body.size.code)
            assertEquals(4, body.size.cpuCores)
            assertEquals(8, body.size.memoryGb)
            assertEquals(100, body.size.diskGb)
        }

        @Test
        @DisplayName("should include timeline in response")
        fun `should include timeline in response`() = runTest {
            // Given: AC 5 - Timeline events displayed
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val detail = createTestDetail(id = requestId)

            coEvery {
                getAdminRequestDetailHandler.handle(any())
            } returns detail.success()

            // When
            val response = withTenant {
                controller.getRequestDetail(
                    id = requestId.value.toString(),
                    jwt = jwt
                )
            }

            // Then: Timeline included
            val body = response.body as AdminRequestDetailResponse
            assertEquals(1, body.timeline.size)
            assertEquals("CREATED", body.timeline[0].eventType)
            assertEquals("John Doe", body.timeline[0].actorName)
        }

        @Test
        @DisplayName("should return 404 for invalid UUID format")
        fun `should return 404 for invalid UUID format`() = runTest {
            // Given
            val jwt = createJwt()

            // When
            val response = withTenant {
                controller.getRequestDetail(
                    id = "not-a-valid-uuid",
                    jwt = jwt
                )
            }

            // Then: Returns 404 (not 400) per security pattern
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        @DisplayName("should return 404 for non-existent request")
        fun `should return 404 for non-existent request`() = runTest {
            // Given
            val jwt = createJwt()
            val requestId = VmRequestId.generate()

            coEvery {
                getAdminRequestDetailHandler.handle(any())
            } returns GetAdminRequestDetailError.NotFound.failure()

            // When
            val response = withTenant {
                controller.getRequestDetail(
                    id = requestId.value.toString(),
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        @DisplayName("should return 404 for forbidden access (security pattern)")
        fun `should return 404 for forbidden access`() = runTest {
            // Given: Security pattern - return 404 instead of 403 to prevent enumeration
            val jwt = createJwt()
            val requestId = VmRequestId.generate()

            coEvery {
                getAdminRequestDetailHandler.handle(any())
            } returns GetAdminRequestDetailError.Forbidden().failure()

            // When
            val response = withTenant {
                controller.getRequestDetail(
                    id = requestId.value.toString(),
                    jwt = jwt
                )
            }

            // Then: Returns 404 (not 403) per CLAUDE.md security pattern
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        @DisplayName("should return 500 for query failure")
        fun `should return 500 for query failure`() = runTest {
            // Given
            val jwt = createJwt()
            val requestId = VmRequestId.generate()

            coEvery {
                getAdminRequestDetailHandler.handle(any())
            } returns GetAdminRequestDetailError.QueryFailure(
                message = "Database error"
            ).failure()

            // When
            val response = withTenant {
                controller.getRequestDetail(
                    id = requestId.value.toString(),
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
        @DisplayName("should pass correct query to handler")
        fun `should pass correct query to handler`() = runTest {
            // Given
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val querySlot = slot<GetAdminRequestDetailQuery>()
            val detail = createTestDetail(id = requestId)

            coEvery {
                getAdminRequestDetailHandler.handle(capture(querySlot))
            } returns detail.success()

            // When
            withTenant {
                controller.getRequestDetail(
                    id = requestId.value.toString(),
                    jwt = jwt
                )
            }

            // Then
            val query = querySlot.captured
            assertEquals(requestId, query.requestId)
            assertEquals(testTenantId, query.tenantId)
        }
    }

    /**
     * Story 2.11: Approve endpoint unit tests.
     */
    @Nested
    @DisplayName("POST /api/admin/requests/{id}/approve")
    inner class ApproveRequestTests {

        @Test
        @DisplayName("should return 200 OK on successful approval")
        fun `should return 200 OK on successful approval`() = runTest {
            // Given
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val body = ApproveRequestBody(version = 1L)
            val approvalResult = de.acci.dvmm.application.vmrequest.ApproveVmRequestResult(requestId = requestId)

            coEvery {
                approveVmRequestHandler.handle(any(), any())
            } returns approvalResult.success()

            // When
            val response = withTenant {
                controller.approveRequest(
                    id = requestId.value.toString(),
                    body = body,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val responseBody = response.body as Map<String, Any>
            assertEquals(requestId.value.toString(), responseBody["requestId"])
            assertEquals("APPROVED", responseBody["status"])
        }

        @Test
        @DisplayName("should return 404 for non-existent request")
        fun `should return 404 for non-existent request`() = runTest {
            // Given
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val body = ApproveRequestBody(version = 1L)

            coEvery {
                approveVmRequestHandler.handle(any(), any())
            } returns de.acci.dvmm.application.vmrequest.ApproveVmRequestError.NotFound(
                requestId = requestId
            ).failure()

            // When
            val response = withTenant {
                controller.approveRequest(
                    id = requestId.value.toString(),
                    body = body,
                    jwt = jwt
                )
            }

            // Then - Returns 404 Not Found
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        @DisplayName("should return 404 for self-approval (Forbidden mapped to 404)")
        fun `should return 404 for self-approval`() = runTest {
            // Given - Security pattern: return 404 to prevent enumeration
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val body = ApproveRequestBody(version = 1L)

            coEvery {
                approveVmRequestHandler.handle(any(), any())
            } returns de.acci.dvmm.application.vmrequest.ApproveVmRequestError.Forbidden().failure()

            // When
            val response = withTenant {
                controller.approveRequest(
                    id = requestId.value.toString(),
                    body = body,
                    jwt = jwt
                )
            }

            // Then - Returns 404 (not 403) per security pattern
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        @DisplayName("should return 422 for invalid state")
        fun `should return 422 for invalid state`() = runTest {
            // Given
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val body = ApproveRequestBody(version = 1L)

            coEvery {
                approveVmRequestHandler.handle(any(), any())
            } returns de.acci.dvmm.application.vmrequest.ApproveVmRequestError.InvalidState(
                currentState = "APPROVED",
                message = "Request is already approved"
            ).failure()

            // When
            val response = withTenant {
                controller.approveRequest(
                    id = requestId.value.toString(),
                    body = body,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        }

        @Test
        @DisplayName("should return 409 for concurrency conflict")
        fun `should return 409 for concurrency conflict`() = runTest {
            // Given
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val body = ApproveRequestBody(version = 1L)

            coEvery {
                approveVmRequestHandler.handle(any(), any())
            } returns de.acci.dvmm.application.vmrequest.ApproveVmRequestError.ConcurrencyConflict(
                message = "Concurrent modification"
            ).failure()

            // When
            val response = withTenant {
                controller.approveRequest(
                    id = requestId.value.toString(),
                    body = body,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.CONFLICT, response.statusCode)
        }

        @Test
        @DisplayName("should return 500 for persistence failure")
        fun `should return 500 for persistence failure`() = runTest {
            // Given
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val body = ApproveRequestBody(version = 1L)

            coEvery {
                approveVmRequestHandler.handle(any(), any())
            } returns de.acci.dvmm.application.vmrequest.ApproveVmRequestError.PersistenceFailure(
                message = "Database error"
            ).failure()

            // When
            val response = withTenant {
                controller.approveRequest(
                    id = requestId.value.toString(),
                    body = body,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        }

        @Test
        @DisplayName("should return 404 for invalid UUID format")
        fun `should return 404 for invalid UUID format`() = runTest {
            // Given
            val jwt = createJwt()
            val body = ApproveRequestBody(version = 1L)

            // When
            val response = withTenant {
                controller.approveRequest(
                    id = "not-a-valid-uuid",
                    body = body,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
            coVerify(exactly = 0) { approveVmRequestHandler.handle(any(), any()) }
        }
    }

    /**
     * Story 2.11: Reject endpoint unit tests.
     */
    @Nested
    @DisplayName("POST /api/admin/requests/{id}/reject")
    inner class RejectRequestTests {

        @Test
        @DisplayName("should return 200 OK on successful rejection")
        fun `should return 200 OK on successful rejection`() = runTest {
            // Given
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val body = RejectRequestBody(
                version = 1L,
                reason = "Insufficient justification provided"
            )
            val rejectResult = de.acci.dvmm.application.vmrequest.RejectVmRequestResult(requestId = requestId)

            coEvery {
                rejectVmRequestHandler.handle(any(), any())
            } returns rejectResult.success()

            // When
            val response = withTenant {
                controller.rejectRequest(
                    id = requestId.value.toString(),
                    body = body,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val responseBody = response.body as Map<String, Any>
            assertEquals(requestId.value.toString(), responseBody["requestId"])
            assertEquals("REJECTED", responseBody["status"])
        }

        @Test
        @DisplayName("should return 404 for non-existent request")
        fun `should return 404 for non-existent request on reject`() = runTest {
            // Given
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val body = RejectRequestBody(
                version = 1L,
                reason = "Not enough resources available"
            )

            coEvery {
                rejectVmRequestHandler.handle(any(), any())
            } returns de.acci.dvmm.application.vmrequest.RejectVmRequestError.NotFound(
                requestId = requestId
            ).failure()

            // When
            val response = withTenant {
                controller.rejectRequest(
                    id = requestId.value.toString(),
                    body = body,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        @DisplayName("should return 404 for self-rejection (Forbidden mapped to 404)")
        fun `should return 404 for self-rejection`() = runTest {
            // Given - Security pattern: return 404 to prevent enumeration
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val body = RejectRequestBody(
                version = 1L,
                reason = "Budget constraints prevent approval"
            )

            coEvery {
                rejectVmRequestHandler.handle(any(), any())
            } returns de.acci.dvmm.application.vmrequest.RejectVmRequestError.Forbidden().failure()

            // When
            val response = withTenant {
                controller.rejectRequest(
                    id = requestId.value.toString(),
                    body = body,
                    jwt = jwt
                )
            }

            // Then - Returns 404 (not 403) per security pattern
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        @DisplayName("should return 422 for invalid state")
        fun `should return 422 for invalid state on reject`() = runTest {
            // Given
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val body = RejectRequestBody(
                version = 1L,
                reason = "Resource pool exhausted"
            )

            coEvery {
                rejectVmRequestHandler.handle(any(), any())
            } returns de.acci.dvmm.application.vmrequest.RejectVmRequestError.InvalidState(
                currentState = "REJECTED",
                message = "Request is already rejected"
            ).failure()

            // When
            val response = withTenant {
                controller.rejectRequest(
                    id = requestId.value.toString(),
                    body = body,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        }

        @Test
        @DisplayName("should return 422 for invalid reason")
        fun `should return 422 for invalid reason`() = runTest {
            // Given
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val body = RejectRequestBody(
                version = 1L,
                reason = "Invalid reason that triggers validation"
            )

            coEvery {
                rejectVmRequestHandler.handle(any(), any())
            } returns de.acci.dvmm.application.vmrequest.RejectVmRequestError.InvalidReason(
                message = "Reason must be between 10 and 500 characters"
            ).failure()

            // When
            val response = withTenant {
                controller.rejectRequest(
                    id = requestId.value.toString(),
                    body = body,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        }

        @Test
        @DisplayName("should return 409 for concurrency conflict")
        fun `should return 409 for concurrency conflict on reject`() = runTest {
            // Given
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val body = RejectRequestBody(
                version = 1L,
                reason = "Resource constraints prevent approval"
            )

            coEvery {
                rejectVmRequestHandler.handle(any(), any())
            } returns de.acci.dvmm.application.vmrequest.RejectVmRequestError.ConcurrencyConflict(
                message = "Concurrent modification"
            ).failure()

            // When
            val response = withTenant {
                controller.rejectRequest(
                    id = requestId.value.toString(),
                    body = body,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.CONFLICT, response.statusCode)
        }

        @Test
        @DisplayName("should return 500 for persistence failure")
        fun `should return 500 for persistence failure on reject`() = runTest {
            // Given
            val jwt = createJwt()
            val requestId = VmRequestId.generate()
            val body = RejectRequestBody(
                version = 1L,
                reason = "Technical limitations prevent approval"
            )

            coEvery {
                rejectVmRequestHandler.handle(any(), any())
            } returns de.acci.dvmm.application.vmrequest.RejectVmRequestError.PersistenceFailure(
                message = "Database error"
            ).failure()

            // When
            val response = withTenant {
                controller.rejectRequest(
                    id = requestId.value.toString(),
                    body = body,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        }

        @Test
        @DisplayName("should return 404 for invalid UUID format")
        fun `should return 404 for invalid UUID format on reject`() = runTest {
            // Given
            val jwt = createJwt()
            val body = RejectRequestBody(
                version = 1L,
                reason = "This reason should not matter"
            )

            // When
            val response = withTenant {
                controller.rejectRequest(
                    id = "invalid-uuid-format",
                    body = body,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
            coVerify(exactly = 0) { rejectVmRequestHandler.handle(any(), any()) }
        }
    }
}
