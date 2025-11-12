package com.axians.eaf.framework.security.revocation

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.RedisConnectionFailureException
import java.time.Duration

@TestConfiguration
@Profile("redis-failure")
open class RedisFailureConfig {
    @Bean
    @Primary
    open fun failingRedisAccessor(): RedisRevocationStore.RevocationRedisAccessor =
        object : RedisRevocationStore.RevocationRedisAccessor {
            private fun redisDown(): Nothing = throw RedisConnectionFailureException("Redis unavailable")

            override fun hasKey(key: String): Boolean = redisDown()

            override fun setValue(
                key: String,
                ttl: Duration,
            ) {
                redisDown()
            }
        }

    @Bean
    @Primary
    open fun testMeterRegistry(): MeterRegistry = SimpleMeterRegistry()
}
