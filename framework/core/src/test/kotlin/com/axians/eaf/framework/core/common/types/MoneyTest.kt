package com.axians.eaf.framework.core.common.types

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Currency

/**
 * Unit tests for Money value object.
 *
 * Validates:
 * - Creation with amount and currency
 * - Structural equality (amount + currency)
 * - Immutability (data class)
 * - BigDecimal precision
 * - Zero and negative amounts
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 */
class MoneyTest {

    @Test
    fun `should create Money with amount and currency`() {
        val money = Money(amount = BigDecimal("100.50"), currency = Currency.getInstance("USD"))

        assertThat(money.amount).isEqualTo(BigDecimal("100.50"))
        assertThat(money.currency).isEqualTo(Currency.getInstance("USD"))
    }

    @Test
    fun `should be equal when amount and currency equal`() {
        val money1 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("EUR"))
        val money2 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("EUR"))

        assertThat(money1).isEqualTo(money2)
    }

    @Test
    fun `should not be equal when amount differs`() {
        val money1 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("USD"))
        val money2 = Money(amount = BigDecimal("200.00"), currency = Currency.getInstance("USD"))

        assertThat(money1).isNotEqualTo(money2)
    }

    @Test
    fun `should not be equal when currency differs`() {
        val money1 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("USD"))
        val money2 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("EUR"))

        assertThat(money1).isNotEqualTo(money2)
    }

    @Test
    fun `should preserve BigDecimal precision`() {
        val preciseAmount = BigDecimal("123.456789")
        val money = Money(amount = preciseAmount, currency = Currency.getInstance("USD"))

        assertThat(money.amount).isEqualTo(preciseAmount)
        assertThat(money.amount.scale()).isEqualTo(6)
    }

    @Test
    fun `should handle zero amounts`() {
        val money = Money(amount = BigDecimal.ZERO, currency = Currency.getInstance("USD"))

        assertThat(money.amount).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `should handle negative amounts (debt representation)`() {
        val money = Money(amount = BigDecimal("-50.00"), currency = Currency.getInstance("USD"))

        assertThat(money.amount).isEqualTo(BigDecimal("-50.00"))
    }

    @Test
    fun `should have consistent hashCode for same values`() {
        val money1 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("USD"))
        val money2 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("USD"))

        assertThat(money1.hashCode()).isEqualTo(money2.hashCode())
    }

    @Test
    fun `should work as Map keys`() {
        val money1 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("USD"))
        val money2 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("USD"))

        val map = mutableMapOf<Money, String>()
        map[money1] = "first"
        map[money2] = "second"

        assertThat(map).hasSize(1)
        assertThat(map[money1]).isEqualTo("second")
    }

    @Test
    fun `should support copy() with modifications`() {
        val original = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("USD"))
        val modified = original.copy(amount = BigDecimal("200.00"))

        assertThat(original.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(modified.amount).isEqualTo(BigDecimal("200.00"))
        assertThat(modified.currency).isEqualTo(Currency.getInstance("USD"))
    }
}
