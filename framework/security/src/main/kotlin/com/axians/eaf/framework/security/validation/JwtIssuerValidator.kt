package com.axians.eaf.framework.security.validation

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/**
 * Layer 6 validator – ensures JWT issuer (iss) matches the trusted Keycloak realm.
 *
 * Story 3.9: Added per-layer metrics instrumentation
 */
@Component
class JwtIssuerValidator(
    @Value("\${eaf.security.keycloak.issuer-uri}")
    private val expectedIssuer: String,
    meterRegistry: MeterRegistry,
) : MeteredTokenValidator("layer6_issuer", meterRegistry) {
    override fun doValidate(token: Jwt): OAuth2TokenValidatorResult {
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
