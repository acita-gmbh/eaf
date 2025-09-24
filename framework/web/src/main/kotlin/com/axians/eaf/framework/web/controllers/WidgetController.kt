package com.axians.eaf.framework.web.controllers

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.api.widget.dto.WidgetResponse
import com.axians.eaf.api.widget.queries.FindWidgetByIdQuery
import com.axians.eaf.api.widget.queries.FindWidgetsQuery
import com.axians.eaf.framework.security.tenant.TenantContext
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.queryhandling.QueryGateway
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
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
class WidgetController(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway,
) {
    @PostMapping
    fun createWidget(
        @RequestBody request: CreateWidgetRequest,
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.getCurrentTenantId()
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

        // Exception handling now delegated to GlobalExceptionHandler
        commandGateway.sendAndWait<String>(command, 5, TimeUnit.SECONDS)
        return ResponseEntity
            .created(URI.create("/widgets/$widgetId"))
            .body(mapOf("id" to widgetId, "status" to "created"))
    }

    @GetMapping("/{id}")
    fun getWidget(
        @PathVariable("id") widgetId: String,
    ): ResponseEntity<WidgetResponse> {
        val tenantId = TenantContext.getCurrentTenantId()
        val query = FindWidgetByIdQuery(widgetId, tenantId)

        val response = queryGateway.query(query, WidgetResponse::class.java).get(5, TimeUnit.SECONDS)

        return if (response != null) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping
    fun getWidgets(
        @RequestParam params: Map<String, String>,
    ): ResponseEntity<Page<WidgetResponse>> {
        val tenantId = TenantContext.getCurrentTenantId()

        // Extract and validate parameters from map
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

        @Suppress("UNCHECKED_CAST")
        val response =
            queryGateway
                .query(query, Page::class.java)
                .get(5, TimeUnit.SECONDS) as Page<WidgetResponse>

        return ResponseEntity.ok(response)
    }

    data class CreateWidgetRequest(
        val name: String,
        val description: String?,
        val value: BigDecimal,
        val category: String,
        val metadata: Map<String, Any>?,
    )
}
