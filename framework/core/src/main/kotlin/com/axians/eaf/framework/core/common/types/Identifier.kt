package com.axians.eaf.framework.core.common.types

/**
 * Base interface for type-safe identifiers in the domain model.
 *
 * Implementations should be value objects providing strong typing for IDs,
 * preventing accidental mixing of different identifier types.
 *
 * Example:
 * ```kotlin
 * data class OrderId(override val value: String) : Identifier
 * data class WidgetId(override val value: String) : Identifier
 * ```
 *
 * @property value The string representation of the identifier
 */
interface Identifier {
    val value: String
}
