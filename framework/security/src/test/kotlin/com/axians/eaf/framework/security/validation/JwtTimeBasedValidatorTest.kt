package com.axians.eaf.framework.security.validation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for JwtTimeBasedValidator (Layer 5: Time-Based Validation).
 *
 * Tests validate exp, iat, and nbf claims with 30-second clock skew tolerance,
 * ensuring tokens are not expired, not issued in the future, and not used before valid time.
 *
 * Story 3.5: JWT Claims Schema and Time-Based Validation (Layers 3-5)
 */
class JwtTimeBasedValidatorTest :
    FunSpec({
        val fixedInstant = Instant.parse("2025-11-09T12:00:00Z")
        val fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
        val clockSkew = Duration.ofSeconds(30)

        test("valid JWT with exp in future should pass validation") {
            val validator = JwtTimeBasedValidator(clockSkew, fixedClock)
            val jwt =
                createJwt(
                    exp = fixedInstant.plusSeconds(3600), // 1 hour from now
                    iat = fixedInstant.minusSeconds(60), // 1 minute ago
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe false
        }

        test("expired JWT beyond clock skew should fail validation") {
            val validator = JwtTimeBasedValidator(clockSkew, fixedClock)
            val jwt =
                createJwt(
                    exp = fixedInstant.minusSeconds(31), // Expired 31 seconds ago (beyond 30s skew)
                    iat = fixedInstant.minusSeconds(3600),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe true
            result.errors.first().errorCode shouldBe "invalid_token"
            result.errors.first().description shouldContain "JWT expired"
        }

        test("expired JWT within clock skew should pass validation") {
            val validator = JwtTimeBasedValidator(clockSkew, fixedClock)
            val jwt =
                createJwt(
                    exp = fixedInstant.minusSeconds(29), // Expired 29 seconds ago (within 30s skew)
                    iat = fixedInstant.minusSeconds(3600),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe false
        }

        test("JWT issued in future beyond clock skew should fail validation") {
            val validator = JwtTimeBasedValidator(clockSkew, fixedClock)
            val jwt =
                createJwt(
                    exp = fixedInstant.plusSeconds(3600),
                    iat = fixedInstant.plusSeconds(31), // Issued 31 seconds in future (beyond 30s skew)
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe true
            result.errors.first().errorCode shouldBe "invalid_token"
            result.errors.first().description shouldContain "JWT issued in the future"
        }

        test("JWT issued in future within clock skew should pass validation") {
            val validator = JwtTimeBasedValidator(clockSkew, fixedClock)
            val jwt =
                createJwt(
                    exp = fixedInstant.plusSeconds(3600),
                    iat = fixedInstant.plusSeconds(29), // Issued 29 seconds in future (within 30s skew)
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe false
        }

        test("JWT with nbf in future beyond clock skew should fail validation") {
            val validator = JwtTimeBasedValidator(clockSkew, fixedClock)
            val jwt =
                createJwt(
                    exp = fixedInstant.plusSeconds(3600),
                    iat = fixedInstant,
                    nbf = fixedInstant.plusSeconds(31), // Not valid for 31 seconds (beyond 30s skew)
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe true
            result.errors.first().errorCode shouldBe "invalid_token"
            result.errors.first().description shouldContain "JWT not valid before"
        }

        test("JWT with nbf in future within clock skew should pass validation") {
            val validator = JwtTimeBasedValidator(clockSkew, fixedClock)
            val jwt =
                createJwt(
                    exp = fixedInstant.plusSeconds(3600),
                    iat = fixedInstant,
                    nbf = fixedInstant.plusSeconds(29), // Not valid for 29 seconds (within 30s skew)
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe false
        }

        test("JWT with nbf in past should pass validation") {
            val validator = JwtTimeBasedValidator(clockSkew, fixedClock)
            val jwt =
                createJwt(
                    exp = fixedInstant.plusSeconds(3600),
                    iat = fixedInstant.minusSeconds(60),
                    nbf = fixedInstant.minusSeconds(60),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe false
        }

        test("JWT without nbf claim should pass validation") {
            val validator = JwtTimeBasedValidator(clockSkew, fixedClock)
            val jwt =
                createJwt(
                    exp = fixedInstant.plusSeconds(3600),
                    iat = fixedInstant,
                    nbf = null,
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe false
        }

        test("JWT without exp claim should pass validation (optional claim)") {
            val validator = JwtTimeBasedValidator(clockSkew, fixedClock)
            val jwt =
                createJwt(
                    exp = null,
                    iat = fixedInstant,
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe false
        }

        test("JWT without iat claim should pass validation (optional claim)") {
            val validator = JwtTimeBasedValidator(clockSkew, fixedClock)
            val jwt =
                createJwt(
                    exp = fixedInstant.plusSeconds(3600),
                    iat = null,
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe false
        }

        test("custom clock skew duration should be respected") {
            val customClockSkew = Duration.ofSeconds(60)
            val validator = JwtTimeBasedValidator(customClockSkew, fixedClock)
            val jwt =
                createJwt(
                    exp = fixedInstant.minusSeconds(59), // Expired 59 seconds ago (within 60s skew)
                    iat = fixedInstant.minusSeconds(3600),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe false
        }
    })

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
