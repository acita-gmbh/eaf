package com.axians.eaf.framework.security.validation

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * JWT Time-Based Validator - Validates exp, iat, and nbf claims with clock skew tolerance.
 *
 * Layer 5 (Architecture): Time-based Validation
 * - Validates `exp` (expiration time): Token must not be expired
 * - Validates `iat` (issued at): Token must have been issued in the past
 * - Validates `nbf` (not before): Token must not be used before valid time
 * - Applies 30-second clock skew tolerance for distributed system clock drift
 *
 * Security Rationale:
 * - Prevents replay attacks with expired tokens
 * - Prevents future-dated tokens (potential time manipulation)
 * - Clock skew tolerance handles legitimate clock drift between systems
 * - Defense-in-depth: Time validation complements signature validation
 *
 * Configuration:
 * - Default clock skew: 30 seconds (configurable via constructor)
 * - Uses system clock by default (testable via Clock injection)
 *
 * Story 3.5: JWT Claims Schema and Time-Based Validation (Layers 3-5)
 * Story 3.9: Added per-layer metrics instrumentation
 */
@Component
class JwtTimeBasedValidator(
    private val clockSkew: Duration = Duration.ofSeconds(30),
    private val clock: Clock = Clock.systemUTC(),
    meterRegistry: MeterRegistry,
) : MeteredTokenValidator("layer5_time_based", meterRegistry) {
    /**
     * Validates time-based claims (exp, iat, nbf) with clock skew tolerance.
     *
     * Returns a failure result if:
     * - Token is expired (`exp` + clock skew < now)
     * - Token issued in the future (`iat` - clock skew > now)
     * - Token used before valid time (`nbf` - clock skew > now)
     *
     * Returns success if all time-based validations pass.
     *
     * @param token The JWT whose time-based claims will be validated.
     * @return An OAuth2TokenValidatorResult representing validation failure with an `invalid_token` error,
     *         or success when all time checks pass.
     */
    override fun doValidate(token: Jwt): OAuth2TokenValidatorResult {
        val now = Instant.now(clock)

        val error =
            validateExpiration(token, now)
                ?: validateIssuedAt(token, now)
                ?: validateNotBefore(token, now)

        return error?.let { OAuth2TokenValidatorResult.failure(it) }
            ?: OAuth2TokenValidatorResult.success()
    }

    private fun validateExpiration(
        token: Jwt,
        now: Instant,
    ): OAuth2Error? {
        val expiresAt = token.expiresAt
        return if (expiresAt != null && now.isAfter(expiresAt.plus(clockSkew))) {
            OAuth2Error(
                "invalid_token",
                "JWT expired at $expiresAt (now: $now, clock skew: ${clockSkew.seconds}s)",
                null,
            )
        } else {
            null
        }
    }

    private fun validateIssuedAt(
        token: Jwt,
        now: Instant,
    ): OAuth2Error? {
        val issuedAt = token.issuedAt
        return if (issuedAt != null && now.isBefore(issuedAt.minus(clockSkew))) {
            OAuth2Error(
                "invalid_token",
                "JWT issued in the future at $issuedAt (now: $now, clock skew: ${clockSkew.seconds}s)",
                null,
            )
        } else {
            null
        }
    }

    private fun validateNotBefore(
        token: Jwt,
        now: Instant,
    ): OAuth2Error? {
        val notBefore = token.notBefore
        return if (notBefore != null && now.isBefore(notBefore.minus(clockSkew))) {
            OAuth2Error(
                "invalid_token",
                "JWT not valid before $notBefore (now: $now, clock skew: ${clockSkew.seconds}s)",
                null,
            )
        } else {
            null
        }
    }
}
