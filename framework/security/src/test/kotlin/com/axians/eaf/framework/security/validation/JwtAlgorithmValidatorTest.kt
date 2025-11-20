package com.axians.eaf.framework.security.validation

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * Unit test for JwtAlgorithmValidator using Nullable Design Pattern.
 *
 * Tests JWT algorithm validation logic (Layer 3) without Spring context.
 * Fast execution (<10ms) compared to integration tests (~30s with Keycloak).
 *
 * Nullable Pattern Benefits:
 * - 100-1000x performance improvement over integration tests
 * - Tests business logic in isolation
 * - No external dependencies (Keycloak, Spring context)
 * - Fast feedback loop for TDD
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 *
 * Story 3.4: JWT Format and Signature Validation (Layers 1-2)
 */
class JwtAlgorithmValidatorTest {

    private val validator = JwtAlgorithmValidator(SimpleMeterRegistry())

    /**
     * Creates a test JWT with specified algorithm header.
     * Uses Nullable Pattern - minimal valid JWT structure without real signature.
     */
    private fun createTestJwt(algorithm: String?): Jwt {
        val headers = if (algorithm != null) {
            mapOf("alg" to algorithm)
        } else {
            emptyMap()
        }

        return Jwt.withTokenValue("test.token.value")
            .headers { it.putAll(headers) }
            .claim("sub", "test-user")
            .claim("jti", "alg-test-jti")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }

    // RS256 algorithm validation

    @Test
    fun `should accept JWT with RS256 algorithm`() {
        // AC3: RS256 algorithm enforcement - only RS256 allowed
        val jwt = createTestJwt("RS256")

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `should reject JWT with RS384 algorithm`() {
        // Only RS256 allowed (strict enforcement)
        val jwt = createTestJwt("RS384")

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description).isEqualTo("JWT algorithm must be RS256 (actual: RS384)")
    }

    @Test
    fun `should reject JWT with RS512 algorithm`() {
        // Only RS256 allowed (strict enforcement)
        val jwt = createTestJwt("RS512")

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description).isEqualTo("JWT algorithm must be RS256 (actual: RS512)")
    }

    // Forbidden algorithm rejection

    @Test
    fun `should reject JWT with HS256 algorithm`() {
        // AC3: Reject HS256 (algorithm confusion attack prevention)
        val jwt = createTestJwt("HS256")

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description)
            .isEqualTo("JWT algorithm 'HS256' not allowed (security risk: algorithm confusion attack)")
    }

    @Test
    fun `should reject JWT with HS384 algorithm`() {
        val jwt = createTestJwt("HS384")

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
    }

    @Test
    fun `should reject JWT with HS512 algorithm`() {
        val jwt = createTestJwt("HS512")

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
    }

    @Test
    fun `should reject JWT with 'none' algorithm`() {
        // Security: Prevents CVE-2018-0114 (none algorithm bypass)
        val jwt = createTestJwt("none")

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description)
            .isEqualTo("JWT algorithm 'none' not allowed (security risk: algorithm confusion attack)")
    }

    // Edge cases

    @Test
    fun `should reject JWT with missing algorithm header`() {
        // Create JWT with typ header but no alg header (edge case)
        val jwt = Jwt.withTokenValue("test.token.value")
            .headers { it["typ"] = "JWT" } // Only typ, no alg
            .claim("sub", "test-user")
            .claim("jti", "alg-test-jti")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description).isEqualTo("JWT algorithm header missing")
    }

    @Test
    fun `should reject JWT with unsupported algorithm`() {
        val jwt = createTestJwt("ES256") // ECDSA not configured

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description).isEqualTo("JWT algorithm must be RS256 (actual: ES256)")
    }

    @Test
    fun `should reject JWT with lowercase algorithm`() {
        val jwt = createTestJwt("rs256") // Case sensitivity test

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
    }

    // Performance characteristics

    @Test
    fun `should validate 1000 JWTs in under 100ms`() {
        // Nullable Pattern performance advantage
        val jwt = createTestJwt("RS256")
        val iterations = 1000

        val startTime = System.nanoTime()
        repeat(iterations) {
            validator.validate(jwt)
        }
        val durationMs = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms

        // Should be <100ms for 1000 validations (<0.1ms per validation)
        // Demonstrates 100-1000x performance vs integration tests
        assertThat(durationMs).isLessThan(100L)
    }
}
