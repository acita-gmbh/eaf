package com.axians.eaf.framework.security.validation

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

/**
 * JWT Claim Schema Validator - Enforces core required claims presence.
 *
 * Layer 4 (Architecture): Claim Schema Validation
 * - Validates presence of required claims: sub, iss, exp, iat
 * - Validates non-blank for critical claims: sub, tenant_id (when present)
 * - Optional claims validated by other layers: aud, tenant_id, roles
 *
 * Security Rationale:
 * - sub: User identity (REQUIRED, must be non-blank)
 * - iss: Token issuer (REQUIRED for trust boundary verification)
 * - exp, iat: Timestamps (REQUIRED for replay attack prevention)
 * - aud: Token audience (validated by Spring Security defaults in Layer 6, Epic 3.6)
 * - tenant_id: Multi-tenant isolation (OPTIONAL until Epic 4, non-blank if present)
 * - roles: Role-based access control (OPTIONAL until Epic 3.6+, validated in Layer 8)
 *
 * Note: aud, tenant_id, and roles are not enforced as required to maintain compatibility
 * with current Keycloak test configuration. They will be validated in dedicated layers:
 * - Epic 3.6: Issuer, Audience, and Role validation (Layers 6 & 8)
 * - Epic 4.2: Tenant context extraction and validation (Layer 1)
 *
 * Story 3.5: JWT Claims Schema and Time-Based Validation (Layers 3-5)
 */
class JwtClaimSchemaValidator : OAuth2TokenValidator<Jwt> {
    companion object {
        // Core required claims (always enforced)
        private val REQUIRED_CLAIMS =
            setOf(
                "sub", // Subject (user ID)
                "iss", // Issuer (Keycloak URL)
                "exp", // Expiration time
                "iat", // Issued at time
            )
    }

    /**
     * Validates that all required claims are present and non-blank in the JWT.
     *
     * Returns a failure result if any required claim is missing or if critical claims
     * (tenant_id, sub) are blank; returns success when all required claims are present
     * and valid.
     *
     * @param token The JWT whose claims will be validated.
     * @return An OAuth2TokenValidatorResult representing validation failure with an `invalid_token` error
     *         listing all missing or invalid claims, or success when all required claims are present and valid.
     */
    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val missingClaims = REQUIRED_CLAIMS.filter { !token.hasClaim(it) }

        if (missingClaims.isNotEmpty()) {
            return OAuth2TokenValidatorResult.failure(
                OAuth2Error(
                    "invalid_token",
                    "JWT missing required claims: ${missingClaims.sorted().joinToString(", ")}",
                    null,
                ),
            )
        }

        // Validate critical claims are non-blank (defense against empty string attacks)
        val invalidClaims = mutableListOf<String>()

        // sub must be non-blank for user identification (always required)
        token.getClaimAsString("sub")?.let { sub ->
            if (sub.isBlank()) {
                invalidClaims.add("sub (blank)")
            }
        }

        // tenant_id must be non-blank if present (required in Epic 4, optional until then)
        if (token.hasClaim("tenant_id")) {
            token.getClaimAsString("tenant_id")?.let { tenantId ->
                if (tenantId.isBlank()) {
                    invalidClaims.add("tenant_id (blank)")
                }
            }
        }

        return if (invalidClaims.isNotEmpty()) {
            OAuth2TokenValidatorResult.failure(
                OAuth2Error(
                    "invalid_token",
                    "JWT has invalid claim values: ${invalidClaims.sorted().joinToString(", ")}",
                    null,
                ),
            )
        } else {
            OAuth2TokenValidatorResult.success()
        }
    }
}
