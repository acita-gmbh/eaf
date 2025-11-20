package com.axians.eaf.framework.security.validation

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Unit tests for JwtIssuerValidator.
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
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
