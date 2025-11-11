package com.axians.eaf.framework.security.revocation

import com.axians.eaf.framework.security.config.RevocationProperties
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Redis-backed revocation store for Layer 7 JWT validation.
 *
 * Responsibilities:
 * - Persist revoked JWT IDs (JTI) with TTL aligned to token expiration
 * - Query Redis to determine whether a presented token has been revoked
 * - Support fail-open (default) or fail-closed behavior when Redis is unavailable
 * - Emit Micrometer metrics for latency, results, and cache hit rate
 */
@Component
@Profile("!test")
class RedisRevocationStore( // Story 3.7: Layer 7 revocation cache
    redisTemplate: StringRedisTemplate,
    private val properties: RevocationProperties,
    meterRegistry: MeterRegistry,
    private val clock: Clock = Clock.systemUTC(),
    private val redisAccessor: RevocationRedisAccessor = StringRedisTemplateAccessor(redisTemplate),
) : TokenRevocationStore {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val checkTimer: Timer =
        Timer
            .builder("security.revocation.check.duration")
            .description("Time spent verifying JWT revocation status via Redis")
            .publishPercentileHistogram()
            .register(meterRegistry)

    private val hitCounter: Counter = resultCounter(meterRegistry, "hit")
    private val missCounter: Counter = resultCounter(meterRegistry, "miss")
    private val errorCounter: Counter = resultCounter(meterRegistry, "error")

    private val hitSamples = AtomicLong(0)
    private val totalSamples = AtomicLong(0)

    init {
        Gauge
            .builder("security.revocation.cache.hit-rate") {
                val total = totalSamples.get()
                if (total == 0L) {
                    1.0
                } else {
                    hitSamples.get().toDouble() / total
                }
            }.description("Rolling hit rate for revocation cache lookups (1.0 = perfect hit rate)")
            .strongReference(true)
            .register(meterRegistry)
    }

    /**
     * Determines whether the provided JTI has been revoked.
     *
     * @return true when Redis contains the revoked key, false otherwise.
     * @throws SecurityException when fail-closed is enabled and Redis is unavailable.
     */
    override fun isRevoked(jti: String): Boolean {
        require(jti.isNotBlank()) { "JTI must not be blank" }

        val sample = Timer.start()
        return try {
            val revoked = redisAccessor.hasKey(prefixedKey(jti))
            sample.stop(checkTimer)
            recordResult(revoked)
            revoked
        } catch (ex: DataAccessException) {
            sample.stop(checkTimer)
            recordError()
            handleRedisFailure(ex)
        }
    }

    /**
     * Stores the given JTI as revoked using a TTL derived from the token expiration.
     */
    override fun revoke(
        jti: String,
        expiresAt: Instant?,
    ) {
        require(jti.isNotBlank()) { "JTI must not be blank" }

        val ttl = computeTtl(expiresAt)
        redisAccessor.setValue(prefixedKey(jti), ttl)
    }

    private fun recordResult(revoked: Boolean) {
        totalSamples.incrementAndGet()
        if (revoked) {
            hitSamples.incrementAndGet()
            hitCounter.increment()
        } else {
            missCounter.increment()
        }
    }

    private fun recordError() {
        totalSamples.incrementAndGet()
        errorCounter.increment()
    }

    private fun handleRedisFailure(ex: DataAccessException): Boolean {
        val rootCause = ex.rootCause ?: ex
        logger.warn(
            "Redis unavailable during revocation check (failClosed={})",
            properties.failClosed,
            rootCause,
        )

        if (properties.failClosed) {
            throw SecurityException("Cannot verify token revocation status - Redis unavailable", ex)
        }

        return false // Fail-open (default) to preserve availability
    }

    private fun computeTtl(expiresAt: Instant?): Duration {
        val now = Instant.now(clock)
        val expiresIn =
            expiresAt
                ?.let { Duration.between(now, it) }
                ?.takeUnless { it.isNegative }
                ?: Duration.ZERO

        val ttl = if (expiresIn < properties.defaultTtl) properties.defaultTtl else expiresIn
        return if (ttl.isZero) properties.defaultTtl else ttl
    }

    private fun prefixedKey(jti: String): String = properties.keyPrefix + jti

    private fun resultCounter(
        meterRegistry: MeterRegistry,
        result: String,
    ): Counter =
        Counter
            .builder("security.revocation.check.total")
            .description("Revocation lookup results grouped by outcome")
            .tag("result", result)
            .register(meterRegistry)

    interface RevocationRedisAccessor {
        fun hasKey(key: String): Boolean

        fun setValue(
            key: String,
            ttl: Duration,
        )
    }

    private class StringRedisTemplateAccessor(
        private val template: StringRedisTemplate,
    ) : RevocationRedisAccessor {
        override fun hasKey(key: String): Boolean = template.hasKey(key) == true

        override fun setValue(
            key: String,
            ttl: Duration,
        ) {
            template.opsForValue().set(key, "revoked", ttl)
        }
    }
}
