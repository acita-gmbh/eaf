package com.axians.eaf.framework.security.validation

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * Unit tests for JwtClaimSchemaValidator - Layer 4 of 10-layer JWT validation system.
 *
 * Validates JWT claim schema enforcement, ensuring required claims (sub, iss, exp, iat, jti)
 * are present and critical claims (sub, tenant_id) are non-blank to prevent identity bypass
 * and multi-tenancy violations.
 *
 * **Test Coverage:**
 * - Required claim presence (sub, iss, exp, iat, jti)
 * - Optional claim handling (tenant_id, roles, aud)
 * - Non-blank validation for critical claims (sub, tenant_id)
 * - Multiple missing claims reporting (sorted alphabetically)
 * - Multiple invalid claims reporting (sorted alphabetically)
 * - Empty list handling (roles: [] is valid)
 * - Blank vs missing distinction (blank is invalid, missing may be valid)
 *
 * **Security Patterns:**
 * - Fail-closed validation (missing required claims = rejection)
 * - Identity bypass prevention (blank 'sub' rejected)
 * - Multi-tenancy bypass prevention (blank 'tenant_id' rejected)
 * - Revocation support (jti required for token blacklisting)
 * - Defense-in-depth (claim schema validated separately from business logic)
 *
 * **Layer Coordination:**
 * - aud: Validated by Layer 6 (JwtAudienceValidator)
 * - tenant_id: Optional until Epic 4 (multi-tenancy), enforced when present
 * - roles: Optional until Epic 3.6+ (role validation layer)
 * - exp/iat: Presence validated here, values validated by Layer 5 (JwtTimeBasedValidator)
 *
 * **Acceptance Criteria:**
 * - Story 3.5: Required claim enforcement (sub, iss, exp, iat, jti)
 * - Story 3.5: Non-blank validation for critical claims
 *
 * @see JwtClaimSchemaValidator Primary class under test
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
 */
class JwtClaimSchemaValidatorTest {
    private val validator = JwtClaimSchemaValidator(SimpleMeterRegistry())

