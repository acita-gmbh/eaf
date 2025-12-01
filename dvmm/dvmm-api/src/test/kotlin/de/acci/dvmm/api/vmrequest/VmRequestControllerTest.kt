package de.acci.dvmm.api.vmrequest

import de.acci.dvmm.application.vmrequest.CreateVmRequestCommand
import de.acci.dvmm.application.vmrequest.CreateVmRequestError
import de.acci.dvmm.application.vmrequest.CreateVmRequestHandler
import de.acci.dvmm.application.vmrequest.CreateVmRequestResult
import de.acci.dvmm.domain.vmrequest.VmRequestId
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

    private val handler = mockk<CreateVmRequestHandler>()
    private lateinit var controller: VmRequestController
    private val testTenantId = TenantId.generate()

    @BeforeEach
    fun setup() {
        controller = VmRequestController(handler)
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
                handler.handle(any(), any())
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
                handler.handle(any(), any())
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
                handler.handle(capture(commandSlot), any())
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
                handler.handle(any(), any())
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
                handler.handle(any(), any())
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
                handler.handle(any(), any())
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
    }
}
