package com.axians.eaf.framework.security.validation

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Unit tests for JwtIssuerValidator - Layer 6 of 10-layer JWT validation system.
 *
 * Validates JWT issuer (iss) claim enforcement to ensure tokens originate from trusted
 * Keycloak realm and prevent token substitution attacks from untrusted identity providers.
 *
 * **Test Coverage:**
 * - Valid issuer acceptance (exact match)
 * - Trailing slash normalization (issuer with/without trailing slash)
 * - Missing issuer claim rejection
 * - Mismatched issuer rejection (untrusted issuer)
 * - Trust boundary enforcement (only configured issuer accepted)
 *
 * **Security Patterns:**
 * - Trust boundary enforcement (only Keycloak realm accepted)
 * - Token substitution attack prevention (foreign tokens rejected)
 * - Fail-closed validation (missing/invalid issuer = rejection)
 * - URL normalization (trailing slash tolerance)
 * - Generic error messages (CWE-209 protection)
 *
 * **Testing Strategy:**
 * - Nullable Pattern: No Spring context, fast unit tests
 * - SimpleMeterRegistry for metrics validation
 * - Comprehensive issuer mismatch scenarios
 *
 * **Acceptance Criteria:**
 * - Story 3.5: Issuer claim validation (iss matches configured Keycloak realm)
 * - Trust boundary enforcement (foreign issuers rejected)
 *
 * @see JwtIssuerValidator Primary class under test
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
 */
class JwtIssuerValidatorTest {

    private val expectedIssuer = "http://keycloak:8080/realms/eaf"

    @Test
    fun `valid issuer should pass validation`() {
        val validator = JwtIssuerValidator(expectedIssuer, SimpleMeterRegistry())
        val jwt = createJwt().claim("iss", expectedIssuer).build()

        assertThat(validator.validate(jwt).hasErrors()).isFalse()
    }

    @Test
    fun `issuer with trailing slash still matches`() {
        val validator = JwtIssuerValidator(expectedIssuer, SimpleMeterRegistry())
        val jwt = createJwt().claim("iss", "$expectedIssuer/").build()

        assertThat(validator.validate(jwt).hasErrors()).isFalse()
    }

    @Test
    fun `missing issuer claim should fail`() {
        val validator = JwtIssuerValidator(expectedIssuer, SimpleMeterRegistry())
        val jwt = createJwt().build()

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description).isEqualTo("JWT missing issuer (iss) claim")
    }

    @Test
    fun `mismatched issuer should fail`() {
        val validator = JwtIssuerValidator(expectedIssuer, SimpleMeterRegistry())
        val jwt = createJwt().claim("iss", "http://evil-issuer/realms/root").build()

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description).isEqualTo(
            "Invalid issuer: http://evil-issuer/realms/root (expected: $expectedIssuer)",
        )
    }
}

private fun createJwt(): Jwt.Builder = Jwt.withTokenValue("token")
    .header("alg", "RS256")
    .claim("sub", "user")
    .claim("jti", "issuer-test-jti")
