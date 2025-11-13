package com.axians.eaf.framework.security.validation

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/**
 * Layer 6 validator – ensures JWT audience includes the expected API identifier.
 *
 * Story 3.9: Added per-layer metrics instrumentation
 */
@Component
class JwtAudienceValidator(
    @Value("\${eaf.security.keycloak.audience}")
    private val expectedAudience: String,
    meterRegistry: MeterRegistry,
) : MeteredTokenValidator("layer6_audience", meterRegistry) {
    override fun doValidate(token: Jwt): OAuth2TokenValidatorResult {
        val audiences = resolveAudiences(token)
        val errorMessage =
            when {
                audiences.isEmpty() -> "JWT missing audience (aud) claim"
                !audiences.contains(expectedAudience) -> "JWT audience missing expected value: $expectedAudience"
                else -> null
            }

        return errorMessage?.let { failure(it) } ?: OAuth2TokenValidatorResult.success()
    }

    private fun failure(message: String): OAuth2TokenValidatorResult =
        OAuth2TokenValidatorResult.failure(
            OAuth2Error("invalid_token", message, null),
        )
}

private fun resolveAudiences(token: Jwt): List<String> {
    val propertyAudiences = token.audience?.takeIf { it.isNotEmpty() }

    val claimAudiences =
        if (propertyAudiences == null) {
            val rawAudClaim = token.claims["aud"]
            when (rawAudClaim) {
                is String -> listOf(rawAudClaim)
                is Collection<*> -> rawAudClaim.mapNotNull { it?.toString() }
                else -> emptyList()
            }.takeIf { it.isNotEmpty() }
        } else {
            null
        }

    val authorizedParty = token.getClaimAsString("azp")?.let { listOf(it) }

    return propertyAudiences
        ?: claimAudiences
        ?: authorizedParty.orEmpty()
}
