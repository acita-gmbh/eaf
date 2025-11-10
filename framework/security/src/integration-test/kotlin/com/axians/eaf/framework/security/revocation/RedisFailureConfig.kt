package com.axians.eaf.framework.security.revocation

import java.time.Duration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.RedisConnectionFailureException

@TestConfiguration
open class RedisFailureConfig {
    @Bean
    @Primary
    fun failingRedisAccessor(): RedisRevocationStore.RevocationRedisAccessor =
        object : RedisRevocationStore.RevocationRedisAccessor {
            private fun redisDown(): Nothing = throw RedisConnectionFailureException("Redis unavailable")

            override fun hasKey(key: String): Boolean = redisDown()

            override fun setValue(key: String, ttl: Duration) {
                redisDown()
            }
        }
}
