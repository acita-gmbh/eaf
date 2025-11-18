package com.axians.eaf.products.widget.api

import com.axians.eaf.framework.multitenancy.TenantContext
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
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryGateway
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
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
    @PreAuthorize("hasRole('WIDGET_ADMIN')")
    @Operation(
        summary = "Create a new widget",
        description =
            "Creates a new widget with the specified name. " +
                "Returns 201 Created with the created widget. Requires WIDGET_ADMIN role.",
        security = [SecurityRequirement(name = "bearer-jwt")],
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
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Missing or invalid JWT token",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - User lacks WIDGET_ADMIN role",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    fun createWidget(
        @Valid @RequestBody request: CreateWidgetRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt?, // Nullable for test profile (permitAll)
    ): WidgetResponse {
        val widgetId = WidgetId(UUID.randomUUID())

        // Extract tenant ID from TenantContext (set by TenantContextFilter, Story 4.6)
        val tenantId = TenantContext.getCurrentTenantId()

        // Synchronous command execution (CQRS write path)
        commandGateway.sendAndWait<Any>(
            CreateWidgetCommand(widgetId, request.name, tenantId),
            COMMAND_TIMEOUT_SECONDS,
            TimeUnit.SECONDS,
        )

        // Query created widget with retry for eventual consistency
        val projection =
            waitForProjection(
                query = FindWidgetQuery(widgetId),
                condition = { true }, // Accept any non-null projection
                errorMessage = "Widget created but projection not available after ${MAX_RETRIES * RETRY_DELAY_MS}ms",
            )

        return projection.toResponse()
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
    @PreAuthorize("hasAnyRole('WIDGET_ADMIN', 'WIDGET_VIEWER')")
    @Operation(
        summary = "Get widget by ID",
        description =
            "Retrieves a widget by its unique identifier. Returns 404 if not found. " +
                "Requires WIDGET_ADMIN or WIDGET_VIEWER role.",
        security = [SecurityRequirement(name = "bearer-jwt")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Widget found",
                content = [Content(schema = Schema(implementation = WidgetResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Missing or invalid JWT token",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - User lacks required role",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
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
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt?,
    ): WidgetResponse {
        // Query with retry for eventual consistency
        val projection =
            try {
                waitForProjection(
                    query = FindWidgetQuery(WidgetId(id)),
                    condition = { true }, // Accept any non-null projection
                    errorMessage = "Widget with id '$id' not found after ${MAX_RETRIES * RETRY_DELAY_MS}ms",
                )
            } catch (ex: ResponseStatusException) {
                // Convert INTERNAL_SERVER_ERROR to NOT_FOUND for missing widgets
                throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Widget with id '$id' not found",
                )
            }

        return projection.toResponse()
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
    @PreAuthorize("hasAnyRole('WIDGET_ADMIN', 'WIDGET_VIEWER')")
    @Operation(
        summary = "List all widgets with cursor pagination",
        description =
            "Returns a paginated list of widgets. Use cursor-based pagination for stable results. " +
                "Requires WIDGET_ADMIN or WIDGET_VIEWER role.",
        security = [SecurityRequirement(name = "bearer-jwt")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Widget list retrieved successfully",
                content = [Content(schema = Schema(implementation = PaginatedResponse::class))],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Missing or invalid JWT token",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - User lacks required role",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
        ],
    )
    fun listWidgets(
        @Parameter(description = "Maximum number of widgets to return (1-100)")
        @RequestParam(defaultValue = "50")
        @Min(1)
        @Max(100)
        limit: Int,
        @Parameter(description = "Cursor for pagination (from previous response)")
        @RequestParam(required = false) cursor: String?,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt?,
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
    @PreAuthorize("hasRole('WIDGET_ADMIN')")
    @Operation(
        summary = "Update a widget",
        description = "Updates a widget's name. Returns 404 if the widget does not exist. Requires WIDGET_ADMIN role.",
        security = [SecurityRequirement(name = "bearer-jwt")],
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
                responseCode = "401",
                description = "Unauthorized - Missing or invalid JWT token",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Forbidden - User lacks WIDGET_ADMIN role",
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
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt?,
    ): WidgetResponse {
        // Extract tenant ID from TenantContext (set by TenantContextFilter, Story 4.6)
        val tenantId = TenantContext.getCurrentTenantId()

        // Synchronous command execution (CQRS write path)
        // Let exceptions propagate to ProblemDetailExceptionHandler
        commandGateway.sendAndWait<Any>(
            UpdateWidgetCommand(WidgetId(id), request.name, tenantId),
            COMMAND_TIMEOUT_SECONDS,
            TimeUnit.SECONDS,
        )

        // Query updated widget with retry until projection reflects the update
        val projection =
            waitForProjection(
                query = FindWidgetQuery(WidgetId(id)),
                condition = { it.name == request.name }, // Wait for updated name
                errorMessage = "Widget updated but projection not consistent after ${MAX_RETRIES * RETRY_DELAY_MS}ms",
            )

        return projection.toResponse()
    }

    /**
     * Polls projection until condition is met or timeout is reached.
     *
     * **Performance Note:** This uses blocking Thread.sleep() which impacts throughput.
     * For MVP phase, this is acceptable as retry duration is typically <200ms.
     * Epic 5 (Observability) or Epic 8 (Performance) should migrate to non-blocking
     * approach (CompletableFuture, Reactor, or Spring @Async).
     *
     * @param query Query to execute
     * @param condition Predicate to test projection (return true when satisfied)
     * @param errorMessage Message for ResponseStatusException if timeout occurs
     * @return Projection when condition is met
     * @throws ResponseStatusException if condition not met within timeout
     */
    private fun waitForProjection(
        query: FindWidgetQuery,
        condition: (WidgetProjection) -> Boolean,
        errorMessage: String,
    ): WidgetProjection {
        var retries = 0
        while (retries < MAX_RETRIES) {
            val projection =
                queryGateway
                    .query(
                        query,
                        ResponseTypes.optionalInstanceOf(WidgetProjection::class.java),
                    ).get()
                    .orElse(null)

            if (projection != null && condition(projection)) {
                return projection
            }

            if (retries < MAX_RETRIES - 1) {
                retries++
                Thread.sleep(RETRY_DELAY_MS)
            } else {
                throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    errorMessage,
                )
            }
        }

        // Unreachable but required for compiler
        throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, errorMessage)
    }

    companion object {
        /**
         * Command timeout in seconds.
         *
         * NOTE: FR011 specifies API p95 <200ms as the performance target.
         * This 10s timeout is a safety net for exceptional cases, not the expected latency.
         * Normal operations complete in <200ms.
         */
        private const val COMMAND_TIMEOUT_SECONDS = 10L

        /**
         * Maximum retries for projection query (eventual consistency).
         *
         * Total timeout: 50 × 100ms = 5 seconds maximum.
         * Typical projection lag: <200ms (well within FR011 target).
         */
        private const val MAX_RETRIES = 50

        /**
         * Delay between retries in milliseconds.
         *
         * NOTE: Uses blocking Thread.sleep() - acceptable for MVP, optimize in Epic 5/8.
         */
        private const val RETRY_DELAY_MS = 100L
    }
}
