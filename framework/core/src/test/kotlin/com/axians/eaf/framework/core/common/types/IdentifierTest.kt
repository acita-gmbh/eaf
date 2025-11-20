package com.axians.eaf.framework.core.common.types

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for Identifier interface implementations.
 *
 * Validates:
 * - Value-based equality
 * - Type-safety across different identifier types
 * - Collections behavior
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 */
class IdentifierTest {

    // Test identifier implementations
    data class OrderId(
        override val value: String,
    ) : Identifier

    data class WidgetId(
        override val value: String,
    ) : Identifier

    @Test
    fun `should create identifier with string value`() {
        val id = OrderId("order-123")
        assertThat(id.value).isEqualTo("order-123")
    }

    @Test
    fun `should be equal when values equal`() {
        val id1 = OrderId("order-123")
        val id2 = OrderId("order-123")
        assertThat(id1).isEqualTo(id2)
    }

    @Test
    fun `should not be equal when values differ`() {
        val id1 = OrderId("order-123")
        val id2 = OrderId("order-456")
        assertThat(id1).isNotEqualTo(id2)
    }

    @Test
    fun `should work with different identifier types`() {
        val orderId = OrderId("123")
        val widgetId = WidgetId("456")

        assertThat(orderId.value).isEqualTo("123")
        assertThat(widgetId.value).isEqualTo("456")
    }

    @Test
    fun `should work correctly in Set collections`() {
        val id1 = OrderId("order-123")
        val id2 = OrderId("order-123")
        val id3 = OrderId("order-456")

        val set = setOf(id1, id2, id3)
        assertThat(set).hasSize(2) // id1 and id2 are duplicates
    }

    @Test
    fun `should work correctly as Map keys`() {
        val id1 = OrderId("order-123")
        val id2 = OrderId("order-123")

        val map = mutableMapOf<OrderId, String>()
        map[id1] = "first"
        map[id2] = "second"

        assertThat(map).hasSize(1) // Same key
        assertThat(map[id1]).isEqualTo("second") // Overwritten value
    }
}
