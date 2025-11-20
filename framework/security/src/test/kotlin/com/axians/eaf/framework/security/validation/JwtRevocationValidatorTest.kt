package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.revocation.TokenRevocationStore
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * Unit tests for JwtRevocationValidator.
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 */
class JwtRevocationValidatorTest {

    private fun validator(
        store: TestTokenRevocationStore = TestTokenRevocationStore(),
    ): Pair<JwtRevocationValidator, TestTokenRevocationStore> =
        JwtRevocationValidator(store, SimpleMeterRegistry()) to store

    @Test
    fun `missing jti fails validation`() {
        val (validator, store) = validator()
        val jwt = jwtBuilder(withJti = false).build()

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description)
            .isEqualTo("JWT missing JTI (jti) claim required for revocation.")
        assertThat(store.queries).isEmpty()
    }

    @Test
    fun `revoked token fails validation`() {
        val store = TestTokenRevocationStore().apply { revoked += "dead-beef" }
        val validator = JwtRevocationValidator(store, SimpleMeterRegistry())
        val jwt = jwtBuilder().claim("jti", "dead-beef").build()

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description)
            .isEqualTo("JWT has been revoked and may not be used.")
    }

    @Test
    fun `active token passes validation`() {
        val validator = JwtRevocationValidator(TestTokenRevocationStore(), SimpleMeterRegistry())
        val jwt = jwtBuilder().claim("jti", "alive").build()

        assertThat(validator.validate(jwt).hasErrors()).isFalse()
    }

    @Test
    fun `fail-closed propagates as validation failure`() {
        val store = TestTokenRevocationStore().apply { throwFor = "locked" }
        val validator = JwtRevocationValidator(store, SimpleMeterRegistry())
        val jwt = jwtBuilder().claim("jti", "locked").build()

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().description)
            .isEqualTo("Token revocation status unavailable. Please retry later.")
    }
}

private fun jwtBuilder(withJti: Boolean = true): Jwt.Builder {
    val builder = Jwt.withTokenValue("token")
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

    override fun revoke(jti: String, expiresAt: Instant?) {
        revoked += jti
    }
}
