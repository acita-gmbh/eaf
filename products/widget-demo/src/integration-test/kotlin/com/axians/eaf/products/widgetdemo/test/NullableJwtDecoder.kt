package com.axians.eaf.products.widgetdemo.test

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import java.time.Instant
import java.util.UUID

/**
 * Nullable Design Pattern implementation for JwtDecoder.
 * Provides fast infrastructure substitute for JWT decoding in widget-demo testing.
 *
 * Maintains real business logic contracts while eliminating external OIDC dependencies.
 * Follows EAF testing standards: "Zero-Mocks Policy" with Nullable Pattern for infrastructure.
 */
class NullableJwtDecoder private constructor(
    private val defaultClaims: Map<String, Any?>,
    private val defaultRoles: List<String>,
) : JwtDecoder {
    companion object {
        private const val DEFAULT_TENANT_ID = "550e8400-e29b-41d4-a716-446655440000"
        private val DEFAULT_ROLES = listOf("USER", "widget:create", "widget:read")
        private const val VALID_TOKEN =
            "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDEiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgxODAvcmVhbG1zL2VhZiIsImF1ZCI6WyJlYWYtYmFja2VuZCJdLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MTcwMDAwMzYwMCwianRpIjoiMDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMDk5IiwidGVuYW50X2lkIjoiNTUwZTg0MDAtZTI5Yi00MWQ0LWE3MTYtNDQ2NjU1NDQwMDAwIiwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIlVTRVIiLCJ3aWRnZXQ6Y3JlYXRlIiwid2lkZ2V0OnJlYWQiXX0sInNlc3Npb25fc3RhdGUiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDA3NzcifQ.c2lnbmF0dXJl"
        private const val NO_TENANT_TOKEN =
            "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDIiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgxODAvcmVhbG1zL2VhZiIsImF1ZCI6WyJlYWYtYmFja2VuZCJdLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MTcwMDAwMzYwMCwianRpIjoiMDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMDk5IiwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIlVTRVIiLCJ3aWRnZXQ6Y3JlYXRlIiwid2lkZ2V0OnJlYWQiXX0sInNlc3Npb25fc3RhdGUiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDA3NzcifQ.c2lnbmF0dXJl"
        private const val INVALID_TOKEN = "invalid.token.signature"

        /**
         * Factory method following Nullable Design Pattern convention.
         * Creates a fast infrastructure substitute for JWT decoding while
         * preserving the production contract enforced by TenLayerJwtValidator.
         */
        fun createNull(
            defaultClaims: Map<String, Any?> = emptyMap(),
            defaultRoles: List<String> = DEFAULT_ROLES,
        ): NullableJwtDecoder = NullableJwtDecoder(defaultClaims, defaultRoles)

        val validTokenValue: String = VALID_TOKEN
        val invalidTokenValue: String = INVALID_TOKEN
        val noTenantTokenValue: String = NO_TENANT_TOKEN
    }

    override fun decode(token: String): Jwt =
        when (token) {
            INVALID_TOKEN -> buildJwt(token = token, roles = emptyList())
            NO_TENANT_TOKEN -> buildJwt(token = token, tenantId = "")
            VALID_TOKEN -> buildJwt(token = token)
            else -> buildJwt(token = token)
        }

    private fun buildJwt(
        token: String,
        tenantId: String? = null,
        roles: List<String>? = null,
        issuedAt: Instant = Instant.now(),
        expiresAt: Instant = Instant.now().plusSeconds(3600),
    ): Jwt {
        val resolvedRoles = roles ?: defaultRoles
        val resolvedTenant = tenantId ?: defaultClaims["tenant_id"] as? String ?: DEFAULT_TENANT_ID
        val resolvedSubject = defaultClaims["sub"] as? String ?: UUID.randomUUID().toString()
        val resolvedIssuer = defaultClaims["iss"] as? String ?: "http://localhost:8180/realms/eaf-test"
        val resolvedAudience = defaultClaims["aud"] ?: "account"
        val resolvedJti = defaultClaims["jti"] as? String ?: UUID.randomUUID().toString()
        val resolvedSession = defaultClaims["session_state"] as? String ?: UUID.randomUUID().toString()
        val headers =
            mapOf(
                "alg" to "RS256",
                "typ" to "JWT",
                "kid" to (defaultClaims["kid"] ?: "nullable-test"),
            )

        val claims =
            mutableMapOf<String, Any>(
                "sub" to resolvedSubject,
                "tenant_id" to resolvedTenant,
                "iss" to resolvedIssuer,
                "aud" to listOf(resolvedAudience),
                "iat" to issuedAt,
                "exp" to expiresAt,
                "jti" to resolvedJti,
                "session_state" to resolvedSession,
                "realm_access" to mapOf("roles" to resolvedRoles),
                "realm_access.roles" to resolvedRoles,
                "roles" to resolvedRoles,
            )

        defaultClaims
            .filterKeys { it !in setOf("tenant_id", "iss", "aud", "sub", "jti", "session_state", "realm_access.roles") }
            .forEach { (key, value) ->
                if (value != null) {
                    claims.putIfAbsent(key, value)
                }
            }

        return Jwt
            .withTokenValue(token.ifBlank { "nullable-test-token" })
            .headers { it.putAll(headers) }
            .claims { it.putAll(claims) }
            .build()
    }
}
