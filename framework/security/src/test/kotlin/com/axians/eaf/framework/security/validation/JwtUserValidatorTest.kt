package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.config.KeycloakOidcConfiguration
import com.axians.eaf.framework.security.user.UserDirectory
import com.axians.eaf.framework.security.user.UserRecord
import com.axians.eaf.framework.security.user.UserValidationException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Unit tests for JwtUserValidator.
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 */
class JwtUserValidatorTest {

    private fun jwt(subject: String? = "user-123"): Jwt = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .apply {
            subject?.let { claim("sub", it) }
            claim("aud", "test-api")
        }.build()

    @Test
    fun `skips validation when feature disabled`() {
        val validator = JwtUserValidator(
            KeycloakOidcConfiguration(validateUser = false),
            AcceptAllDirectory(),
            SimpleMeterRegistry(),
        )

        assertThat(validator.validate(jwt(null)).hasErrors()).isFalse()
    }

    @Test
    fun `fails when subject missing`() {
        val validator = JwtUserValidator(
            KeycloakOidcConfiguration(validateUser = true),
            AcceptAllDirectory(),
            SimpleMeterRegistry(),
        )

        val result = validator.validate(jwt(subject = null))

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description)
            .isEqualTo("JWT missing subject (sub) claim required for user validation.")
    }

    @Test
    fun `fails when user directory reports missing user`() {
        val validator = JwtUserValidator(
            KeycloakOidcConfiguration(validateUser = true),
            MissingUserDirectory(),
            SimpleMeterRegistry(),
        )

        val result = validator.validate(jwt())

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description)
            .isEqualTo("JWT subject user is invalid (Layer 9)")
    }

    @Test
    fun `fails when user inactive`() {
        val validator = JwtUserValidator(
            KeycloakOidcConfiguration(validateUser = true),
            InactiveUserDirectory(),
            SimpleMeterRegistry(),
        )

        val result = validator.validate(jwt())

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description)
            .isEqualTo("JWT subject user is invalid (Layer 9)")
    }

    @Test
    fun `propagates directory failures as validation errors`() {
        val validator = JwtUserValidator(
            KeycloakOidcConfiguration(validateUser = true),
            ThrowingDirectory(),
            SimpleMeterRegistry(),
        )

        val result = validator.validate(jwt())

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description)
            .isEqualTo("Unable to validate JWT subject user at this time. Please retry later.")
    }

    @Test
    fun `passes when user exists and active`() {
        val validator = JwtUserValidator(
            KeycloakOidcConfiguration(validateUser = true),
            AcceptAllDirectory(),
            SimpleMeterRegistry(),
        )

        assertThat(validator.validate(jwt()).hasErrors()).isFalse()
    }
}

private class AcceptAllDirectory : UserDirectory {
    override fun findById(userId: String): UserRecord = UserRecord(userId, active = true)
}

private class MissingUserDirectory : UserDirectory {
    override fun findById(userId: String): UserRecord? = null
}

private class InactiveUserDirectory : UserDirectory {
    override fun findById(userId: String): UserRecord = UserRecord(userId, active = false)
}

private class ThrowingDirectory : UserDirectory {
    override fun findById(userId: String): UserRecord? = throw UserValidationException("boom")
}
