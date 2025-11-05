package com.axians.eaf.products.widget.domain

import com.axians.eaf.framework.core.domain.ValueObject
import java.util.UUID

/**
 * Widget aggregate identifier.
 *
 * Wraps a UUID string to provide type-safe aggregate identification
 * for the Widget domain model.
 *
 * @property value The unique identifier string (UUID format)
 */
data class WidgetId(
    val value: String,
) : ValueObject() {
    /**
     * Convenience constructor accepting UUID.
     *
     * @param uuid The UUID to convert to string identifier
     */
    constructor(uuid: UUID) : this(uuid.toString())
}
