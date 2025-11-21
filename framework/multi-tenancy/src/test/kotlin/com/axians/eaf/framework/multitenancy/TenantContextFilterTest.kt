package com.axians.eaf.framework.multitenancy

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

/**
 * Unit tests for TenantContextFilter - Layer 1 tenant extraction from JWT claims.
 *
 * Validates the first defense layer of EAF's 3-layer multi-tenancy enforcement system,
 * ensuring tenant_id is properly extracted from JWT tokens and propagated through the
 * request lifecycle with fail-closed security and comprehensive error handling.
 *
 * **Test Coverage:**
 * - JWT tenant_id claim extraction and TenantContext population
 * - Fail-closed behavior (missing tenant_id = 400 Bad Request, no chain execution)
 * - ThreadLocal cleanup in finally block (success and exception cases)
 * - Metrics emission (extraction duration timer, error counters)
 * - Non-JWT request handling (skip tenant extraction gracefully)
 * - SecurityContextHolder integration with JwtAuthenticationToken
 *
 * **Multi-Tenancy Patterns:**
 * - Layer 1: JWT-based tenant extraction (request filter)
 * - Fail-closed security (missing tenant blocks request immediately)
 * - ThreadLocal context with guaranteed cleanup (finally block)
 * - Defense-in-depth integration with Layer 2 (service validation) and Layer 3 (PostgreSQL RLS)
 * - Generic error messages (CWE-209 protection)
 *
 * **Test Strategy:**
 * - Spring MockHttpServletRequest/Response (no mocking framework)
 * - Real SecurityContextHolder with JwtAuthenticationToken
 * - SimpleMeterRegistry for metrics validation
 * - FilterChain capture pattern for mid-execution assertions
 * - Exception propagation verification (cleanup despite errors)
 *
 * **Acceptance Criteria:**
 * - Story 4.2 AC2: JWT tenant_id claim extracted to TenantContext
 * - Story 4.2 AC3: TenantContext populated during request lifecycle
 * - Story 4.2 AC4: Missing tenant_id returns 400 Bad Request
 * - Story 4.2 AC5: ThreadLocal cleanup in finally block
 * - Story 4.2 AC7: Metrics emitted for extraction duration and failures
 *
 * @see TenantContextFilter Primary class under test
 * @see TenantContext ThreadLocal tenant storage
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
 */
class TenantContextFilterTest {
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var objectMapper: ObjectMapper
    private lateinit var filter: TenantContextFilter
    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse
    private lateinit var filterChain: FilterChain

