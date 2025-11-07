package com.axians.eaf.products.widget.api

import com.axians.eaf.products.widget.domain.WidgetId
import com.axians.eaf.products.widget.query.WidgetProjection
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * Request DTO for creating a new Widget.
 *
 * @property name Display name for the widget (1-255 characters, cannot be blank)
 */
@Schema(description = "Request to create a new widget")
data class CreateWidgetRequest(
    @param:NotBlank(message = "Name cannot be blank")
    @param:Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    @param:Schema(
        description = "Display name of the widget",
        example = "My Widget",
        minLength = 1,
        maxLength = 255,
    )
    val name: String,
)

/**
 * Request DTO for updating an existing Widget.
 *
 * @property name New display name for the widget (1-255 characters, cannot be blank)
 */
@Schema(description = "Request to update a widget's name")
data class UpdateWidgetRequest(
    @param:NotBlank(message = "Name cannot be blank")
    @param:Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
    @param:Schema(
        description = "New display name of the widget",
        example = "Updated Widget Name",
        minLength = 1,
        maxLength = 255,
    )
    val name: String,
)

/**
 * Response DTO representing a Widget.
 *
 * @property id Unique identifier of the widget
 * @property name Display name of the widget
 * @property published Whether the widget is publicly available
 * @property createdAt UTC timestamp when the widget was created
 * @property updatedAt UTC timestamp when the widget was last modified
 */
@Schema(description = "Widget representation")
data class WidgetResponse(
    @param:Schema(description = "Unique identifier of the widget", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    val id: String,
    @param:Schema(description = "Display name of the widget", example = "My Widget")
    val name: String,
    @param:Schema(description = "Whether the widget is published", example = "false")
    val published: Boolean,
    @param:Schema(description = "Creation timestamp (ISO 8601 UTC)", example = "2025-11-07T12:34:56.789Z")
    val createdAt: Instant,
    @param:Schema(description = "Last update timestamp (ISO 8601 UTC)", example = "2025-11-07T12:34:56.789Z")
    val updatedAt: Instant,
)

/**
 * Paginated response for widget list queries.
 *
 * Uses cursor-based pagination for stable page boundaries.
 *
 * @property data List of widgets for the current page
 * @property nextCursor Opaque cursor for fetching the next page (null if no more results)
 * @property hasMore Whether additional results exist beyond this page
 */
@Schema(description = "Paginated list of widgets")
data class PaginatedResponse<T>(
    @param:Schema(description = "List of widgets in this page")
    val data: List<T>,
    @param:Schema(description = "Cursor for next page (null if no more results)", nullable = true)
    val nextCursor: String?,
    @param:Schema(description = "Whether more results exist")
    val hasMore: Boolean,
)

/**
 * Extension function to convert WidgetProjection to WidgetResponse DTO.
 */
fun WidgetProjection.toResponse(): WidgetResponse =
    WidgetResponse(
        id = this.id.value,
        name = this.name,
        published = this.published,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
