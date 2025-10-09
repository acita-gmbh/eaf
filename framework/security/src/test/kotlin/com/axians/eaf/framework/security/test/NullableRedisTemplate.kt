
package com.axians.eaf.framework.security.test

import org.springframework.data.redis.core.RedisTemplate
import java.util.concurrent.ConcurrentHashMap

/**
 * Nullable Design Pattern implementation for RedisTemplate.
 * Provides a fast, in-memory substitute for Redis operations in unit tests,
 * specifically for checking key existence in the JWT revocation blacklist.
 *
 * This follows the EAF "Zero-Mocks Policy" for infrastructure dependencies.
 */
class NullableRedisTemplate : RedisTemplate<String, String>() {
    private val storage = ConcurrentHashMap.newKeySet<String>()

    companion object {
        /**
         * Factory method to create a clean, empty nullable instance.
         */
        fun createNull(): NullableRedisTemplate = NullableRedisTemplate()
    }

    /**
     * Simulates the `hasKey` operation by checking for the key in the in-memory set.
     *
     * @param key The key to check (e.g., "revoked:<jti>").
     * @return True if the key exists in the set, false otherwise.
     */
    override fun hasKey(key: String): Boolean {
        return storage.contains(key)
    }

    /**
     * Test-only helper method to add a key to the in-memory set,
     * simulating the blacklisting of a token.
     *
     * @param key The key to add to the blacklist.
     */
    fun addKey(key: String) {
        storage.add(key)
    }

    /**
     * Test-only helper method to clear all keys from the in-memory set,
     * ensuring a clean state between tests.
     */
    fun clear() {
        storage.clear()
    }
}
