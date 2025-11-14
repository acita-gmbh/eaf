package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.revocation.TokenRevocationStore
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/**
 * Layer 7 validator that rejects JWTs flagged as revoked in Redis.
 *
 * Story 3.9: Added per-layer metrics instrumentation
 */
@Component
@Profile("!test")
class JwtRevocationValidator(
    private val revocationStore: TokenRevocationStore,
    meterRegistry: MeterRegistry,
) : MeteredTokenValidator("layer7_revocation", meterRegistry) {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun doValidate(token: Jwt): OAuth2TokenValidatorResult {
        val jti =
            token.id?.takeIf { it.isNotBlank() }
                ?: return OAuth2TokenValidatorResult.failure(MISSING_JTI_ERROR)

        return try {
            val revoked = revocationStore.isRevoked(jti)
            if (revoked) {
                OAuth2TokenValidatorResult.failure(REVOKED_ERROR)
            } else {
                OAuth2TokenValidatorResult.success()
            }
        } catch (ex: SecurityException) {
            logger.warn("Token revocation status unavailable", ex)
            OAuth2TokenValidatorResult.failure(REVOCATION_UNAVAILABLE_ERROR)
        }
    }

    companion object {
        private val MISSING_JTI_ERROR =
            OAuth2Error(
                "invalid_token",
                "JWT missing JTI (jti) claim required for revocation.",
                null,
            )

        private val REVOKED_ERROR =
            OAuth2Error(
                "invalid_token",
                "JWT has been revoked and may not be used.",
                null,
            )

        private val REVOCATION_UNAVAILABLE_ERROR =
            OAuth2Error(
                "invalid_token",
                "Token revocation status unavailable. Please retry later.",
                null,
            )
    }
}