    @Test
    fun `valid JWT with all required claims should pass validation`() {
        val jwt =
            createJwt(
                claims =
                    mapOf(
                        "sub" to "user-123",
                        "iss" to "https://keycloak.example.com/realms/eaf",
                        "aud" to "eaf-api",
                        "exp" to Instant.now().plusSeconds(3600),
                        "iat" to Instant.now(),
                        "tenant_id" to "tenant-a",
                        "roles" to listOf("WIDGET_ADMIN"),
                    ),
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `JWT missing 'sub' claim should fail validation`() {
        val jwt =
            createJwt(
                claims =
                    mapOf(
                        "iss" to "https://keycloak.example.com/realms/eaf",
                        "aud" to "eaf-api",
                        "exp" to Instant.now().plusSeconds(3600),
                        "iat" to Instant.now(),
                        "tenant_id" to "tenant-a",
                        "roles" to listOf("WIDGET_ADMIN"),
                    ),
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description).isEqualTo("JWT missing required claims: sub")
    }

    @Test
    fun `JWT missing optional 'tenant_id' claim should pass validation`() {
        // tenant_id is optional until Epic 4 (multi-tenancy implementation)
        val jwt =
            createJwt(
                claims =
                    mapOf(
                        "sub" to "user-123",
                        "iss" to "https://keycloak.example.com/realms/eaf",
                        "aud" to "eaf-api",
                        "exp" to Instant.now().plusSeconds(3600),
                        "iat" to Instant.now(),
                        // tenant_id intentionally omitted
                    ),
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `JWT missing optional 'roles' claim should pass validation`() {
        // roles is optional until Epic 3.6+ (role validation layer)
        val jwt =
            createJwt(
                claims =
                    mapOf(
                        "sub" to "user-123",
                        "iss" to "https://keycloak.example.com/realms/eaf",
                        "aud" to "eaf-api",
                        "exp" to Instant.now().plusSeconds(3600),
                        "iat" to Instant.now(),
                        // roles intentionally omitted
                    ),
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `JWT missing required claims should list all missing claims in sorted order`() {
        val jwt =
            createJwt(
                claims =
                    mapOf(
                        "sub" to "user-123",
                        "aud" to "eaf-api",
                        "exp" to Instant.now().plusSeconds(3600),
                    ),
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        val description = result.errors.first().description
        // Only 'iat' and 'iss' are missing and enforced as required claims in this test.
        // The next test covers the jti requirement.
        // tenant_id and roles are optional until Epic 4 and Epic 3.6+
        assertThat(description).isEqualTo("JWT missing required claims: iat, iss")
    }

    @Test
    fun `JWT missing jti claim should fail validation`() {
        val jwt =
            createJwt(
                claims =
                    mapOf(
                        "sub" to "user-123",
                        "iss" to "https://keycloak.example.com/realms/eaf",
                        "aud" to "eaf-api",
                        "exp" to Instant.now().plusSeconds(3600),
                        "iat" to Instant.now(),
                    ),
                includeJti = false,
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description).isEqualTo("JWT missing required claims: jti")
    }

    @Test
    fun `JWT with blank tenant_id should fail validation`() {
        val jwt =
            createJwt(
                claims =
                    mapOf(
                        "sub" to "user-123",
                        "iss" to "https://keycloak.example.com/realms/eaf",
                        "aud" to "eaf-api",
                        "exp" to Instant.now().plusSeconds(3600),
                        "iat" to Instant.now(),
                        "tenant_id" to "", // Blank tenant_id (multi-tenancy bypass attempt)
                        "roles" to listOf("WIDGET_ADMIN"),
                    ),
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description).isEqualTo("JWT has invalid claim values: tenant_id (blank)")
    }

    @Test
    fun `JWT with blank sub should fail validation`() {
        val jwt =
            createJwt(
                claims =
                    mapOf(
                        "sub" to "", // Blank subject (user identity bypass attempt)
                        "iss" to "https://keycloak.example.com/realms/eaf",
                        "aud" to "eaf-api",
                        "exp" to Instant.now().plusSeconds(3600),
                        "iat" to Instant.now(),
                        "tenant_id" to "tenant-a",
                        "roles" to listOf("WIDGET_ADMIN"),
                    ),
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description).isEqualTo("JWT has invalid claim values: sub (blank)")
    }

    @Test
    fun `JWT with both blank tenant_id and sub should list both invalid claims`() {
        val jwt =
            createJwt(
                claims =
                    mapOf(
                        "sub" to "",
                        "iss" to "https://keycloak.example.com/realms/eaf",
                        "aud" to "eaf-api",
                        "exp" to Instant.now().plusSeconds(3600),
                        "iat" to Instant.now(),
                        "tenant_id" to "",
                        "roles" to listOf("WIDGET_ADMIN"),
                    ),
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description)
            .isEqualTo("JWT has invalid claim values: sub (blank), tenant_id (blank)")
    }

    @Test
    fun `JWT with non-critical empty claims should pass validation`() {
        val jwt =
            createJwt(
                claims =
                    mapOf(
                        "sub" to "user-123",
                        "iss" to "", // Non-critical claim (validated separately by issuer validator)
                        "aud" to "", // Non-critical claim (validated separately by audience validator)
                        "exp" to Instant.now().plusSeconds(3600),
                        "iat" to Instant.now(),
                        "tenant_id" to "tenant-a",
                        "roles" to emptyList<String>(), // Empty list is valid (no-roles scenario)
                    ),
            )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }
}

/**
 * Helper function to create a test JWT with specified claims.
 */
private fun createJwt(
    claims: Map<String, Any>,
    includeJti: Boolean = true,
): Jwt {
    val headers = mapOf("alg" to "RS256", "typ" to "JWT")
    val tokenValue = "mock-token-value"

    val exp = claims["exp"] as? Instant
    val iat = claims["iat"] as? Instant

    return Jwt
        .withTokenValue(tokenValue)
        .headers { h -> h.putAll(headers) }
        .claims { c ->
            c.putAll(claims)
            if (includeJti) {
                c.putIfAbsent("jti", "test-jti")
            }
        }.apply {
            exp?.let { expiresAt(it) }
            iat?.let { issuedAt(it) }
        }.build()
}
