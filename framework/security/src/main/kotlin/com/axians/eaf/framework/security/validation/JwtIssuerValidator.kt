package com.axians.eaf.framework.security.validation

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Layer 6 validator – ensures JWT issuer (iss) matches the trusted Keycloak realm.
 */
class JwtIssuerValidator(
    private val expectedIssuer: String,
) : OAuth2TokenValidator<Jwt> {
    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val issuerClaim = token.issuer?.toString() ?: token.getClaimAsString("iss")
        val errorMessage =
            when {
                issuerClaim.isNullOrBlank() -> "JWT missing issuer (iss) claim"
                issuerClaim.trimEnd('/') != expectedIssuer.trimEnd('/') ->
                    "Invalid issuer: $issuerClaim (expected: $expectedIssuer)"
                else -> null
            }

        return errorMessage?.let { failure(it) } ?: OAuth2TokenValidatorResult.success()
    }

    private fun failure(message: String): OAuth2TokenValidatorResult =
        OAuth2TokenValidatorResult.failure(
            OAuth2Error("invalid_token", message, null),
        )
}
