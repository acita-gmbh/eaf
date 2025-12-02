package de.acci.dvmm.api.vmrequest

import de.acci.dvmm.application.vmrequest.CancelVmRequestCommand
import de.acci.dvmm.application.vmrequest.CancelVmRequestError
import de.acci.dvmm.application.vmrequest.CancelVmRequestHandler
import de.acci.dvmm.application.vmrequest.CancelVmRequestResult
import de.acci.dvmm.application.vmrequest.CreateVmRequestCommand
import de.acci.dvmm.application.vmrequest.CreateVmRequestError
import de.acci.dvmm.application.vmrequest.CreateVmRequestHandler
import de.acci.dvmm.application.vmrequest.CreateVmRequestResult
import de.acci.dvmm.application.vmrequest.GetMyRequestsError
import de.acci.dvmm.application.vmrequest.GetMyRequestsHandler
import de.acci.dvmm.application.vmrequest.GetMyRequestsQuery
import de.acci.dvmm.application.vmrequest.GetRequestDetailError
import de.acci.dvmm.application.vmrequest.GetRequestDetailHandler
import de.acci.dvmm.application.vmrequest.GetRequestDetailQuery
import de.acci.dvmm.application.vmrequest.TimelineEventItem
import de.acci.dvmm.application.vmrequest.TimelineEventType
import de.acci.dvmm.application.vmrequest.VmRequestDetail
import de.acci.dvmm.application.vmrequest.VmRequestSummary
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.dvmm.domain.vmrequest.VmRequestId
import de.acci.dvmm.domain.vmrequest.VmRequestStatus
import de.acci.dvmm.domain.vmrequest.VmSize
import de.acci.eaf.core.types.UserId
import de.acci.eaf.eventsourcing.projection.PagedResponse
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.tenant.TenantContext
import de.acci.eaf.tenant.TenantContextElement
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.util.UUID

@DisplayName("VmRequestController")
class VmRequestControllerTest {

    private val createHandler = mockk<CreateVmRequestHandler>()
    private val getMyRequestsHandler = mockk<GetMyRequestsHandler>()
    private val cancelHandler = mockk<CancelVmRequestHandler>()
    private val getRequestDetailHandler = mockk<GetRequestDetailHandler>()
    private lateinit var controller: VmRequestController
    private val testTenantId = TenantId.generate()

