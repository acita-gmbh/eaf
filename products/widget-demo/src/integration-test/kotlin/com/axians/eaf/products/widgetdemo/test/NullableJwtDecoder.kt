package com.axians.eaf.products.widgetdemo.test

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import java.time.Instant

/**
 * Nullable Design Pattern implementation for JwtDecoder.
 * Provides fast infrastructure substitute for JWT decoding in widget-demo testing.
 *
 * Maintains real business logic contracts while eliminating external OIDC dependencies.
 * Follows EAF testing standards: "Zero-Mocks Policy" with Nullable Pattern for infrastructure.
 */
class NullableJwtDecoder : JwtDecoder {
    companion object {
        /**
         * Factory method following Nullable Design Pattern convention.
         * Creates a fast infrastructure substitute for JWT decoding.
         */
        fun createNull(): NullableJwtDecoder = NullableJwtDecoder()


    }

    override fun decode(token: String): Jwt =
        when {
            token.contains("valid-tenant") -> createValidTenantJwt(token, "test-tenant-123")
            token.contains("no-tenant") -> createJwtWithoutTenant(token)
            token.contains("invalid") -> throw JwtException("Invalid JWT token for testing")
            token.contains("expired") -> throw JwtException("JWT token expired for testing")
            else -> createValidTenantJwt(token, "550e8400-e29b-41d4-a716-446655440000")
        }

    private fun createValidTenantJwt(
        token: String,
        tenantId: String,
    ): Jwt {
        val headers = mapOf("alg" to "HS256", "typ" to "JWT")
        val claims =
            mapOf(
                "sub" to "test-user",
                "tenant_id" to tenantId,
                "iss" to "test-issuer",
                "aud" to "test-audience",
                "iat" to Instant.now().epochSecond,
                "exp" to Instant.now().plusSeconds(3600).epochSecond,
            )

        return Jwt(token, Instant.now(), Instant.now().plusSeconds(3600), headers, claims)
    }

    private fun createJwtWithoutTenant(token: String): Jwt {
        val headers = mapOf("alg" to "HS256", "typ" to "JWT")
        val claims =
            mapOf(
                "sub" to "test-user",
                // Missing tenant_id claim for fail-closed testing
                "iss" to "test-issuer",
                "aud" to "test-audience",
                "iat" to Instant.now().epochSecond,
                "exp" to Instant.now().plusSeconds(3600).epochSecond,
            )

        return Jwt(token, Instant.now(), Instant.now().plusSeconds(3600), headers, claims)
    }
}
