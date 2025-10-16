package com.axians.eaf.framework.security.jwt

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.axians.eaf.framework.security.errors.SecurityError
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * JWT format and signature validation (Layers 1-3).
 *
 * Story 6.2: Added @Profile("!test") to prevent loading in test environments.
 */
@Component
@Profile("!test") // Story 6.2: Requires JwtDecoder from SecurityConfiguration
class JwtFormatValidator(
    private val jwtDecoder: JwtDecoder,
    private val meterRegistry: MeterRegistry,
) {
    companion object {
        private val JWT_PATTERN = Regex("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")
        private const val MAX_TOKEN_SIZE = 8192
    }

    fun validateBasicFormat(token: String): Either<SecurityError, Unit> =
        when {
            token.isBlank() -> SecurityError.EmptyToken.left()
            !token.matches(JWT_PATTERN) -> SecurityError.InvalidTokenFormat.left()
            token.length > MAX_TOKEN_SIZE -> SecurityError.TokenTooLarge.left()
            token.split(".").size != 3 -> SecurityError.InvalidJwtStructure.left()
            else -> Unit.right()
        }

    fun verifySignature(token: String): Either<SecurityError, Jwt> =
        try {
            val jwt = jwtDecoder.decode(token)
            jwt.right()
        } catch (e: JwtException) {
            meterRegistry.counter("jwt.validation.signature_failure").increment()
            SecurityError.InvalidSignature(e.message ?: "Unknown signature error").left()
        }

    fun ensureRS256Algorithm(jwt: Jwt): Either<SecurityError, Unit> {
        val algorithm = jwt.headers["alg"] as? String
        return if (algorithm == "RS256") {
            Unit.right()
        } else {
            meterRegistry
                .counter(
                    "jwt.validation.algorithm_mismatch",
                    "algorithm",
                    algorithm ?: "null",
                ).increment()
            SecurityError.UnsupportedAlgorithm(algorithm).left()
        }
    }
}

/**
 * JWT claims and timing validation (Layers 4-6).
 *
 * Story 6.2: Added @Profile("!test") to prevent loading in test environments.
 * Story 9.1 Security Fix: Externalized issuer/audience for environment-specific configuration
 */
