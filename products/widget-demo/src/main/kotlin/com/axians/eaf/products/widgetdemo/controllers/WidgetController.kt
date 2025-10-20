package com.axians.eaf.products.widgetdemo.controllers

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.api.widget.dto.PagedResponse
import com.axians.eaf.api.widget.dto.WidgetResponse
import com.axians.eaf.api.widget.queries.FindWidgetByIdQuery
import com.axians.eaf.api.widget.queries.FindWidgetsQuery
import com.axians.eaf.framework.security.tenant.TenantContext
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.queryhandling.QueryGateway
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/widgets")
@PreAuthorize("hasRole('USER')")
class WidgetController(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway,
    private val tenantContext: TenantContext,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('widget:create')")
    fun createWidget(
        @RequestBody request: CreateWidgetRequest,
    ): ResponseEntity<Any> {
        val tenantId = tenantContext.getCurrentTenantId()
        val widgetId = UUID.randomUUID().toString()

        val command =
            CreateWidgetCommand(
                widgetId = widgetId,
                tenantId = tenantId,
                name = request.name,
                description = request.description,
                value = request.value,
                category = request.category,
                metadata = request.metadata ?: emptyMap(),
            )

        commandGateway.sendAndWait<String>(command, 5, TimeUnit.SECONDS)
        return ResponseEntity
            .created(URI.create("/widgets/$widgetId"))
            .body(mapOf("id" to widgetId, "status" to "created"))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('widget:read')")
    fun getWidget(
        @PathVariable("id") widgetId: String,
    ): ResponseEntity<WidgetResponse> {
        val tenantId = tenantContext.getCurrentTenantId()
        val query = FindWidgetByIdQuery(widgetId, tenantId)

        val response = queryGateway.query(query, WidgetResponse::class.java).get(5, TimeUnit.SECONDS)

        return if (response != null) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('widget:read')")
    fun getWidgets(
        @RequestParam params: Map<String, String>,
    ): ResponseEntity<PagedResponse<WidgetResponse>> {
        val tenantId = tenantContext.getCurrentTenantId()

        val page = params["page"]?.toIntOrNull() ?: 0
        val size = (params["size"]?.toIntOrNull() ?: 20).coerceAtMost(100)
        val sort = params["sort"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val category = params["category"]?.takeIf { it.isNotBlank() }
        val search = params["search"]?.takeIf { it.isNotBlank() }

        val query =
            FindWidgetsQuery(
                tenantId = tenantId,
                page = page,
                size = size,
                sort = sort,
                category = category,
                search = search,
            )

        // Story 9.2 Fix: Use custom PagedResponseType to handle generic type matching
        // Background: Axon 4.12 cannot match generic types like PagedResponse<T> due to type erasure.
        // The PagedResponseType provides the missing generic type information at runtime.
        val responseType =
            com.axians.eaf.api.responsetypes.PagedResponseType.pagedInstanceOf(
                WidgetResponse::class.java,
            )
        val response = queryGateway.query(query, responseType).get(5, TimeUnit.SECONDS)

        // AC3: Set pagination headers for React-Admin compatibility
        val rangeStart = page * size
        val rangeEnd = rangeStart + response.content.size - 1
        val contentRange = "widgets $rangeStart-$rangeEnd/${response.totalElements}"

        return ResponseEntity
            .ok()
            .header("Content-Range", contentRange)
            .header("X-Total-Count", response.totalElements.toString())
            .body(response)
    }

    data class CreateWidgetRequest(
        val name: String,
        val description: String?,
        val value: BigDecimal,
        val category: String,
        val metadata: Map<String, Any>?,
    )
}
