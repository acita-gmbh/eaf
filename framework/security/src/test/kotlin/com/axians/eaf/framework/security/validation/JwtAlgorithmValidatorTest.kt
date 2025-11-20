package com.axians.eaf.framework.security.validation

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * Unit tests for JwtAlgorithmValidator - Layer 3 of 10-layer JWT validation system.
 *
 * Validates JWT algorithm header enforcement (RS256 only) to prevent algorithm confusion
 * attacks (CVE-2018-0114) where attackers downgrade cryptographic strength or bypass
 * signature verification entirely using 'none' algorithm.
 *
 * **Test Coverage:**
 * - RS256 algorithm acceptance (only allowed algorithm)
 * - Forbidden algorithm rejection (HS256, HS384, HS512, 'none')
 * - Algorithm confusion attack prevention (symmetric vs asymmetric)
 * - Unsupported algorithm detection (RS384, RS512, ES256)
 * - Missing algorithm header detection
 * - Case sensitivity enforcement (rs256 rejected)
 * - Performance validation (1000 validations <100ms)
 *
 * **Security Patterns:**
 * - Algorithm whitelist enforcement (RS256 only, fail-closed)
 * - CVE-2018-0114 mitigation ('none' algorithm bypass prevention)
 * - Algorithm confusion attack protection (reject symmetric algorithms)
 * - Strict header validation (no defaults, explicit rejection)
 * - Nullable Design Pattern for fast security validation
 *
 * **Testing Strategy:**
 * - Nullable Pattern: No Spring context, no Keycloak (100-1000x faster)
 * - SimpleMeterRegistry for metrics validation
 * - Performance baseline: <0.1ms per validation
 * - Comprehensive attack vector coverage
 *
 * **Acceptance Criteria:**
 * - Story 3.4 AC3: RS256 algorithm enforcement (only RS256 allowed)
 * - Story 3.4: Algorithm confusion attack prevention
 *
 * @see JwtAlgorithmValidator Primary class under test
 * @see <a href="https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2018-0114">CVE-2018-0114</a>
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
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
