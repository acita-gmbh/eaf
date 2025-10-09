@file:Suppress("RedundantProjection", "ktlint:standard:function-signature")

package com.axians.eaf.products.widgetdemo.test

import org.springframework.data.redis.connection.BitFieldSubCommands
import org.springframework.data.redis.core.RedisOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit

/**
 * Nullable Design Pattern implementation for RedisTemplate.
 * Provides an in-memory substitute for Redis operations during integration testing
 * to satisfy JwtSecurityValidator dependencies without external infrastructure.
 */
class NullableRedisTemplate : RedisTemplate<String, String>() {
    private val storage = ConcurrentHashMap.newKeySet<String>()
    private val values: ConcurrentMap<String, String> = ConcurrentHashMap()
    private val counters: ConcurrentMap<String, Long> = ConcurrentHashMap()
    private val valueOperations = NullableValueOperations()

    companion object {
        fun createNull(): NullableRedisTemplate = NullableRedisTemplate()
    }

    override fun hasKey(key: String): Boolean = storage.contains(key)

    override fun afterPropertiesSet() {
        // Skip RedisConnectionFactory assertion for integration tests.
    }

    override fun opsForValue(): ValueOperations<String, String> = valueOperations

    fun addKey(key: String) {
        storage.add(key)
    }

    fun clear() {
        storage.clear()
        values.clear()
        counters.clear()
    }

    private inner class NullableValueOperations : ValueOperations<String, String> {
        override fun set(key: String, value: String) {
            values[key] = value
            value.toLongOrNull()?.let { counters[key] = it }
            storage.add(key)
        }

        override fun set(
            key: String,
            value: String,
            timeout: Long,
            unit: TimeUnit,
        ) {
            set(key, value)
        }

        override fun set(
            key: String,
            value: String,
            timeout: Duration,
        ) {
            set(key, value)
        }

        override fun setGet(
            key: String,
            value: String,
            timeout: Long,
            unit: TimeUnit,
        ): String {
            val previous = get(key)
            set(key, value, timeout, unit)
            return previous ?: value
        }

        override fun setGet(
            key: String,
            value: String,
            duration: Duration,
        ): String {
            val previous = get(key)
            set(key, value, duration)
            return previous ?: value
        }

        override fun setIfAbsent(key: String, value: String): Boolean =
            values.putIfAbsent(key, value) == null

        override fun setIfAbsent(
            key: String,
            value: String,
            timeout: Long,
            unit: TimeUnit,
        ): Boolean? = setIfAbsent(key, value)

        override fun setIfAbsent(
            key: String,
            value: String,
            timeout: Duration,
        ): Boolean? = setIfAbsent(key, value)

        override fun setIfPresent(key: String, value: String): Boolean =
            if (values.containsKey(key)) {
                set(key, value)
                true
            } else {
                false
            }

        override fun setIfPresent(
            key: String,
            value: String,
            timeout: Long,
            unit: TimeUnit,
        ): Boolean? = setIfPresent(key, value)

        override fun setIfPresent(
            key: String,
            value: String,
            timeout: Duration,
        ): Boolean? = setIfPresent(key, value)

        override fun multiSet(map: MutableMap<out String, out String>) {
            map.forEach { (k, v) -> set(k, v) }
        }

        override fun multiSetIfAbsent(map: MutableMap<out String, out String>): Boolean {
            if (map.keys.any { values.containsKey(it) }) {
                return false
            }
            multiSet(map)
            return true
        }

        override fun get(key: Any): String? = values[key]

        override fun getAndDelete(key: String): String? {
            counters.remove(key)
            storage.remove(key)
            return values.remove(key)
        }

        override fun getAndExpire(
            key: String,
            timeout: Long,
            unit: TimeUnit,
        ): String? = get(key)

        override fun getAndExpire(
            key: String,
            timeout: Duration,
        ): String? = get(key)

        override fun getAndPersist(key: String): String? = get(key)

        override fun getAndSet(key: String, value: String): String? {
            val previous = get(key)
            set(key, value)
            return previous
        }

        override fun multiGet(keys: Collection<String>): List<String?> = keys.map { get(it) }

        override fun increment(key: String): Long? = increment(key, 1L)

        override fun increment(
            key: String,
            delta: Long,
        ): Long {
            val result = counters.merge(key, delta) { current, change -> current + change } ?: delta
            values[key] = result.toString()
            storage.add(key)
            return result
        }

        override fun increment(
            key: String,
            delta: Double,
        ): Double {
            val merged = counters.merge(key, delta.toLong()) { current, change -> current + change }
            val result = merged?.toDouble() ?: delta
            values[key] = result.toString()
            storage.add(key)
            return result
        }

        override fun decrement(key: String): Long? = increment(key, -1L)

        override fun decrement(
            key: String,
            delta: Long,
        ): Long? = increment(key, -delta)

        override fun append(
            key: String,
            value: String,
        ): Int {
            val newValue = (values[key] ?: "") + value
            set(key, newValue)
            return newValue.length
        }

        override fun get(
            key: String,
            start: Long,
            end: Long,
        ): String? {
            val value = values[key] ?: return null
            val normalizedEnd = if (end < 0) value.length + end else end
            val from = start.coerceAtLeast(0).coerceAtMost(value.length.toLong()).toInt()
            val to = (normalizedEnd + 1).coerceAtLeast(from.toLong()).coerceAtMost(value.length.toLong()).toInt()
            return value.substring(from, to)
        }

        override fun set(key: String, value: String, offset: Long) {
            val current = values[key] ?: ""
            val builder = StringBuilder(current)
            val index = offset.coerceAtLeast(0).toInt()
            while (builder.length < index) {
                builder.append('\u0000')
            }
            value.forEachIndexed { idx, ch ->
                val position = index + idx
                if (position < builder.length) {
                    builder.setCharAt(position, ch)
                } else {
                    builder.append(ch)
                }
            }
            set(key, builder.toString())
        }

        override fun size(key: String): Long = (values[key] ?: "").length.toLong()

        override fun setBit(key: String, offset: Long, value: Boolean): Boolean {
            // No-op bit operations for nullable implementation
            return false
        }

        override fun getBit(key: String, offset: Long): Boolean = false

        override fun bitField(key: String, subCommands: BitFieldSubCommands): List<Long> = emptyList()

        override fun getOperations(): RedisOperations<String, String> = this@NullableRedisTemplate
    }
}
