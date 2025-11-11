package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.revocation.TokenRevocationStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.springframework.security.oauth2.jwt.Jwt

class JwtRevocationValidatorTest :
    FunSpec({
        fun validator(
            store: TestTokenRevocationStore = TestTokenRevocationStore(),
        ): Pair<JwtRevocationValidator, TestTokenRevocationStore> = JwtRevocationValidator(store) to store

        test("missing jti fails validation") {
            val (validator, store) = validator()
            val jwt = jwtBuilder(withJti = false).build()

            val result = validator.validate(jwt)

            result.hasErrors().shouldBeTrue()
            result.errors
                .first()
                .description
                .shouldBe("JWT missing JTI (jti) claim required for revocation.")
            store.queries.shouldBe(emptyList())
        }

        test("revoked token fails validation") {
            val store = TestTokenRevocationStore().apply { revoked += "dead-beef" }
            val validator = JwtRevocationValidator(store)
            val jwt = jwtBuilder().claim("jti", "dead-beef").build()

            val result = validator.validate(jwt)

            result.hasErrors().shouldBeTrue()
            result.errors
                .first()
                .description
                .shouldBe("JWT has been revoked and may not be used.")
        }

        test("active token passes validation") {
            val validator = JwtRevocationValidator(TestTokenRevocationStore())
            val jwt = jwtBuilder().claim("jti", "alive").build()

            validator.validate(jwt).hasErrors().shouldBeFalse()
        }

        test("fail-closed propagates as validation failure") {
            val store = TestTokenRevocationStore().apply { throwFor = "locked" }
            val validator = JwtRevocationValidator(store)
            val jwt = jwtBuilder().claim("jti", "locked").build()

            val result = validator.validate(jwt)

            result.hasErrors().shouldBeTrue()
            result.errors
                .first()
                .description
                .shouldBe("Token revocation status unavailable. Please retry later.")
        }
    })

private fun jwtBuilder(withJti: Boolean = true): Jwt.Builder {
    val builder =
        Jwt
            .withTokenValue("token")
            .header("alg", "RS256")
            .claim("sub", "user")

    return if (withJti) {
        builder.claim("jti", "jti-default")
    } else {
        builder
    }
}

private class TestTokenRevocationStore : TokenRevocationStore {
    val revoked = mutableSetOf<String>()
    val queries = mutableListOf<String>()
    var throwFor: String? = null

    override fun isRevoked(jti: String): Boolean {
        queries += jti
        if (throwFor == jti) {
            throw SecurityException("Redis unavailable")
        }
        return revoked.contains(jti)
    }

    override fun revoke(
        jti: String,
        expiresAt: java.time.Instant?,
    ) {
        revoked += jti
    }
}
