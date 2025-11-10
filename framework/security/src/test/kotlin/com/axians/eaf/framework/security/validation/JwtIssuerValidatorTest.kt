package com.axians.eaf.framework.security.validation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.springframework.security.oauth2.jwt.Jwt

class JwtIssuerValidatorTest :
    FunSpec({
        val expectedIssuer = "http://keycloak:8080/realms/eaf"

        test("valid issuer should pass validation") {
            val validator = JwtIssuerValidator(expectedIssuer)
            val jwt = createJwt().claim("iss", expectedIssuer).build()

            validator.validate(jwt).hasErrors().shouldBeFalse()
        }

        test("issuer with trailing slash still matches") {
            val validator = JwtIssuerValidator(expectedIssuer)
            val jwt = createJwt().claim("iss", "$expectedIssuer/").build()

            validator.validate(jwt).hasErrors().shouldBeFalse()
        }

        test("missing issuer claim should fail") {
            val validator = JwtIssuerValidator(expectedIssuer)
            val jwt = createJwt().build()

            val result = validator.validate(jwt)

            result.hasErrors().shouldBeTrue()
            result.errors
                .first()
                .description
                .shouldBe("JWT missing issuer (iss) claim")
        }

        test("mismatched issuer should fail") {
            val validator = JwtIssuerValidator(expectedIssuer)
            val jwt = createJwt().claim("iss", "http://evil-issuer/realms/root").build()

            val result = validator.validate(jwt)

            result.hasErrors().shouldBeTrue()
            result.errors.first().description.shouldBe(
                "Invalid issuer: http://evil-issuer/realms/root (expected: $expectedIssuer)",
            )
        }
    })

private fun createJwt(): Jwt.Builder =
    Jwt
        .withTokenValue("token")
        .header("alg", "RS256")
        .claim("sub", "user")
