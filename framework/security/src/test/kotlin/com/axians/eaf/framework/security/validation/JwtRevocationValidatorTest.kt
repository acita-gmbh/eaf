package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.revocation.TokenRevocationStore
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * Unit tests for JwtRevocationValidator - Layer 7 of 10-layer JWT validation system.
 *
 * Validates JWT revocation status using Redis-backed token blacklist, ensuring revoked tokens
 * (e.g., after logout, password reset, or security breach) cannot be used even if not yet expired.
 * Implements fail-closed behavior when revocation store is unavailable.
 *
 * **Test Coverage:**
 * - Missing jti claim rejection (jti required for revocation tracking)
 * - Revoked token rejection (blacklisted jti = access denied)
 * - Active token acceptance (jti not blacklisted = allow)
 * - Fail-closed behavior (Redis unavailable = reject token)
 * - TestTokenRevocationStore for deterministic testing
 *
 * **Security Patterns:**
 * - Token revocation enforcement (logout, password reset, breach response)
 * - Fail-closed design (unavailable revocation store = reject, not allow)
 * - jti requirement (unique token identifier for blacklisting)
 * - Redis TTL alignment (tokens expire from blacklist when JWT exp reached)
 * - Defense-in-depth (revocation check independent of expiration)
 *
 * **Testing Strategy:**
 * - TestTokenRevocationStore: In-memory nullable implementation
 * - Fail-closed simulation (store throws exception = validation failure)
 * - SimpleMeterRegistry for metrics validation
 * - No Redis dependency (fast unit tests)
 *
 * **Acceptance Criteria:**
 * - Story 3.7: Token revocation validation (jti blacklist check)
 * - Story 3.7: Fail-closed behavior (unavailable store = reject)
 *
 * @see JwtRevocationValidator Primary class under test
 * @see TokenRevocationStore Redis-backed revocation interface
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
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
