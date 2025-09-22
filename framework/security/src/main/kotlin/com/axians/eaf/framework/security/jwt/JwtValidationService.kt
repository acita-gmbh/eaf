package com.axians.eaf.framework.security.jwt

import arrow.core.Either
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Extensible JWT validation service interface.
 * Foundation for Story 3.3's 10-layer JWT validation implementation.
 */
interface JwtValidationService {
    /**
     * Validates JWT token with extensible validation layers.
     * @param jwt The JWT token to validate
     * @return Either validation error or validation result
     */
    fun validate(jwt: Jwt): Either<JwtValidationError, JwtValidationResult>
}

/**
 * JWT validation errors for comprehensive error handling.
 */
sealed class JwtValidationError(
    override val message: String,
) : Exception(message) {
    data class AlgorithmError(
        val algorithm: String?,
    ) : JwtValidationError("Unsupported algorithm: $algorithm")

    data class IssuerError(
        val issuer: String?,
    ) : JwtValidationError("Invalid issuer: $issuer")

    data class AudienceError(
        val audience: List<String>?,
    ) : JwtValidationError("Invalid audience: $audience")

    data class ExpirationError(
        val expiry: String,
    ) : JwtValidationError("Token expired: $expiry")

    data class MalformedTokenError(
        val details: String,
    ) : JwtValidationError("Malformed token: $details")
}

/**
 * JWT validation result containing extracted claims and context.
 */
data class JwtValidationResult(
    val userId: String,
    val tenantId: String,
    val roles: Set<String>,
    val issuer: String,
    val audience: List<String>,
    val issuedAt: Long,
    val expiresAt: Long,
)

/**
 * Basic JWT validation service implementation.
 * Foundation for enhanced validation in Story 3.3.
 */
class BasicJwtValidationService : JwtValidationService {
    override fun validate(jwt: Jwt): Either<JwtValidationError, JwtValidationResult> = validateJwtClaims(jwt)

    private fun validateJwtClaims(jwt: Jwt): Either<JwtValidationError, JwtValidationResult> =
        try {
            extractAndValidateClaims(jwt)
        } catch (e: IllegalArgumentException) {
            Either.Left(JwtValidationError.MalformedTokenError(e.message ?: "Claim extraction error"))
        } catch (e: ClassCastException) {
            Either.Left(JwtValidationError.MalformedTokenError(e.message ?: "Invalid claim type"))
        }

    private fun extractAndValidateClaims(jwt: Jwt): Either<JwtValidationError, JwtValidationResult> =
        validateRequiredClaims(jwt).map { claims ->
            JwtValidationResult(
                userId = claims.userId,
                tenantId = claims.tenantId,
                roles = JwtClaimsExtractor.extractRoles(jwt),
                issuer = claims.issuer,
                audience = claims.audience,
                issuedAt = jwt.issuedAt?.epochSecond ?: 0L,
                expiresAt = jwt.expiresAt?.epochSecond ?: 0L,
            )
        }

    private fun validateRequiredClaims(jwt: Jwt): Either<JwtValidationError, RequiredClaims> {
        val userId = jwt.getClaimAsString("sub")
        val tenantId = jwt.getClaimAsString("tenant_id")
        val issuer = jwt.issuer?.toString()
        val audience = jwt.audience

        return when {
            userId == null -> Either.Left(JwtValidationError.MalformedTokenError("Missing subject claim"))
            tenantId == null ->
                Either.Left(
                    JwtValidationError.MalformedTokenError("Missing tenant_id claim"),
                )
            issuer == null -> Either.Left(JwtValidationError.IssuerError("Missing issuer"))
            audience == null -> Either.Left(JwtValidationError.AudienceError(null))
            else -> Either.Right(RequiredClaims(userId, tenantId, issuer, audience))
        }
    }

    private data class RequiredClaims(
        val userId: String,
        val tenantId: String,
        val issuer: String,
        val audience: List<String>,
    )
}
