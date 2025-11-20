package com.axians.eaf.framework.security.revocation

import com.axians.eaf.framework.security.config.RevocationProperties
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit tests for RedisRevocationStore.
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 */
class RedisRevocationStoreTest {

    private val meterRegistry = SimpleMeterRegistry()
    private val fixedClock = Clock.fixed(Instant.parse("2025-11-10T00:00:00Z"), ZoneOffset.UTC)

    private fun store(
        properties: RevocationProperties = RevocationProperties(),
        accessor: TestRedisAccessor = TestRedisAccessor(),
    ): Pair<RedisRevocationStore, TestRedisAccessor> = RedisRevocationStore(
        redisTemplate = StringRedisTemplate(),
        properties = properties,
        meterRegistry = meterRegistry,
        clock = fixedClock,
        redisAccessor = accessor,
    ) to accessor

    @Test
    fun `isRevoked returns true when Redis contains key`() {
        val (store, accessor) = store()
        accessor.put("jwt:revoked:jti-123")

        assertThat(store.isRevoked("jti-123")).isTrue()
    }

    @Test
    fun `isRevoked returns false on cache miss`() {
        val (store, _) = store()

        assertThat(store.isRevoked("jti-123")).isFalse()
    }

    @Test
    fun `fail-open returns false when Redis unavailable`() {
        val (store, accessor) = store()
        accessor.throwOnHasKey = true

        assertThat(store.isRevoked("jti-123")).isFalse()
    }

    @Test
    fun `fail-closed throws SecurityException when Redis unavailable`() {
        val (store, accessor) = store(RevocationProperties(failClosed = true))
        accessor.throwOnHasKey = true

        assertThrows<SecurityException> {
            store.isRevoked("jti-123")
        }
    }

    @Test
    fun `revoke stores entry with TTL derived from expiration or default`() {
        val accessor = TestRedisAccessor()
        val store = store(accessor = accessor).first
        val expiration = Instant.parse("2025-11-10T00:15:00Z")

        store.revoke("jti-123", expiration)

        assertThat(accessor.lastSetTtl("jwt:revoked:jti-123")).isEqualTo(Duration.ofMinutes(15))
    }

    @Test
    fun `revoke falls back to default TTL when expiration missing or shorter`() {
        val accessor = TestRedisAccessor()
        val store = store(accessor = accessor).first

        store.revoke("jti-999", null)

        assertThat(accessor.lastSetTtl("jwt:revoked:jti-999")).isEqualTo(Duration.ofMinutes(10))
    }
}

private class TestRedisAccessor : RedisRevocationStore.RevocationRedisAccessor {
    private val entries = mutableMapOf<String, Duration>()
    var throwOnHasKey: Boolean = false

    override fun hasKey(key: String): Boolean {
        if (throwOnHasKey) {
            throw RedisConnectionFailureException("Redis unavailable")
        }
        return entries.containsKey(key)
    }

    override fun setValue(key: String, ttl: Duration) {
        entries[key] = ttl
    }

    fun put(key: String) {
        entries[key] = Duration.ZERO
    }

    fun lastSetTtl(key: String): Duration? = entries[key]
}
