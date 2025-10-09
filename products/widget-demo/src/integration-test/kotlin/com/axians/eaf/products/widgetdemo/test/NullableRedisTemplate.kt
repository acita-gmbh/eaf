package com.axians.eaf.products.widgetdemo.test

import org.springframework.data.redis.core.RedisTemplate
import java.util.concurrent.ConcurrentHashMap

/**
 * Nullable Design Pattern implementation for RedisTemplate.
 * Provides an in-memory substitute for Redis operations during integration testing
 * to satisfy JwtSecurityValidator dependencies without external infrastructure.
 */
class NullableRedisTemplate : RedisTemplate<String, String>() {
    private val storage = ConcurrentHashMap.newKeySet<String>()

    companion object {
        fun createNull(): NullableRedisTemplate = NullableRedisTemplate()
    }

    override fun hasKey(key: String): Boolean = storage.contains(key)

    override fun afterPropertiesSet() {
        // Skip RedisConnectionFactory assertion for integration tests.
    }

    fun addKey(key: String) {
        storage.add(key)
    }

    fun clear() {
        storage.clear()
    }
}
