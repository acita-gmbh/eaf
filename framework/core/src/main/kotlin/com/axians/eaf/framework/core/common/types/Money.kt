package com.axians.eaf.framework.core.common.types

import com.axians.eaf.framework.core.domain.ValueObject
import java.math.BigDecimal
import java.util.Currency

/**
 * Value object representing monetary amounts with currency.
 *
 * Money is immutable and implements structural equality based on both
 * amount and currency. Use BigDecimal for precise decimal arithmetic.
 *
 * Example:
 * ```kotlin
 * val price = Money(
 *     amount = BigDecimal("99.99"),
 *     currency = Currency.getInstance("USD")
 * )
 * ```
 *
 * @property amount The monetary amount with arbitrary precision
 * @property currency The ISO 4217 currency code (e.g., USD, EUR, GBP)
 */
data class Money(
    val amount: BigDecimal,
    val currency: Currency,
) : ValueObject()
