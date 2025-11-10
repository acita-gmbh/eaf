package com.axians.eaf.framework.security.validation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.springframework.security.oauth2.jwt.Jwt

class JwtAudienceValidatorTest :
    FunSpec({
        val expected = "eaf-api"

        test("audience containing expected value should pass validation") {
            val validator = JwtAudienceValidator(expected)
            val jwt = createJwt().audience(listOf("eaf-api", "eaf-console")).build()

            validator.validate(jwt).hasErrors().shouldBeFalse()
        }

        test("audience expressed as string should be supported") {
            val validator = JwtAudienceValidator(expected)
            val jwt =
                createJwt()
                    .claim("aud", "eaf-api")
                    .build()

            validator.validate(jwt).hasErrors().shouldBeFalse()
        }

        test("authorized party (azp) should be used as fallback for audience") {
            val validator = JwtAudienceValidator(expected)
            val jwt =
                createJwt()
                    .claim("azp", "eaf-api")
                    .build()

            validator.validate(jwt).hasErrors().shouldBeFalse()
        }

        test("missing audience claim should fail") {
            val validator = JwtAudienceValidator(expected)
            val jwt = createJwt().build()

            val result = validator.validate(jwt)

            result.hasErrors().shouldBeTrue()
            result.errors
                .first()
                .description
                .shouldBe("JWT missing audience (aud) claim")
        }

        test("audience missing expected value should fail") {
            val validator = JwtAudienceValidator(expected)
            val jwt = createJwt().audience(listOf("eaf-console")).build()

            val result = validator.validate(jwt)

            result.hasErrors().shouldBeTrue()
            result.errors
                .first()
                .description
                .shouldBe("JWT audience missing expected value: $expected")
        }
    })

private fun createJwt(): Jwt.Builder =
    Jwt
        .withTokenValue("token")
        .header("alg", "RS256")
        .claim("sub", "user")
