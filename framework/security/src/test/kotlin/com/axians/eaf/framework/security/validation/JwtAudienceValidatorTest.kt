package com.axians.eaf.framework.security.validation

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Unit tests for JwtAudienceValidator.
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
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
