package com.axians.eaf.framework.web.controllers

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import org.axonframework.commandhandling.gateway.CommandGateway
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
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

        return try {
            commandGateway.sendAndWait<String>(command, 5, TimeUnit.SECONDS)
            ResponseEntity
                .created(URI.create("/widgets/$widgetId"))
                .body(mapOf("id" to widgetId, "status" to "created"))
        } catch (e: IllegalArgumentException) {
            val problemDetail =
                ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    e.message ?: "Validation failed",
                )
            problemDetail.title = "Widget Creation Failed"
            problemDetail.type = URI.create("/problems/validation-error")
            problemDetail.setProperty("widgetId", widgetId)
            problemDetail.setProperty("tenantId", tenantId)
            ResponseEntity.badRequest().body(problemDetail)
        } catch (e: org.axonframework.commandhandling.CommandExecutionException) {
            val problemDetail =
                ProblemDetail.forStatusAndDetail(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Command execution failed: ${e.message}",
                )
            problemDetail.title = "Widget Creation Error"
            problemDetail.type = URI.create("/problems/command-error")
            problemDetail.setProperty("widgetId", widgetId)
            problemDetail.setProperty("error", e.javaClass.simpleName)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail)
        } catch (e: java.util.concurrent.TimeoutException) {
            val problemDetail =
                ProblemDetail.forStatusAndDetail(
                    HttpStatus.REQUEST_TIMEOUT,
                    "Command processing timed out: ${e.message}",
                )
            problemDetail.title = "Widget Creation Timeout"
            problemDetail.type = URI.create("/problems/timeout-error")
            problemDetail.setProperty("widgetId", widgetId)
            problemDetail.setProperty("timeout", "5 seconds")
            problemDetail.setProperty("cause", e.message ?: "Timeout after 5 seconds")
            ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(problemDetail)
        }
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