@Component
@Profile("!test") // Story 6.2: Requires live infrastructure validation
class JwtClaimsValidator(
    private val meterRegistry: MeterRegistry,
    @param:org.springframework.beans.factory.annotation.Value("\${eaf.security.jwt.expected-issuer}")
    private val expectedIssuer: String,
    @param:org.springframework.beans.factory.annotation.Value("\${eaf.security.jwt.expected-audience}")
    private val expectedAudience: String,
) {
    companion object {
        private const val CLOCK_SKEW_TOLERANCE_SECONDS = 60L
        private const val MAX_TOKEN_AGE_HOURS = 24L
    }

    fun validateClaimSchema(jwt: Jwt): Either<SecurityError, JwtClaims> {
        return try {
            val sub =
                jwt.getClaimAsString("sub")
                    ?: return SecurityError.MissingClaim("sub").left()
            val iss =
                jwt.getClaimAsString("iss")
                    ?: return SecurityError.MissingClaim("iss").left()
            val aud =
                jwt.audience?.firstOrNull()
                    ?: return SecurityError.MissingClaim("aud").left()
            val exp =
                jwt.expiresAt?.epochSecond
                    ?: return SecurityError.MissingClaim("exp").left()
            val iat =
                jwt.issuedAt?.epochSecond
                    ?: return SecurityError.MissingClaim("iat").left()
            val jti =
                jwt.getClaimAsString("jti")
                    ?: return SecurityError.MissingClaim("jti").left()
            val tenantId =
                jwt.getClaimAsString("tenant_id")
                    ?: return SecurityError.MissingClaim("tenant_id").left()

            val roles =
                JwtClaimsExtractor
                    .extractRoles(jwt)
                    .takeIf { it.isNotEmpty() }
                    ?.toList()
                    ?: (jwt.getClaimAsStringList("roles") ?: emptyList())

            val claims =
                JwtClaims(
                    sub = sub,
                    iss = iss,
                    aud = aud,
                    exp = exp,
                    iat = iat,
                    jti = jti,
                    tenantId = tenantId,
                    roles = roles,
                    sessionId = jwt.getClaimAsString("session_state"),
                )

            // Validate claim formats
            if (!isValidUUID(claims.tenantId)) {
                return SecurityError.InvalidClaimFormat("tenant_id", claims.tenantId).left()
            }

            if (!isValidUUID(claims.sub)) {
                return SecurityError.InvalidClaimFormat("sub", claims.sub).left()
            }

            claims.right()
        } catch (e: IllegalArgumentException) {
            SecurityError.ClaimExtractionError(e.message ?: "Unknown error").left()
        } catch (e: ClassCastException) {
            SecurityError.ClaimExtractionError(e.message ?: "Invalid claim type").left()
        }
    }

    fun ensureNotExpired(claims: JwtClaims): Either<SecurityError, Unit> {
        val now = Instant.now()
        val expiry = Instant.ofEpochSecond(claims.exp)
        val issued = Instant.ofEpochSecond(claims.iat)

        return when {
            // Check expiration with clock skew tolerance
            now.isAfter(expiry.plusSeconds(CLOCK_SKEW_TOLERANCE_SECONDS)) -> {
                meterRegistry.counter("jwt.validation.expired").increment()
                SecurityError.TokenExpired(expiry, now).left()
            }
            // Check issued-at time (prevent future tokens)
            issued.isAfter(now.plusSeconds(CLOCK_SKEW_TOLERANCE_SECONDS)) -> {
                meterRegistry.counter("jwt.validation.future_token").increment()
                SecurityError.FutureToken(issued, now).left()
            }
            // Check token age (prevent very old tokens)
            Duration.between(issued, now).toHours() > MAX_TOKEN_AGE_HOURS -> {
                meterRegistry.counter("jwt.validation.token_too_old").increment()
                SecurityError.TokenTooOld(issued, now).left()
            }
            else -> Unit.right()
        }
    }

    fun validateIssuerAudience(claims: JwtClaims): Either<SecurityError, Unit> =
        when {
            claims.iss != expectedIssuer -> {
                meterRegistry
                    .counter(
                        "jwt.validation.invalid_issuer",
                        "issuer",
                        claims.iss,
                    ).increment()
                SecurityError.InvalidIssuer(claims.iss, expectedIssuer).left()
            }
            claims.aud != expectedAudience -> {
                meterRegistry
                    .counter(
                        "jwt.validation.invalid_audience",
                        "audience",
                        claims.aud,
                    ).increment()
                SecurityError.InvalidAudience(claims.aud, expectedAudience).left()
            }
            else -> Unit.right()
        }

    private fun isValidUUID(value: String): Boolean =
        try {
            UUID.fromString(value)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
}

/**
 * JWT security context validation (Layers 7-10).
 *
 * Story 6.2: Added @Profile("!test") to prevent loading in test environments.
 */
@Component
@Profile("!test") // Story 6.2: Requires RedisTemplate (live infrastructure)
class JwtSecurityValidator(
    private val redisTemplate: RedisTemplate<String, String>,
    private val meterRegistry: MeterRegistry,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(JwtSecurityValidator::class.java)
        private val INJECTION_PATTERNS =
            listOf(
                "(?i)(union|select|insert|update|delete|drop)\\s",
                "(?i)(script|javascript|onerror|onload)",
                "(?i)(exec|execute|xp_|sp_)",
                "(?i)(ldap://|ldaps://|dns://)",
                "(?i)(<script|<iframe|<object|<embed)",
            )
    }

    fun ensureNotRevoked(jti: String): Either<SecurityError, Unit> =
        try {
            val isRevoked = redisTemplate.hasKey("revoked:$jti")
            if (isRevoked) {
                meterRegistry.counter("jwt.validation.revoked").increment()
                SecurityError.TokenRevoked(jti).left()
            } else {
                Unit.right()
            }
        } catch (e: org.springframework.data.redis.RedisConnectionFailureException) {
            // SECURITY: Fail-closed when Redis unavailable (emergency recovery requires revocation capability)
            meterRegistry.counter("jwt.validation.revocation_check_failed").increment()
            logger.error("Redis connection failed, BLOCKING token: ${e.message}")
            SecurityError
                .RevocationCheckFailed(
                    "Token revocation verification unavailable",
                ).left()
        } catch (e: org.springframework.dao.DataAccessException) {
            // SECURITY: Fail-closed when Redis data access fails
            meterRegistry.counter("jwt.validation.revocation_check_failed").increment()
            logger.error("Redis data access failed, BLOCKING token: ${e.message}")
            SecurityError
                .RevocationCheckFailed(
                    "Token revocation verification unavailable",
                ).left()
        }

    fun validateRoles(roleNames: List<String>): Either<SecurityError, Set<Role>> {
        return try {
            if (roleNames.isEmpty()) {
                meterRegistry.counter("jwt.validation.no_roles").increment()
                return SecurityError.NoRolesAssigned.left()
            }

            // Stubbed implementation - create test roles
            val roles =
                roleNames
                    .map { roleName ->
                        Role(name = roleName, description = "Role: $roleName")
                    }.toSet()

            // Check for privilege escalation attempts
            val suspiciousRoles = roles.filter { it.name.contains("admin", ignoreCase = true) }
            if (suspiciousRoles.isNotEmpty()) {
                logger.warn(
                    "Privilege escalation attempt detected - roles: {}",
                    suspiciousRoles.map { it.name },
                )
            }

            roles.right()
        } catch (e: IllegalArgumentException) {
            SecurityError.RoleValidationError(e.message ?: "Role validation error").left()
        } catch (e: ClassCastException) {
            SecurityError.RoleValidationError(e.message ?: "Invalid role type").left()
        }
    }

    fun validateUser(userId: String): Either<SecurityError, User> =
        try {
            // Stubbed implementation - create test user
            val user =
                User(
                    id = userId,
                    isActive = true,
                    isLocked = false,
                    isExpired = false,
                )

            when {
                !user.isActive -> {
                    meterRegistry.counter("jwt.validation.inactive_user").increment()
                    SecurityError.UserInactive(userId).left()
                }
                user.isLocked -> {
                    meterRegistry.counter("jwt.validation.locked_user").increment()
                    SecurityError.UserLocked(userId).left()
                }
                user.isExpired -> {
                    meterRegistry.counter("jwt.validation.expired_user").increment()
                    SecurityError.UserExpired(userId).left()
                }
                else -> user.right()
            }
        } catch (e: IllegalArgumentException) {
            SecurityError.UserValidationError(e.message ?: "User validation error").left()
        } catch (e: ClassCastException) {
            SecurityError.UserValidationError(e.message ?: "Invalid user data").left()
        }

    fun ensureNoInjection(token: String): Either<SecurityError, Unit> =
        try {
            // Decode token parts for injection scanning
            val parts = token.split(".")
            val header = String(Base64.getUrlDecoder().decode(parts[0]))
            val payload = String(Base64.getUrlDecoder().decode(parts[1]))
            val fullContent = "$header$payload"

            val detectedPatterns =
                INJECTION_PATTERNS.filter { pattern ->
                    Regex(pattern).containsMatchIn(fullContent)
                }

            if (detectedPatterns.isNotEmpty()) {
                meterRegistry
                    .counter(
                        "jwt.validation.injection_detected",
                        "patterns",
                        detectedPatterns.size.toString(),
                    ).increment()

                logger.error(
                    "Injection attempt detected - patterns: {}, token_hash: {}",
                    detectedPatterns,
                    token.take(20).hashCode(),
                )

                SecurityError.InjectionDetected(detectedPatterns).left()
            } else {
                Unit.right()
            }
        } catch (e: IllegalArgumentException) {
            SecurityError.InjectionCheckFailed(e.message ?: "Base64 decode failed").left()
        } catch (e: IndexOutOfBoundsException) {
            SecurityError.InjectionCheckFailed(e.message ?: "Token part missing").left()
        }
}