    @BeforeEach
    fun setup() {
        controller = VmRequestController(
            createVmRequestHandler = createHandler,
            getMyRequestsHandler = getMyRequestsHandler,
            cancelVmRequestHandler = cancelHandler,
            getRequestDetailHandler = getRequestDetailHandler
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

    private fun createValidRequest() = CreateVmRequestRequest(
        vmName = "web-server-01",
        projectId = UUID.randomUUID().toString(),
        size = "M",
        justification = "Valid justification for testing purposes"
    )

    private suspend fun <T> withTenant(block: suspend () -> T): T {
        return withContext(TenantContextElement(testTenantId)) {
            block()
        }
    }

    @Nested
    @DisplayName("POST /api/requests")
    inner class CreateRequestTests {

        @Test
        @DisplayName("should return 201 Created with Location header on success")
        fun `should return 201 Created with Location header on success`() = runTest {
            // Given
            val request = createValidRequest()
            val jwt = createJwt()
            val requestId = VmRequestId.generate()

            coEvery {
                createHandler.handle(any(), any())
            } returns CreateVmRequestResult(requestId).success()

            // When
            val response = withTenant {
                controller.createVmRequest(request, jwt)
            }

            // Then
            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertTrue(
                response.headers.location?.toString()?.contains(requestId.value.toString()) == true,
                "Location header should contain request ID"
            )
        }

        @Test
        @DisplayName("should return response body with request data")
        fun `should return response body with request data`() = runTest {
            // Given
            val request = createValidRequest()
            val jwt = createJwt()
            val requestId = VmRequestId.generate()

            coEvery {
                createHandler.handle(any(), any())
            } returns CreateVmRequestResult(requestId).success()

            // When
            val response = withTenant {
                controller.createVmRequest(request, jwt)
            }

            // Then
            val body = response.body as VmRequestResponse
            assertEquals(requestId.value.toString(), body.id)
            assertEquals(request.vmName, body.vmName)
            assertEquals(request.projectId, body.projectId)
            assertEquals("M", body.size.code)
            assertEquals("PENDING", body.status)
        }

        @Test
        @DisplayName("should pass command with tenant and user context")
        fun `should pass command with tenant and user context`() = runTest {
            // Given
            val request = createValidRequest()
            val userId = UUID.randomUUID().toString()
            val jwt = createJwt(subject = userId)
            val commandSlot = slot<CreateVmRequestCommand>()

            coEvery {
                createHandler.handle(capture(commandSlot), any())
            } returns CreateVmRequestResult(VmRequestId.generate()).success()

            // When
            withTenant {
                controller.createVmRequest(request, jwt)
            }

            // Then
            val command = commandSlot.captured
            assertEquals(testTenantId, command.tenantId)
            assertEquals(userId, command.requesterId.value.toString())
            assertEquals(request.justification, command.justification)
        }

        @Test
        @DisplayName("should return 400 for invalid VM name")
        fun `should return 400 for invalid VM name`() = runTest {
            // Given
            val request = CreateVmRequestRequest(
                vmName = "Invalid VM Name!", // Contains spaces and invalid chars
                projectId = UUID.randomUUID().toString(),
                size = "M",
                justification = "Valid justification for testing purposes"
            )
            val jwt = createJwt()

            // When
            val response = withTenant {
                controller.createVmRequest(request, jwt)
            }

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            val body = response.body as ValidationErrorResponse
            assertEquals("validation", body.type)
            assertTrue(body.errors.any { it.field == "vmName" })
        }

        @Test
        @DisplayName("should return 400 for VM name with consecutive hyphens")
        fun `should return 400 for VM name with consecutive hyphens`() = runTest {
            // Given
            val request = CreateVmRequestRequest(
                vmName = "web--server", // Consecutive hyphens not allowed
                projectId = UUID.randomUUID().toString(),
                size = "M",
                justification = "Valid justification for testing purposes"
            )
            val jwt = createJwt()

            // When
            val response = withTenant {
                controller.createVmRequest(request, jwt)
            }

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            val body = response.body as ValidationErrorResponse
            assertEquals("validation", body.type)
            assertTrue(body.errors.any { it.field == "vmName" })
        }

        @Test
        @DisplayName("should return 400 for invalid size")
        fun `should return 400 for invalid size`() = runTest {
            // Given
            val request = CreateVmRequestRequest(
                vmName = "web-server-01",
                projectId = UUID.randomUUID().toString(),
                size = "XXL", // Invalid size
                justification = "Valid justification for testing purposes"
            )
            val jwt = createJwt()

            // When
            val response = withTenant {
                controller.createVmRequest(request, jwt)
            }

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            val body = response.body as ValidationErrorResponse
            assertTrue(body.errors.any { it.field == "size" })
        }

        @Test
        @DisplayName("should return 409 Conflict for quota exceeded")
        fun `should return 409 Conflict for quota exceeded`() = runTest {
            // Given
            val request = createValidRequest()
            val jwt = createJwt()

            coEvery {
                createHandler.handle(any(), any())
            } returns CreateVmRequestError.QuotaExceeded(
                available = 0,
                requested = 1
            ).failure()

            // When
            val response = withTenant {
                controller.createVmRequest(request, jwt)
            }

            // Then
            assertEquals(HttpStatus.CONFLICT, response.statusCode)
            val body = response.body as QuotaExceededResponse
            assertEquals("quota_exceeded", body.type)
            assertEquals(0, body.available)
            assertEquals(1, body.requested)
        }

        @Test
        @DisplayName("should return 409 Conflict for concurrency conflict")
        fun `should return 409 Conflict for concurrency conflict`() = runTest {
            // Given
            val request = createValidRequest()
            val jwt = createJwt()

            coEvery {
                createHandler.handle(any(), any())
            } returns CreateVmRequestError.ConcurrencyConflict(
                message = "Concurrent modification detected"
            ).failure()

            // When
            val response = withTenant {
                controller.createVmRequest(request, jwt)
            }

            // Then
            assertEquals(HttpStatus.CONFLICT, response.statusCode)
            val body = response.body as ConcurrencyConflictResponse
            assertEquals("concurrency_conflict", body.type)
            assertEquals("Concurrent modification detected", body.message)
        }

        @Test
        @DisplayName("should return size with resource specifications")
        fun `should return size with resource specifications`() = runTest {
            // Given
            val request = createValidRequest().copy(size = "XL")
            val jwt = createJwt()

            coEvery {
                createHandler.handle(any(), any())
            } returns CreateVmRequestResult(VmRequestId.generate()).success()

            // When
            val response = withTenant {
                controller.createVmRequest(request, jwt)
            }

            // Then
            val body = response.body as VmRequestResponse
            assertEquals("XL", body.size.code)
            assertEquals(16, body.size.cpuCores)
            assertEquals(32, body.size.memoryGb)
            assertEquals(500, body.size.diskGb)
        }

        @Test
        @DisplayName("should return 500 for persistence failure")
        fun `should return 500 for persistence failure`() = runTest {
            // Given
            val request = createValidRequest()
            val jwt = createJwt()

            coEvery {
                createHandler.handle(any(), any())
            } returns CreateVmRequestError.PersistenceFailure(
                message = "Database connection failed"
            ).failure()

            // When
            val response = withTenant {
                controller.createVmRequest(request, jwt)
            }

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            val body = response.body as InternalErrorResponse
            assertEquals("internal_error", body.type)
        }
    }

    @Nested
    @DisplayName("GET /api/requests/my")
    inner class GetMyRequestsTests {

        @Test
        @DisplayName("should return 200 OK with paginated requests")
        fun `should return 200 OK with paginated requests`() = runTest {
            // Given
            val jwt = createJwt()
            val requestSummary = createTestSummary()

            coEvery {
                getMyRequestsHandler.handle(any())
            } returns PagedResponse(
                items = listOf(requestSummary),
                page = 0,
                size = 20,
                totalElements = 1L
            ).success()

            // When
            val response = withTenant {
                controller.getMyRequests(page = 0, size = 20, jwt = jwt)
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as PagedVmRequestsResponse
            assertEquals(1, body.items.size)
            assertEquals(requestSummary.id.value.toString(), body.items[0].id)
        }

        @Test
        @DisplayName("should return empty page when no requests")
        fun `should return empty page when no requests`() = runTest {
            // Given
            val jwt = createJwt()

            coEvery {
                getMyRequestsHandler.handle(any())
            } returns PagedResponse<VmRequestSummary>(
                items = emptyList(),
                page = 0,
                size = 20,
                totalElements = 0L
            ).success()

            // When
            val response = withTenant {
                controller.getMyRequests(page = 0, size = 20, jwt = jwt)
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as PagedVmRequestsResponse
            assertEquals(0, body.items.size)
            assertEquals(0L, body.totalElements)
        }

        @Test
        @DisplayName("should validate and coerce pagination parameters")
        fun `should validate and coerce pagination parameters`() = runTest {
            // Given
            val jwt = createJwt()
            val querySlot = slot<GetMyRequestsQuery>()

            coEvery {
                getMyRequestsHandler.handle(capture(querySlot))
            } returns PagedResponse<VmRequestSummary>(
                items = emptyList(),
                page = 0,
                size = 100,
                totalElements = 0L
            ).success()

            // When - page size > 100 should be coerced to 100
            withTenant {
                controller.getMyRequests(page = -1, size = 200, jwt = jwt)
            }

            // Then
            val query = querySlot.captured
            assertEquals(0, query.pageRequest.page) // Negative page coerced to 0
            assertEquals(100, query.pageRequest.size) // Size > 100 coerced to 100
        }

        @Test
        @DisplayName("should return 500 for query failure")
        fun `should return 500 for query failure`() = runTest {
            // Given
            val jwt = createJwt()

            coEvery {
                getMyRequestsHandler.handle(any())
            } returns GetMyRequestsError.QueryFailure(
                message = "Database error"
            ).failure()

            // When
            val response = withTenant {
                controller.getMyRequests(page = 0, size = 20, jwt = jwt)
            }

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            val body = response.body as InternalErrorResponse
            assertEquals("internal_error", body.type)
        }
    }

    @Nested
    @DisplayName("GET /api/requests/{id}")
    inner class GetRequestDetailTests {

        @Test
        @DisplayName("should return 200 OK with request detail")
        fun `should return 200 OK with request detail`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val jwt = createJwt()
            val detail = createTestDetail(requestId)

            coEvery {
                getRequestDetailHandler.handle(any())
            } returns detail.success()

            // When
            val response = withTenant {
                controller.getRequestDetail(id = requestId.value.toString(), jwt = jwt)
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as VmRequestDetailResponse
            assertEquals(requestId.value.toString(), body.id)
            assertEquals(detail.vmName, body.vmName)
            assertEquals(detail.status, body.status)
        }

        @Test
        @DisplayName("should include timeline events in response")
        fun `should include timeline events in response`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val jwt = createJwt()
            val detail = createTestDetail(requestId)

            coEvery {
                getRequestDetailHandler.handle(any())
            } returns detail.success()

            // When
            val response = withTenant {
                controller.getRequestDetail(id = requestId.value.toString(), jwt = jwt)
            }

            // Then
            val body = response.body as VmRequestDetailResponse
            assertEquals(1, body.timeline.size)
            assertEquals("CREATED", body.timeline[0].eventType)
        }

        @Test
        @DisplayName("should pass correct query parameters to handler")
        fun `should pass correct query parameters to handler`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val userId = UUID.randomUUID().toString()
            val jwt = createJwt(subject = userId)
            val querySlot = slot<GetRequestDetailQuery>()

            coEvery {
                getRequestDetailHandler.handle(capture(querySlot))
            } returns createTestDetail(requestId).success()

            // When
            withTenant {
                controller.getRequestDetail(id = requestId.value.toString(), jwt = jwt)
            }

            // Then
            val query = querySlot.captured
            assertEquals(testTenantId, query.tenantId)
            assertEquals(requestId, query.requestId)
            assertEquals(userId, query.userId.value.toString())
        }

        @Test
        @DisplayName("should return 400 for invalid request ID format")
        fun `should return 400 for invalid request ID format`() = runTest {
            // Given
            val jwt = createJwt()

            // When
            val response = withTenant {
                controller.getRequestDetail(id = "not-a-valid-uuid", jwt = jwt)
            }

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            val body = response.body as ValidationErrorResponse
            assertTrue(body.errors.any { it.field == "id" })
        }

        @Test
        @DisplayName("should return 404 when request not found")
        fun `should return 404 when request not found`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val jwt = createJwt()

            coEvery {
                getRequestDetailHandler.handle(any())
            } returns GetRequestDetailError.NotFound(requestId).failure()

            // When
            val response = withTenant {
                controller.getRequestDetail(id = requestId.value.toString(), jwt = jwt)
            }

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
            val body = response.body as NotFoundResponse
            assertEquals("not_found", body.type)
        }

        @Test
        @DisplayName("should return 500 for query failure")
        fun `should return 500 for query failure`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val jwt = createJwt()

            coEvery {
                getRequestDetailHandler.handle(any())
            } returns GetRequestDetailError.QueryFailure(
                message = "Database error"
            ).failure()

            // When
            val response = withTenant {
                controller.getRequestDetail(id = requestId.value.toString(), jwt = jwt)
            }

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            val body = response.body as InternalErrorResponse
            assertEquals("internal_error", body.type)
        }

        private fun createTestDetail(requestId: VmRequestId): VmRequestDetail {
            val now = Instant.now()
            return VmRequestDetail(
                id = requestId,
                vmName = "test-vm",
                size = "M",
                cpuCores = 4,
                memoryGb = 16,
                diskGb = 200,
                justification = "Test justification",
                status = "PENDING",
                projectName = "Test Project",
                requesterName = "Test User",
                createdAt = now,
                timeline = listOf(
                    TimelineEventItem(
                        eventType = TimelineEventType.CREATED,
                        actorName = "Test User",
                        details = null,
                        occurredAt = now
                    )
                )
            )
        }
    }

