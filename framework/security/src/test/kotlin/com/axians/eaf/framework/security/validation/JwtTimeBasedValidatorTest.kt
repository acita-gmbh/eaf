package com.axians.eaf.framework.security.validation

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for JwtTimeBasedValidator - Layer 5 of 10-layer JWT validation system.
 *
 * Validates JWT time-based claims (exp, iat, nbf) with configurable clock skew tolerance
 * (default 30 seconds) to prevent token replay attacks, expired token usage, and future
 * token acceptance while accommodating reasonable time synchronization differences.
 *
 * **Test Coverage:**
 * - Expiration validation (exp) with clock skew tolerance
 * - Issued-at validation (iat) preventing future-issued tokens
 * - Not-before validation (nbf) enforcing delayed token activation
 * - Clock skew tolerance (within/beyond 30-second threshold)
 * - Optional claim handling (exp, iat, nbf may be missing)
 * - Custom clock skew configuration (configurable tolerance)
 * - Fixed clock testing (deterministic time-based tests)
 *
 * **Security Patterns:**
 * - Token replay attack prevention (expired tokens rejected)
 * - Future token rejection (iat in future beyond skew = rejection)
 * - Delayed activation support (nbf enforces "not valid before" time)
 * - Clock skew tolerance (30s default to accommodate NTP drift)
 * - Fail-closed validation (invalid time = rejection)
 * - Time synchronization resilience (tolerates reasonable drift)
 *
 * **Testing Strategy:**
 * - Fixed Clock: Deterministic time-based testing (no flakiness)
 * - Boundary testing: Within/beyond clock skew (29s vs 31s)
 * - SimpleMeterRegistry for metrics validation
 * - Nullable Pattern: No Spring context, fast unit tests
 *
 * **Acceptance Criteria:**
 * - Story 3.5: Time-based claim validation (exp, iat, nbf)
 * - Story 3.5: Clock skew tolerance (30-second default)
 *
 * @see JwtTimeBasedValidator Primary class under test
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
 */
class JwtTimeBasedValidatorTest {
    private val fixedInstant = Instant.parse("2025-11-09T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
    private val clockSkew = Duration.ofSeconds(30)

    @Test
    fun `valid JWT with exp in future should pass validation`() {
        val validator = JwtTimeBasedValidator(clockSkew, fixedClock, SimpleMeterRegistry())
        val jwt =
            createJwt(
                exp = fixedInstant.plusSeconds(3600), // 1 hour from now
                iat = fixedInstant.minusSeconds(60), // 1 minute ago
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `expired JWT beyond clock skew should fail validation`() {
        val validator = JwtTimeBasedValidator(clockSkew, fixedClock, SimpleMeterRegistry())
        val jwt =
            createJwt(
                exp = fixedInstant.minusSeconds(31), // Expired 31 seconds ago (beyond 30s skew)
                iat = fixedInstant.minusSeconds(3600),
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().errorCode).isEqualTo("invalid_token")
        assertThat(result.errors.first().description).contains("JWT expired")
    }

    @Test
    fun `expired JWT within clock skew should pass validation`() {
        val validator = JwtTimeBasedValidator(clockSkew, fixedClock, SimpleMeterRegistry())
        val jwt =
            createJwt(
                exp = fixedInstant.minusSeconds(29), // Expired 29 seconds ago (within 30s skew)
                iat = fixedInstant.minusSeconds(3600),
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `JWT issued in future beyond clock skew should fail validation`() {
        val validator = JwtTimeBasedValidator(clockSkew, fixedClock, SimpleMeterRegistry())
        val jwt =
            createJwt(
                exp = fixedInstant.plusSeconds(3600),
                iat = fixedInstant.plusSeconds(31), // Issued 31 seconds in future (beyond 30s skew)
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().errorCode).isEqualTo("invalid_token")
        assertThat(result.errors.first().description).contains("JWT issued in the future")
    }

    @Test
    fun `JWT issued in future within clock skew should pass validation`() {
        val validator = JwtTimeBasedValidator(clockSkew, fixedClock, SimpleMeterRegistry())
        val jwt =
            createJwt(
                exp = fixedInstant.plusSeconds(3600),
                iat = fixedInstant.plusSeconds(29), // Issued 29 seconds in future (within 30s skew)
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `JWT with nbf in future beyond clock skew should fail validation`() {
        val validator = JwtTimeBasedValidator(clockSkew, fixedClock, SimpleMeterRegistry())
        val jwt =
            createJwt(
                exp = fixedInstant.plusSeconds(3600),
                iat = fixedInstant,
                nbf = fixedInstant.plusSeconds(31), // Not valid for 31 seconds (beyond 30s skew)
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().errorCode).isEqualTo("invalid_token")
        assertThat(result.errors.first().description).contains("JWT not valid before")
    }

    @Test
    fun `JWT with nbf in future within clock skew should pass validation`() {
        val validator = JwtTimeBasedValidator(clockSkew, fixedClock, SimpleMeterRegistry())
        val jwt =
            createJwt(
                exp = fixedInstant.plusSeconds(3600),
                iat = fixedInstant,
                nbf = fixedInstant.plusSeconds(29), // Not valid for 29 seconds (within 30s skew)
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `JWT with nbf in past should pass validation`() {
        val validator = JwtTimeBasedValidator(clockSkew, fixedClock, SimpleMeterRegistry())
        val jwt =
            createJwt(
                exp = fixedInstant.plusSeconds(3600),
                iat = fixedInstant.minusSeconds(60),
                nbf = fixedInstant.minusSeconds(60),
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `JWT without nbf claim should pass validation`() {
        val validator = JwtTimeBasedValidator(clockSkew, fixedClock, SimpleMeterRegistry())
        val jwt =
            createJwt(
                exp = fixedInstant.plusSeconds(3600),
                iat = fixedInstant,
                nbf = null,
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `JWT without exp claim should pass validation (optional claim)`() {
        val validator = JwtTimeBasedValidator(clockSkew, fixedClock, SimpleMeterRegistry())
        val jwt =
            createJwt(
                exp = null,
                iat = fixedInstant,
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `JWT without iat claim should pass validation (optional claim)`() {
        val validator = JwtTimeBasedValidator(clockSkew, fixedClock, SimpleMeterRegistry())
        val jwt =
            createJwt(
                exp = fixedInstant.plusSeconds(3600),
                iat = null,
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `custom clock skew duration should be respected`() {
        val customClockSkew = Duration.ofSeconds(60)
        val validator = JwtTimeBasedValidator(customClockSkew, fixedClock, SimpleMeterRegistry())
        val jwt =
            createJwt(
                exp = fixedInstant.minusSeconds(59), // Expired 59 seconds ago (within 60s skew)
                iat = fixedInstant.minusSeconds(3600),
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }
}

/**
 * Helper function to create a test JWT with specified time-based claims.
 */
private fun createJwt(
    exp: Instant?,
    iat: Instant?,
    nbf: Instant? = null,
): Jwt {
    val headers = mapOf("alg" to "RS256", "typ" to "JWT")
    val tokenValue = "mock-token-value"

    return Jwt
        .withTokenValue(tokenValue)
        .headers { h -> h.putAll(headers) }
        .claims {
            it["sub"] = "user"
            it["jti"] = "time-test-jti"
        }.apply {
            if (exp != null) expiresAt(exp)
            if (iat != null) issuedAt(iat)
            if (nbf != null) notBefore(nbf)
        }.build()
}
