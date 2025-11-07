package com.axians.eaf.products.widget.api

import com.axians.eaf.products.widget.domain.CreateWidgetCommand
import com.axians.eaf.products.widget.domain.UpdateWidgetCommand
import com.axians.eaf.products.widget.domain.WidgetId
import com.axians.eaf.products.widget.query.FindWidgetQuery
import com.axians.eaf.products.widget.query.ListWidgetsQuery
import com.axians.eaf.products.widget.query.PaginatedWidgetResponse
import com.axians.eaf.products.widget.query.WidgetProjection
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryGateway
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * REST API controller for Widget CRUD operations.
 *
 * Implements the CQRS pattern using Axon Framework:
 * - Writes: CommandGateway.sendAndWait() for synchronous command execution
 * - Reads: QueryGateway.query() for projection queries
 *
 * **Performance Target:** API p95 <200ms (FR011)
 *
 * All endpoints return RFC 7807 ProblemDetail on errors (Story 2.9).
 */
@RestController
@RequestMapping("/api/v1/widgets")
@Tag(name = "Widgets", description = "Widget CRUD operations")
class WidgetController(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway,
) {
    /**
     * Create a new Widget.
     *
     * Synchronously dispatches CreateWidgetCommand and waits for completion.
     *
     * @param request Widget creation request with name
     * @return Created widget with HTTP 201 Created
     */
    @PostMapping(
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a new widget",
        description = "Creates a new widget with the specified name. Returns 201 Created with the created widget.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Widget created successfully",
                content = [Content(schema = Schema(implementation = WidgetResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request (validation error)",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    fun createWidget(
        @Valid @RequestBody request: CreateWidgetRequest,
    ): WidgetResponse {
        val widgetId = WidgetId(UUID.randomUUID())

        // Synchronous command execution (CQRS write path)
        commandGateway.sendAndWait<Any>(
            CreateWidgetCommand(widgetId, request.name),
            COMMAND_TIMEOUT_SECONDS,
            TimeUnit.SECONDS,
        )

        // Query created widget (CQRS read path)
        // Retry logic to handle eventual consistency of projection
        val query = FindWidgetQuery(widgetId)
        var widget: WidgetProjection? = null
        var retries = 0
        while (widget == null && retries < MAX_RETRIES) {
            widget =
                queryGateway
                    .query(
                        query,
                        ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                    ).get()
                    .orElse(null)

            if (widget == null) {
                retries++
                Thread.sleep(RETRY_DELAY_MS)
            }
        }

        return widget?.toResponse()
            ?: throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Widget created but projection not yet available after ${MAX_RETRIES * RETRY_DELAY_MS}ms",
            )
    }

    /**
     * Get a Widget by ID.
     *
     * Queries the read model projection for the widget.
     *
     * @param id Unique identifier of the widget
     * @return Widget projection with HTTP 200 OK
     * @throws ResponseStatusException 404 if widget not found
     */
    @GetMapping(
        "/{id}",
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    @Operation(
        summary = "Get widget by ID",
        description = "Retrieves a widget by its unique identifier. Returns 404 if not found.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Widget found",
                content = [Content(schema = Schema(implementation = WidgetResponse::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Widget not found",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    fun getWidget(
        @Parameter(description = "UUID of the widget to retrieve")
        @PathVariable id: UUID,
    ): WidgetResponse {
        val query = FindWidgetQuery(WidgetId(id))

        // Query read model with retry for eventual consistency
        var widget: WidgetProjection? = null
        var retries = 0
        while (widget == null && retries < MAX_RETRIES) {
            widget =
                queryGateway
                    .query(
                        query,
                        ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                    ).get()
                    .orElse(null)

            if (widget == null) {
                if (retries < MAX_RETRIES - 1) {
                    retries++
                    Thread.sleep(RETRY_DELAY_MS)
                } else {
                    // Final retry failed - widget not found
                    throw ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Widget with id '$id' not found",
                    )
                }
            }
        }

        return widget!!.toResponse()
    }

    /**
     * List Widgets with cursor-based pagination.
     *
     * Queries the read model for a paginated list of widgets.
     *
     * @param limit Maximum number of widgets to return (default: 50, max: 100)
     * @param cursor Opaque cursor from previous response for pagination
     * @return Paginated list of widgets with HTTP 200 OK
     */
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "List all widgets with cursor pagination",
        description = "Returns a paginated list of widgets. Use cursor-based pagination for stable results.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Widget list retrieved successfully",
                content = [Content(schema = Schema(implementation = PaginatedResponse::class))],
            ),
        ],
    )
    fun listWidgets(
        @Parameter(description = "Maximum number of widgets to return")
        @RequestParam(defaultValue = "50") limit: Int,
        @Parameter(description = "Cursor for pagination (from previous response)")
        @RequestParam(required = false) cursor: String?,
    ): PaginatedResponse<WidgetResponse> {
        val query = ListWidgetsQuery(limit, cursor)

        // Query read model (CQRS read path)
        val result =
            queryGateway
                .query(
                    query,
                    ResponseTypes.instanceOf(PaginatedWidgetResponse::class.java),
                ).get()

        return PaginatedResponse(
            data = result.widgets.map { it.toResponse() },
            nextCursor = result.nextCursor,
            hasMore = result.hasMore,
        )
    }

    /**
     * Update a Widget's name.
     *
     * Synchronously dispatches UpdateWidgetCommand and waits for completion.
     *
     * @param id Unique identifier of the widget to update
     * @param request Widget update request with new name
     * @return Updated widget with HTTP 200 OK
     * @throws ResponseStatusException 404 if widget not found
     */
    @PutMapping(
        "/{id}",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    @Operation(
        summary = "Update a widget",
        description = "Updates a widget's name. Returns 404 if the widget does not exist.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Widget updated successfully",
                content = [Content(schema = Schema(implementation = WidgetResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request (validation error)",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Widget not found",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    fun updateWidget(
        @Parameter(description = "UUID of the widget to update")
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateWidgetRequest,
    ): WidgetResponse {
        // Synchronous command execution (CQRS write path)
        // Let exceptions propagate to ProblemDetailExceptionHandler
        commandGateway.sendAndWait<Any>(
            UpdateWidgetCommand(WidgetId(id), request.name),
            COMMAND_TIMEOUT_SECONDS,
            TimeUnit.SECONDS,
        )

        // Query updated widget (CQRS read path)
        return getWidget(id)
    }

    companion object {
        /**
         * Command timeout in seconds (FR011: API p95 <200ms target).
         */
        private const val COMMAND_TIMEOUT_SECONDS = 10L

        /**
         * Maximum retries for projection query (eventual consistency).
         */
        private const val MAX_RETRIES = 50

        /**
         * Delay between retries in milliseconds.
         */
        private const val RETRY_DELAY_MS = 100L
    }
}
