package com.axians.eaf.framework.core.common.types

import com.axians.eaf.framework.core.domain.ValueObject
import java.math.BigDecimal

/**
 * Value object representing quantities with units of measurement.
 *
 * Quantity is immutable and implements structural equality based on both
 * numeric value and unit. Use BigDecimal for precise decimal arithmetic.
 *
 * Example:
 * ```kotlin
 * val weight = Quantity(
 *     value = BigDecimal("10.5"),
 *     unit = "kg"
 * )
 *
 * val count = Quantity(
 *     value = BigDecimal("100"),
 *     unit = "items"
 * )
 * ```
 *
 * @property value The numeric quantity value with arbitrary precision
 * @property unit The unit of measurement (e.g., "kg", "liters", "items", "meters")
 */
data class Quantity(
    val value: BigDecimal,
    val unit: String,
) : ValueObject()
