package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.config.KeycloakOidcConfiguration
import com.axians.eaf.framework.security.user.UserDirectory
import com.axians.eaf.framework.security.user.UserRecord
import com.axians.eaf.framework.security.user.UserValidationException
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.security.oauth2.jwt.Jwt

class JwtUserValidatorTest :
    FunSpec({
        fun jwt(subject: String? = "user-123"): Jwt =
            Jwt
                .withTokenValue("token")
                .header("alg", "RS256")
                .apply {
                    subject?.let { claim("sub", it) }
                    claim("aud", "test-api")
                }.build()

        test("skips validation when feature disabled") {
            val validator =
                JwtUserValidator(
                    KeycloakOidcConfiguration(validateUser = false),
                    AcceptAllDirectory(),
                    SimpleMeterRegistry(),
                )

            validator.validate(jwt(null)).hasErrors().shouldBeFalse()
        }

        test("fails when subject missing") {
            val validator =
                JwtUserValidator(
                    KeycloakOidcConfiguration(validateUser = true),
                    AcceptAllDirectory(),
                    SimpleMeterRegistry(),
                )

            val result = validator.validate(jwt(subject = null))

            result.hasErrors().shouldBeTrue()
            result.errors
                .first()
                .description
                .shouldBe("JWT missing subject (sub) claim required for user validation.")
        }

        test("fails when user directory reports missing user") {
            val validator =
                JwtUserValidator(
                    KeycloakOidcConfiguration(validateUser = true),
                    MissingUserDirectory(),
                    SimpleMeterRegistry(),
                )

            val result = validator.validate(jwt())

            result.hasErrors().shouldBeTrue()
            result.errors
                .first()
                .description
                .shouldBe("JWT subject user is invalid (Layer 9)")
        }

        test("fails when user inactive") {
            val validator =
                JwtUserValidator(
                    KeycloakOidcConfiguration(validateUser = true),
                    InactiveUserDirectory(),
                    SimpleMeterRegistry(),
                )

            val result = validator.validate(jwt())

            result.hasErrors().shouldBeTrue()
            result.errors
                .first()
                .description
                .shouldBe("JWT subject user is invalid (Layer 9)")
        }

        test("propagates directory failures as validation errors") {
            val validator =
                JwtUserValidator(
                    KeycloakOidcConfiguration(validateUser = true),
                    ThrowingDirectory(),
                    SimpleMeterRegistry(),
                )

            val result = validator.validate(jwt())

            result.hasErrors().shouldBeTrue()
            result.errors
                .first()
                .description
                .shouldBe("Unable to validate JWT subject user at this time. Please retry later.")
        }

        test("passes when user exists and active") {
            val validator =
                JwtUserValidator(
                    KeycloakOidcConfiguration(validateUser = true),
                    AcceptAllDirectory(),
                    SimpleMeterRegistry(),
                )

            validator.validate(jwt()).hasErrors().shouldBeFalse()
        }
    })

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
