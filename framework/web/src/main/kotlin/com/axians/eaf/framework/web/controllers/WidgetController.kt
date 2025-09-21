package com.axians.eaf.framework.web.controllers

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import org.axonframework.commandhandling.gateway.CommandGateway
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/widgets")
class WidgetController(
    private val commandGateway: CommandGateway,
) {
    @PostMapping
    fun createWidget(
        @RequestBody request: CreateWidgetRequest,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<Any> {
        val tenantId = extractTenantFromJwt(authorization)
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

    private fun extractTenantFromJwt(authorization: String): String {
        // Simple extraction for testing - will be replaced with proper JWT parsing
        // when security module implementation is complete
        return when {
            authorization.startsWith("Bearer ") -> {
                // Extract tenant from Bearer token or use test default
                "test-tenant"
            }
            else -> "default-tenant"
        }
    }

    data class CreateWidgetRequest(
        val name: String,
        val description: String?,
        val value: BigDecimal,
        val category: String,
        val metadata: Map<String, Any>?,
    )
}
