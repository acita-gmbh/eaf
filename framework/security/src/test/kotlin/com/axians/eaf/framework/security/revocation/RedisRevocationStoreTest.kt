package com.axians.eaf.framework.security.revocation

import com.axians.eaf.framework.security.config.RevocationProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class RedisRevocationStoreTest :
    FunSpec({
        val meterRegistry = SimpleMeterRegistry()
        val fixedClock = Clock.fixed(Instant.parse("2025-11-10T00:00:00Z"), ZoneOffset.UTC)

        fun store(
            properties: RevocationProperties = RevocationProperties(),
            accessor: TestRedisAccessor = TestRedisAccessor(),
        ): Pair<RedisRevocationStore, TestRedisAccessor> =
            RedisRevocationStore(
                redisTemplate = StringRedisTemplate(),
                properties = properties,
                meterRegistry = meterRegistry,
                clock = fixedClock,
                redisAccessor = accessor,
            ) to accessor

        test("isRevoked returns true when Redis contains key") {
            val (store, accessor) = store()
            accessor.put("jwt:revoked:jti-123")

            store.isRevoked("jti-123").shouldBeTrue()
        }

        test("isRevoked returns false on cache miss") {
            val (store, _) = store()

            store.isRevoked("jti-123").shouldBeFalse()
        }

        test("fail-open returns false when Redis unavailable") {
            val (store, accessor) = store()
            accessor.throwOnHasKey = true

            store.isRevoked("jti-123").shouldBeFalse()
        }

        test("fail-closed throws SecurityException when Redis unavailable") {
            val (store, accessor) = store(RevocationProperties(failClosed = true))
            accessor.throwOnHasKey = true

            shouldThrow<SecurityException> {
                store.isRevoked("jti-123")
            }
        }

        test("revoke stores entry with TTL derived from expiration or default") {
            val accessor = TestRedisAccessor()
            val store = store(accessor = accessor).first
            val expiration = Instant.parse("2025-11-10T00:15:00Z")

            store.revoke("jti-123", expiration)

            accessor.lastSetTtl("jwt:revoked:jti-123").shouldBe(Duration.ofMinutes(15))
        }

        test("revoke falls back to default TTL when expiration missing or shorter") {
            val accessor = TestRedisAccessor()
            val store = store(accessor = accessor).first

            store.revoke("jti-999", null)

            accessor.lastSetTtl("jwt:revoked:jti-999").shouldBe(Duration.ofMinutes(10))
        }
    })

private class TestRedisAccessor : RedisRevocationStore.RevocationRedisAccessor {
    private val entries = mutableMapOf<String, Duration>()
    var throwOnHasKey: Boolean = false

    override fun hasKey(key: String): Boolean {
        if (throwOnHasKey) {
            throw RedisConnectionFailureException("Redis unavailable")
        }
        return entries.containsKey(key)
    }

    override fun setValue(
        key: String,
        ttl: Duration,
    ) {
        entries[key] = ttl
    }

    fun put(key: String) {
        entries[key] = Duration.ZERO
    }

    fun lastSetTtl(key: String): Duration? = entries[key]
}