    @BeforeEach
    fun beforeEach() {
        meterRegistry = SimpleMeterRegistry()
        objectMapper = ObjectMapper()
        filter = TenantContextFilter(meterRegistry, objectMapper)

        // Use Spring mock request/response
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()

        // Simple filter chain (no-op for basic tests)
        filterChain = FilterChain { _: ServletRequest, _: ServletResponse -> }

        // Clear SecurityContextHolder before each test
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun afterEach() {
        // Ensure cleanup after each test
        TenantContext.clearCurrentTenant()
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `AC2+AC3 - Extract tenant_id from JWT and populate TenantContext`() {
        // Given: JWT with tenant_id claim
        val tenantId = "tenant-unit-test"
        val jwt =
            createMockJwt(
                subject = "user123",
                tenantId = tenantId,
            )
        val authentication = JwtAuthenticationToken(jwt)
        SecurityContextHolder.getContext().authentication = authentication

        var contextDuringChain: String? = null
        val chainWithCapture =
            FilterChain { _: ServletRequest, _: ServletResponse ->
                contextDuringChain = TenantContext.current()
            }

        // When: Filter executes
        filter.doFilter(request, response, chainWithCapture)

        // Then: TenantContext should be populated during chain execution
        assertThat(contextDuringChain).isEqualTo(tenantId)

        // After filter completes, cleanup happens in finally
        assertThat(TenantContext.current()).isNull()
    }

    @Test
    fun `AC4 - Missing tenant_id claim rejects request with 400 Bad Request`() {
        // Given: JWT WITHOUT tenant_id claim
        val jwt = createMockJwtWithoutTenant(subject = "user456")
        val authentication = JwtAuthenticationToken(jwt)
        SecurityContextHolder.getContext().authentication = authentication

        var chainCalled = false
        val chainWithFlag =
            FilterChain { _: ServletRequest, _: ServletResponse ->
                chainCalled = true
            }

        // When: Filter executes
        filter.doFilter(request, response, chainWithFlag)

        // Then: Response is 400 Bad Request
        assertThat(response.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
        assertThat(response.contentType).isEqualTo("application/json")

        val responseBody = response.contentAsString
        assertThat(responseBody).contains("Missing required tenant context")
        assertThat(responseBody).contains("400")

        // Verify chain was NOT called
        assertThat(chainCalled).isFalse()

        // Verify missing_tenant_failures counter incremented
        val counter = meterRegistry.counter("missing_tenant_failures", "reason", "missing_claim")
        assertThat(counter.count()).isEqualTo(1.0)
    }

    @Test
    fun `AC5 - ThreadLocal cleanup in finally block after successful request`() {
        // Given: JWT with tenant_id
        val tenantId = "tenant-cleanup-test"
        val jwt = createMockJwt(subject = "user789", tenantId = tenantId)
        val authentication = JwtAuthenticationToken(jwt)
        SecurityContextHolder.getContext().authentication = authentication

        // When: Filter executes
        filter.doFilter(request, response, filterChain)

        // Then: TenantContext should be cleared after filter completes
        assertThat(TenantContext.current()).isNull()
    }

    @Test
    fun `AC5 - ThreadLocal cleanup in finally block even when exception occurs`() {
        // Given: JWT with tenant_id
        val tenantId = "tenant-exception-test"
        val jwt = createMockJwt(subject = "user999", tenantId = tenantId)
        val authentication = JwtAuthenticationToken(jwt)
        SecurityContextHolder.getContext().authentication = authentication

        // Filter chain that throws exception
        val throwingChain =
            FilterChain { _: ServletRequest, _: ServletResponse ->
                throw TestFilterException("Simulated error")
            }

        // When: Filter executes and exception is thrown
        assertThrows<TestFilterException> {
            filter.doFilter(request, response, throwingChain)
        }

        // Then: TenantContext should STILL be cleared (finally block executed)
        assertThat(TenantContext.current()).isNull()

        // Verify error counter incremented
        val errorCounter =
            meterRegistry.counter(
                "tenant_context_extraction_errors",
                "error_type",
                "TestFilterException",
            )
        assertThat(errorCounter.count()).isEqualTo(1.0)
    }

    @Test
    fun `AC7 - Metrics emitted - tenant_context_extraction_duration timer`() {
        // Given: JWT with tenant_id
        val jwt = createMockJwt(subject = "metrics-user", tenantId = "tenant-metrics")
        val authentication = JwtAuthenticationToken(jwt)
        SecurityContextHolder.getContext().authentication = authentication

        // When: Filter executes
        filter.doFilter(request, response, filterChain)

        // Then: Timer should be incremented
        val timer =
            meterRegistry.timer(
                "tenant_context_extraction_duration",
                "layer",
                "1-jwt-extraction",
            )
        assertThat(timer.count()).isEqualTo(1L)
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isGreaterThan(0.0)
    }

    @Test
    fun `Filter skips tenant extraction for non-JWT requests`() {
        // Given: Non-JWT authentication (e.g., anonymous or other auth type)
        SecurityContextHolder.getContext().authentication = null

        var chainCalled = false
        val chainWithFlag =
            FilterChain { _: ServletRequest, _: ServletResponse ->
                chainCalled = true
            }

        // When: Filter executes
        filter.doFilter(request, response, chainWithFlag)

        // Then: Chain should be called without tenant context
        assertThat(chainCalled).isTrue()

        // TenantContext should remain empty
        assertThat(TenantContext.current()).isNull()
    }
}

/**
 * Create mock JWT with tenant_id claim.
 */
private fun createMockJwt(
    subject: String,
    tenantId: String,
): Jwt =
    Jwt
        .withTokenValue("mock-token")
        .header("alg", "RS256")
        .header("typ", "JWT")
        .claim("sub", subject)
        .claim("tenant_id", tenantId)
        .claim("iss", "https://keycloak.example.com/realms/eaf-test")
        .claim("aud", "eaf-client")
        .claim("exp", Instant.now().plusSeconds(3600))
        .claim("iat", Instant.now())
        .claim("roles", listOf("user"))
        .build()

/**
 * Create mock JWT WITHOUT tenant_id claim (for negative testing).
 */
private fun createMockJwtWithoutTenant(subject: String): Jwt =
    Jwt
        .withTokenValue("mock-token-no-tenant")
        .header("alg", "RS256")
        .header("typ", "JWT")
        .claim("sub", subject)
        .claim("iss", "https://keycloak.example.com/realms/eaf-test")
        .claim("aud", "eaf-client")
        .claim("exp", Instant.now().plusSeconds(3600))
        .claim("iat", Instant.now())
        .claim("roles", listOf("user"))
        .build()

/**
 * Specific exception for testing filter exception handling (avoids detekt TooGenericExceptionThrown).
 */
private class TestFilterException(
    message: String,
) : Exception(message)
