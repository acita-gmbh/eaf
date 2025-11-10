package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.revocation.RedisRevocationStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.security.oauth2.jwt.Jwt

class JwtRevocationValidatorTest : FunSpec({
    lateinit var revocationStore: RedisRevocationStore

    beforeTest {
        revocationStore = Mockito.mock(RedisRevocationStore::class.java)
    }

    test("missing jti fails validation") {
        val validator = JwtRevocationValidator(revocationStore)
        val jwt = jwtBuilder(withJti = false).build()

        val result = validator.validate(jwt)

        result.hasErrors().shouldBeTrue()
        result.errors.first().description.shouldBe("JWT missing JTI (jti) claim required for revocation.")
        Mockito.verifyNoInteractions(revocationStore)
    }

    test("revoked token fails validation") {
        Mockito.`when`(revocationStore.isRevoked("dead-beef")).thenReturn(true)
        val validator = JwtRevocationValidator(revocationStore)
        val jwt = jwtBuilder().claim("jti", "dead-beef").build()

        val result = validator.validate(jwt)

        result.hasErrors().shouldBeTrue()
        result.errors.first().description.shouldBe("JWT has been revoked and may not be used.")
    }

    test("active token passes validation") {
        Mockito.`when`(revocationStore.isRevoked("alive"))
            .thenReturn(false)

        val validator = JwtRevocationValidator(revocationStore)
        val jwt = jwtBuilder().claim("jti", "alive").build()

        validator.validate(jwt).hasErrors().shouldBeFalse()
    }

    test("fail-closed propagates as validation failure") {
        Mockito.`when`(revocationStore.isRevoked("locked"))
            .thenThrow(SecurityException("Redis unavailable"))

        val validator = JwtRevocationValidator(revocationStore)
        val jwt = jwtBuilder().claim("jti", "locked").build()

        val result = validator.validate(jwt)

        result.hasErrors().shouldBeTrue()
        result.errors.first().description.shouldBe("Token revocation status unavailable. Please retry later.")
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
