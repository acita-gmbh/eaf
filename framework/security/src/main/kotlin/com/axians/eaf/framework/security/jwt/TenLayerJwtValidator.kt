package com.axians.eaf.framework.security.jwt

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.axians.eaf.framework.security.errors.SecurityError
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Enterprise-grade 10-layer JWT validation system.
 * Implements comprehensive security validation addressing all major attack vectors.
 */
@Component
class TenLayerJwtValidator(
    private val formatValidator: JwtFormatValidator,
    private val claimsValidator: JwtClaimsValidator,
    private val securityValidator: JwtSecurityValidator,
    private val meterRegistry: MeterRegistry,
) : JwtValidationService {
    companion object {
        private val logger = LoggerFactory.getLogger(TenLayerJwtValidator::class.java)
    }

    override fun validate(jwt: Jwt): Either<JwtValidationError, JwtValidationResult> =
        validateTenLayers(jwt.tokenValue)
            .mapLeft { securityError ->
                // Convert SecurityError to JwtValidationError for interface compatibility
                when (securityError) {
                    is SecurityError.UnsupportedAlgorithm -> JwtValidationError.AlgorithmError(securityError.algorithm)
                    is SecurityError.InvalidIssuer -> JwtValidationError.IssuerError(securityError.actual)
                    is SecurityError.InvalidAudience -> JwtValidationError.AudienceError(listOf(securityError.actual))
                    is SecurityError.TokenExpired -> JwtValidationError.ExpirationError(securityError.expiredAt.toString())
                    else -> JwtValidationError.MalformedTokenError(securityError.message)
                }
            }.map { validationResult ->
                // Convert ValidationResult to JwtValidationResult for interface compatibility
                JwtValidationResult(
                    userId = validationResult.user.id,
                    tenantId = validationResult.tenantId,
                    roles = validationResult.roles.map { it.name }.toSet(),
                    issuer = validationResult.issuer,
                    audience = listOf(validationResult.audience),
                    issuedAt = validationResult.issuedAt.epochSecond,
                    expiresAt = validationResult.expiresAt.epochSecond,
                )
            }

    /**
     * Performs comprehensive 10-layer JWT validation.
     * Each layer addresses specific attack vectors and security concerns.
     */
    fun validateTenLayers(token: String): Either<SecurityError, ValidationResult> {
        val startTime = System.nanoTime()

        return try {
            performLayerValidation(token, startTime)
        } catch (e: SecurityError) {
            recordValidationFailure(e, startTime)
            Either.Left(e)
        } catch (e: IllegalArgumentException) {
            val securityError = SecurityError.ClaimExtractionError(e.message ?: "Invalid argument")
            recordValidationFailure(securityError, startTime)
            Either.Left(securityError)
        } catch (e: ClassCastException) {
            val securityError = SecurityError.ClaimExtractionError(e.message ?: "Invalid type cast")
            recordValidationFailure(securityError, startTime)
            Either.Left(securityError)
        }
    }

    private fun performLayerValidation(token: String, startTime: Long): Either<SecurityError, ValidationResult> {
        // Layer 1: Format Validation
        formatValidator.validateBasicFormat(token).fold(
            ifLeft = { return Either.Left(it) },
            ifRight = { recordLayerSuccess(1) }
        )

        // Layer 2-3: Signature and Algorithm Validation
        val jwt = formatValidator.verifySignature(token).fold(
            ifLeft = { return Either.Left(it) },
            ifRight = {
                recordLayerSuccess(2)
                it
            }
        )

        formatValidator.ensureRS256Algorithm(jwt).fold(
            ifLeft = { return Either.Left(it) },
            ifRight = { recordLayerSuccess(3) }
        )

        // Layer 4-6: Claims, Time, and Issuer Validation
        val claims = claimsValidator.validateClaimSchema(jwt).fold(
            ifLeft = { return Either.Left(it) },
            ifRight = {
                recordLayerSuccess(4)
                it
            }
        )

        // Layer 5-6: Time and Issuer Validation
        claimsValidator.ensureNotExpired(claims).fold(
            ifLeft = { return Either.Left(it) },
            ifRight = { recordLayerSuccess(5) }
        )

        claimsValidator.validateIssuerAudience(claims).fold(
            ifLeft = { return Either.Left(it) },
            ifRight = { recordLayerSuccess(6) }
        )

        // Layer 7-10: Security Context Validation
        val (roles, user) = performSecurityValidation(claims, token).fold(
            ifLeft = { return Either.Left(it) },
            ifRight = { it }
        )

        return Either.Right(createValidationResult(claims, roles, user, startTime))
    }

    private fun performSecurityValidation(claims: JwtClaims, token: String): Either<SecurityError, Pair<Set<Role>, User>> {
        // Layer 7: Revocation Check
        securityValidator.ensureNotRevoked(claims.jti).fold(
            ifLeft = { return Either.Left(it) },
            ifRight = { recordLayerSuccess(7) }
        )

        // Layer 8: Role Validation
        val roles = securityValidator.validateRoles(claims.roles).fold(
            ifLeft = { return Either.Left(it) },
            ifRight = {
                recordLayerSuccess(8)
                it
            }
        )

        // Layer 9: User Validation
        val user = securityValidator.validateUser(claims.sub).fold(
            ifLeft = { return Either.Left(it) },
            ifRight = {
                recordLayerSuccess(9)
                it
            }
        )

        // Layer 10: Injection Detection
        securityValidator.ensureNoInjection(token).fold(
            ifLeft = { return Either.Left(it) },
            ifRight = { recordLayerSuccess(10) }
        )

        return Either.Right(Pair(roles, user))
    }

    private fun createValidationResult(claims: JwtClaims, roles: Set<Role>, user: User, startTime: Long): ValidationResult {
        val validationResult = ValidationResult(
            user = user,
            roles = roles,
            tenantId = claims.tenantId,
            sessionId = claims.sessionId,
            issuer = claims.iss,
            audience = claims.aud,
            expiresAt = Instant.ofEpochSecond(claims.exp),
            issuedAt = Instant.ofEpochSecond(claims.iat),
        )

        // Record successful validation
        val duration = Duration.ofNanos(System.nanoTime() - startTime)
        meterRegistry.timer("jwt.validation.success").record(duration)

        logger.info(
            "JWT validation successful - userId: {}, tenantId: {}, duration: {}ms",
            user.id, claims.tenantId, duration.toMillis(),
        )

        return validationResult
    }

    private fun recordLayerSuccess(layer: Int) {
        meterRegistry
            .counter(
                "jwt.validation.layer.success",
                "layer",
                layer.toString(),
            ).increment()
    }

    private fun recordValidationFailure(
        error: SecurityError,
        startTime: Long,
    ) {
        val duration = Duration.ofNanos(System.nanoTime() - startTime)

        meterRegistry
            .counter(
                "jwt.validation.failure",
                "error_type",
                error::class.simpleName ?: "Unknown",
            ).increment()

        meterRegistry
            .timer("jwt.validation.failure_duration")
            .record(duration)

        logger.error(
            "JWT validation failed - error: {}, duration: {}ms",
            error::class.simpleName,
            duration.toMillis(),
        )
    }
}

/**
 * Supporting data classes for JWT validation
 */
data class JwtClaims(
    val sub: String,
    val iss: String,
    val aud: String,
    val exp: Long,
    val iat: Long,
    val jti: String,
    val tenantId: String,
    val roles: List<String>,
    val sessionId: String?,
)

data class ValidationResult(
    val user: User,
    val roles: Set<Role>,
    val tenantId: String,
    val sessionId: String?,
    val issuer: String,
    val audience: String,
    val expiresAt: Instant,
    val issuedAt: Instant,
)

data class User(
    val id: String,
    val isActive: Boolean,
    val isLocked: Boolean,
    val isExpired: Boolean,
)

data class Role(
    val name: String,
    val description: String,
)
