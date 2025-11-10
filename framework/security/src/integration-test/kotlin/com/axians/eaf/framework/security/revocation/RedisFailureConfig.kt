package com.axians.eaf.framework.security.revocation

import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

@TestConfiguration
class RedisFailureConfig {
    @Bean
    @Primary
    fun failingRedisTemplate(): StringRedisTemplate {
        val template = Mockito.mock(StringRedisTemplate::class.java)
        @Suppress("UNCHECKED_CAST")
        val valueOps = Mockito.mock(ValueOperations::class.java) as ValueOperations<String, String>
        Mockito.`when`(template.opsForValue()).thenReturn(valueOps)
        Mockito.`when`(template.hasKey(anyString()))
            .thenThrow(RedisConnectionFailureException("Redis unavailable"))
        return template
    }
}
