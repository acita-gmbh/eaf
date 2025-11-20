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
 * Unit tests for JwtUserValidator - Layer 9 of 10-layer JWT validation system.
 *
 * Validates JWT subject (sub) claim against Keycloak user directory, ensuring the user exists
 * and is active. This layer prevents deleted/disabled user accounts from accessing the system
 * even with valid, non-expired tokens. Configurable (can be disabled for performance).
 *
 * **Test Coverage:**
 * - Feature toggle (validateUser=false skips validation)
 * - Missing subject claim rejection (sub required when validation enabled)
 * - User existence validation (UserDirectory lookup)
 * - Active status validation (inactive users rejected)
 * - UserDirectory error handling (fail-closed on directory unavailable)
 * - Test user directory implementations (Accept, Missing, Inactive, Throwing)
 *
 * **Security Patterns:**
 * - Deleted user protection (user removed from Keycloak = token invalid)
 * - Disabled user enforcement (inactive accounts cannot authenticate)
 * - Fail-closed validation (UserDirectory unavailable = reject)
 * - Generic error messages (CWE-209 protection)
 * - Optional validation (configurable for performance vs security tradeoff)
 *
 * **Testing Strategy:**
 * - Test UserDirectory implementations (nullable pattern)
 * - Feature toggle testing (validateUser true/false)
 * - Error propagation testing (exceptions become validation failures)
 * - SimpleMeterRegistry for metrics validation
 *
 * **Acceptance Criteria:**
 * - Story 3.8: User validation (sub matches active Keycloak user)
 * - Story 3.8: Configurable validation (validateUser feature toggle)
 * - Story 3.8: Fail-closed behavior (directory unavailable = reject)
 *
 * @see JwtUserValidator Primary class under test
 * @see UserDirectory Keycloak user lookup interface
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
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