    @Nested
    @DisplayName("POST /api/requests/{id}/cancel")
    inner class CancelRequestTests {

        @Test
        @DisplayName("should return 200 OK on successful cancellation with type field")
        fun `should return 200 OK on successful cancellation with type field`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val jwt = createJwt()

            coEvery {
                cancelHandler.handle(any(), any())
            } returns CancelVmRequestResult(requestId).success()

            // When
            val response = withTenant {
                controller.cancelRequest(
                    id = requestId.value.toString(),
                    request = null,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as CancelSuccessResponse
            assertEquals("cancelled", body.type)
            assertEquals("Request cancelled successfully", body.message)
            assertEquals(requestId.value.toString(), body.requestId)
        }

        @Test
        @DisplayName("should accept optional cancellation reason")
        fun `should accept optional cancellation reason`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val jwt = createJwt()
            val commandSlot = slot<CancelVmRequestCommand>()

            coEvery {
                cancelHandler.handle(capture(commandSlot), any())
            } returns CancelVmRequestResult(requestId).success()

            // When
            withTenant {
                controller.cancelRequest(
                    id = requestId.value.toString(),
                    request = CancelVmRequestRequest(reason = "No longer needed"),
                    jwt = jwt
                )
            }

            // Then
            val command = commandSlot.captured
            assertEquals("No longer needed", command.reason)
        }

        @Test
        @DisplayName("should return 400 for invalid request ID format")
        fun `should return 400 for invalid request ID format`() = runTest {
            // Given
            val jwt = createJwt()

            // When
            val response = withTenant {
                controller.cancelRequest(
                    id = "not-a-valid-uuid",
                    request = null,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            val body = response.body as ValidationErrorResponse
            assertTrue(body.errors.any { it.field == "id" })
        }

        @Test
        @DisplayName("should return 404 when request not found")
        fun `should return 404 when request not found`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val jwt = createJwt()

            coEvery {
                cancelHandler.handle(any(), any())
            } returns CancelVmRequestError.NotFound(requestId).failure()

            // When
            val response = withTenant {
                controller.cancelRequest(
                    id = requestId.value.toString(),
                    request = null,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
            val body = response.body as NotFoundResponse
            assertEquals("not_found", body.type)
        }

        @Test
        @DisplayName("should return 403 when user is not request owner")
        fun `should return 403 when user is not request owner`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val jwt = createJwt()

            coEvery {
                cancelHandler.handle(any(), any())
            } returns CancelVmRequestError.Forbidden().failure()

            // When
            val response = withTenant {
                controller.cancelRequest(
                    id = requestId.value.toString(),
                    request = null,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
            val body = response.body as ForbiddenResponse
            assertEquals("forbidden", body.type)
        }

        @Test
        @DisplayName("should return 409 when request is not in cancellable state")
        fun `should return 409 when request is not in cancellable state`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val jwt = createJwt()

            coEvery {
                cancelHandler.handle(any(), any())
            } returns CancelVmRequestError.InvalidState(
                currentState = "APPROVED"
            ).failure()

            // When
            val response = withTenant {
                controller.cancelRequest(
                    id = requestId.value.toString(),
                    request = null,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.CONFLICT, response.statusCode)
            val body = response.body as InvalidStateResponse
            assertEquals("invalid_state", body.type)
            assertEquals("APPROVED", body.currentState)
        }

        @Test
        @DisplayName("should return 409 for concurrency conflict")
        fun `should return 409 for concurrency conflict`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val jwt = createJwt()

            coEvery {
                cancelHandler.handle(any(), any())
            } returns CancelVmRequestError.ConcurrencyConflict(
                message = "Concurrent modification"
            ).failure()

            // When
            val response = withTenant {
                controller.cancelRequest(
                    id = requestId.value.toString(),
                    request = null,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.CONFLICT, response.statusCode)
            val body = response.body as ConcurrencyConflictResponse
            assertEquals("concurrency_conflict", body.type)
        }

        @Test
        @DisplayName("should return 500 for persistence failure")
        fun `should return 500 for persistence failure`() = runTest {
            // Given
            val requestId = VmRequestId.generate()
            val jwt = createJwt()

            coEvery {
                cancelHandler.handle(any(), any())
            } returns CancelVmRequestError.PersistenceFailure(
                message = "Database error"
            ).failure()

            // When
            val response = withTenant {
                controller.cancelRequest(
                    id = requestId.value.toString(),
                    request = null,
                    jwt = jwt
                )
            }

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            val body = response.body as InternalErrorResponse
            assertEquals("internal_error", body.type)
        }
    }

    private fun createTestSummary(): VmRequestSummary {
        val now = Instant.now()
        return VmRequestSummary(
            id = VmRequestId.generate(),
            tenantId = testTenantId,
            requesterId = UserId.generate(),
            requesterName = "Test User",
            projectId = ProjectId.generate(),
            projectName = "Test Project",
            vmName = "test-vm",
            size = VmSize.M,
            justification = "Test justification",
            status = VmRequestStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
    }
}
