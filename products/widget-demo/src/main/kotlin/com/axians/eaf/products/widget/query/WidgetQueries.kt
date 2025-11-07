package com.axians.eaf.products.widget.query

import com.axians.eaf.products.widget.domain.WidgetId

/**
 * Query to find a single Widget by its unique identifier.
 *
 * Returns the current projection state of the widget, or null if not found.
 *
 * @property widgetId Unique identifier of the widget to retrieve
 */
data class FindWidgetQuery(
    val widgetId: WidgetId,
)

/**
 * Query to list Widgets with cursor-based pagination.
 *
 * Uses cursor pagination (not offset/limit) to provide consistent results
 * even when the underlying dataset changes between page requests.
 *
 * **Performance Target:** <200ms (FR011)
 *
 * @property limit Maximum number of widgets to return (default: 50, max: 100)
 * @property cursor Optional cursor from previous response for pagination (Base64-encoded)
 */
data class ListWidgetsQuery(
    val limit: Int = 50,
    val cursor: String? = null,
)
