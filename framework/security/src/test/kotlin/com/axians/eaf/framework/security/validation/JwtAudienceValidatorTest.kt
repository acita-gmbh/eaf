package com.axians.eaf.framework.security.validation

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Unit tests for JwtAudienceValidator - Layer 6 of 10-layer JWT validation system.
 *
 * Validates JWT audience (aud) claim enforcement to ensure tokens are intended for this
 * application and prevent token replay attacks from other services in the same Keycloak realm.
 *
 * **Test Coverage:**
 * - Audience array containing expected value (multi-audience support)
 * - Audience as single string (aud: "eaf-api")
 * - Authorized party (azp) fallback when aud missing
 * - Missing audience claim rejection
 * - Audience missing expected value rejection
 * - Multi-audience token validation (token for multiple services)
 *
 * **Security Patterns:**
 * - Token replay attack prevention (tokens for other services rejected)
 * - Audience whitelist enforcement (only "eaf-api" accepted)
 * - Fail-closed validation (missing/invalid audience = rejection)
 * - Authorized party (azp) fallback (OIDC compliance)
 * - Generic error messages (CWE-209 protection)
 *
 * **Testing Strategy:**
 * - Nullable Pattern: No Spring context, fast unit tests
 * - SimpleMeterRegistry for metrics validation
 * - Comprehensive audience mismatch scenarios
 * - OIDC spec compliance (aud as string or array, azp fallback)
 *
 * **Acceptance Criteria:**
 * - Story 3.5: Audience claim validation (aud contains "eaf-api")
 * - Token replay attack prevention (foreign audience rejected)
 *
 * @see JwtAudienceValidator Primary class under test
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
 */
class JwtAudienceValidatorTest {

    private val expected = "eaf-api"

    @Test
    fun `audience containing expected value should pass validation`() {
        val validator = JwtAudienceValidator(expected, SimpleMeterRegistry())
        val jwt = createJwt().audience(listOf("eaf-api", "eaf-console")).build()

        assertThat(validator.validate(jwt).hasErrors()).isFalse()
    }

    @Test
    fun `audience expressed as string should be supported`() {
        val validator = JwtAudienceValidator(expected, SimpleMeterRegistry())
        val jwt = createJwt()
            .claim("aud", "eaf-api")
            .build()

        assertThat(validator.validate(jwt).hasErrors()).isFalse()
    }

    @Test
    fun `authorized party (azp) should be used as fallback for audience`() {
        val validator = JwtAudienceValidator(expected, SimpleMeterRegistry())
        val jwt = createJwt()
            .claim("azp", "eaf-api")
            .build()

        assertThat(validator.validate(jwt).hasErrors()).isFalse()
    }

    @Test
    fun `missing audience claim should fail`() {
        val validator = JwtAudienceValidator(expected, SimpleMeterRegistry())
        val jwt = createJwt().build()

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description).isEqualTo("JWT missing audience (aud) claim")
    }

    @Test
    fun `audience missing expected value should fail`() {
        val validator = JwtAudienceValidator(expected, SimpleMeterRegistry())
        val jwt = createJwt().audience(listOf("eaf-console")).build()

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description).isEqualTo("JWT audience missing expected value: $expected")
    }
}

private fun createJwt(): Jwt.Builder = Jwt.withTokenValue("token")
    .header("alg", "RS256")
    .claim("sub", "user")
    .claim("jti", "aud-test-jti")
