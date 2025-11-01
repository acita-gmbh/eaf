package com.axians.eaf.framework.security.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.axians.eaf.framework.security.errors.SecurityError
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

/**
 * Service for extracting tenant ID from authenticated JWT tokens.
 * Extracted from TenantContextFilter for unit testability (Story 8.6).
 *
 * Implements fail-closed design for missing/invalid tenant claims.
 */
@Service
class TenantExtractionService {
    companion object {
        private const val TENANT_CLAIM = "tenant_id"
    }

    /**
     * Extracts tenant ID from Spring Security authentication context.
     *
     * @param authentication The authentication object from SecurityContextHolder
     * @return Either error or tenant ID
     */
    fun extractTenantId(authentication: Authentication?): Either<SecurityError, String> =
        when {
            authentication == null -> SecurityError.NoAuthentication.left()
            authentication !is JwtAuthenticationToken ->
                SecurityError.NonJwtAuthentication(authentication.javaClass.simpleName).left()
            else -> {
                val jwt: Jwt = authentication.token
                val tenantId = jwt.getClaimAsString(TENANT_CLAIM)
                when {
                    tenantId.isNullOrBlank() -> SecurityError.MissingTenantClaim.left()
                    else -> tenantId.right()
                }
            }
        }

    /**
     * Convenience method that throws exception on error (for filter usage).
     *
     * @return Tenant ID if present, null if no authentication
     * @throws IllegalStateException for authentication errors
     */
    fun extractTenantIdOrNull(authentication: Authentication?): String? =
        when (val result = extractTenantId(authentication)) {
            is Either.Left ->
                when (result.value) {
                    is SecurityError.NoAuthentication -> null
                    is SecurityError.NonJwtAuthentication ->
                        error("Authentication is not JWT-based")
                    is SecurityError.MissingTenantClaim ->
                        error("Missing or invalid tenant_id claim")
                    else -> error("Tenant extraction failed")
                }
            is Either.Right -> result.value
        }
}
