package com.axians.eaf.framework.security.revocation

import com.axians.eaf.framework.security.config.RevocationProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class RedisRevocationStoreTest : FunSpec({
    lateinit var redisTemplate: StringRedisTemplate
    lateinit var valueOperations: ValueOperations<String, String>
    lateinit var meterRegistry: SimpleMeterRegistry

    beforeTest {
        redisTemplate = Mockito.mock(StringRedisTemplate::class.java)
        @Suppress("UNCHECKED_CAST")
        valueOperations = Mockito.mock(ValueOperations::class.java) as ValueOperations<String, String>
        Mockito.`when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        meterRegistry = SimpleMeterRegistry()
    }

    fun store(properties: RevocationProperties = RevocationProperties()): RedisRevocationStore =
        RedisRevocationStore(
            redisTemplate = redisTemplate,
            properties = properties,
            meterRegistry = meterRegistry,
            clock = Clock.fixed(Instant.parse("2025-11-10T00:00:00Z"), ZoneOffset.UTC),
        )

    test("isRevoked returns true when Redis contains key") {
        Mockito.`when`(redisTemplate.hasKey(anyString())).thenReturn(true)

        val store = store()

        store.isRevoked("jti-123").shouldBeTrue()
    }

    test("isRevoked returns false on cache miss") {
        Mockito.`when`(redisTemplate.hasKey(anyString())).thenReturn(false)

        val store = store()

        store.isRevoked("jti-123").shouldBeFalse()
    }

    test("fail-open returns false when Redis unavailable") {
        Mockito.`when`(redisTemplate.hasKey(anyString())).thenThrow(RedisConnectionFailureException("boom"))

        val store = store()

        store.isRevoked("jti-123").shouldBeFalse()
    }

    test("fail-closed throws SecurityException when Redis unavailable") {
        Mockito.`when`(redisTemplate.hasKey(anyString())).thenThrow(RedisConnectionFailureException("boom"))

        val store = store(RevocationProperties(failClosed = true))

        shouldThrow<SecurityException> {
            store.isRevoked("jti-123")
        }
    }

    test("revoke stores entry with TTL derived from expiration or default") {
        val store = store()
        val expiration = Instant.parse("2025-11-10T00:15:00Z")

        store.revoke("jti-123", expiration)

        val ttlCaptor = ArgumentCaptor.forClass(Duration::class.java)
        Mockito.verify(valueOperations)
            .set(Mockito.eq("jwt:revoked:jti-123"), Mockito.eq("revoked"), ttlCaptor.capture())

        ttlCaptor.value.shouldBe(Duration.ofMinutes(15))
    }

    test("revoke falls back to default TTL when expiration missing or shorter") {
        val store = store()

        store.revoke("jti-999", null)

        val ttlCaptor = ArgumentCaptor.forClass(Duration::class.java)
        Mockito.verify(valueOperations)
            .set(Mockito.eq("jwt:revoked:jti-999"), Mockito.eq("revoked"), ttlCaptor.capture())

        ttlCaptor.value.shouldBe(Duration.ofMinutes(10))
    }

})
