package com.axians.eaf.products.widgetdemo.controllers

import com.axians.eaf.products.widgetdemo.domain.WidgetError
import com.axians.eaf.products.widgetdemo.domain.WidgetValidationException
import org.axonframework.commandhandling.CommandExecutionException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

/**
 * Maps widget domain exceptions to RFC 7807 Problem Details responses.
 *
 * Ensures REST endpoints surface meaningful validation feedback instead of
 * propagating Axon command exceptions as 500 errors.
 */
@RestControllerAdvice(assignableTypes = [WidgetController::class])
class WidgetExceptionHandler {
    @ExceptionHandler(WidgetValidationException::class)
    fun handleWidgetValidation(exception: WidgetValidationException): ResponseEntity<ProblemDetail> = toProblemResponse(exception.error)

    @ExceptionHandler(CommandExecutionException::class)
    fun handleCommandExecution(exception: CommandExecutionException): ResponseEntity<ProblemDetail> =
        when (val cause = exception.cause) {
            is WidgetValidationException -> toProblemResponse(cause.error)
            else -> throw exception
        }

    private fun toProblemResponse(error: WidgetError): ResponseEntity<ProblemDetail> {
        val status =
            when (error) {
                is WidgetError.ValidationError -> HttpStatus.BAD_REQUEST
                is WidgetError.BusinessRuleViolation -> HttpStatus.BAD_REQUEST
                is WidgetError.TenantIsolationViolation -> HttpStatus.FORBIDDEN
                is WidgetError.NotFound -> HttpStatus.NOT_FOUND
                is WidgetError.Conflict -> HttpStatus.CONFLICT
            }
        val detail =
            when (error) {
                is WidgetError.ValidationError ->
                    "Validation failed for field \"${error.field}\" with constraint \"${error.constraint}\"."
                is WidgetError.BusinessRuleViolation -> "Business rule violated: ${error.rule} (${error.reason})."
                is WidgetError.TenantIsolationViolation -> "Access denied: tenant context mismatch."
                is WidgetError.NotFound -> "Widget with id \"${error.widgetId}\" not found."
                is WidgetError.Conflict -> "Request conflict: ${error.reason}."
            }

        val problem =
            ProblemDetail
                .forStatusAndDetail(status, detail)
                .apply {
                    title =
                        when (error) {
                            is WidgetError.ValidationError,
                            is WidgetError.BusinessRuleViolation,
                            -> "Widget Creation Failed"
                            is WidgetError.TenantIsolationViolation -> "Tenant Validation Failed"
                            is WidgetError.NotFound -> "Widget Not Found"
                            is WidgetError.Conflict -> "Widget Conflict"
                        }
                    type =
                        when (error) {
                            is WidgetError.ValidationError -> URI.create("/problems/validation-error")
                            is WidgetError.BusinessRuleViolation -> URI.create("/problems/business-rule-violation")
                            is WidgetError.TenantIsolationViolation -> URI.create("/problems/tenant-isolation")
                            is WidgetError.NotFound -> URI.create("/problems/widget-not-found")
                            is WidgetError.Conflict -> URI.create("/problems/widget-conflict")
                        }
                    when (error) {
                        is WidgetError.ValidationError -> {
                            setProperty("field", error.field)
                            setProperty("constraint", error.constraint)
                            setProperty("invalidValue", error.invalidValue)
                        }
                        is WidgetError.BusinessRuleViolation -> {
                            setProperty("rule", error.rule)
                            setProperty("reason", error.reason)
                        }
                        is WidgetError.TenantIsolationViolation -> {
                            setProperty("reason", "tenant_mismatch")
                        }
                        is WidgetError.NotFound -> setProperty("widgetId", error.widgetId)
                        is WidgetError.Conflict -> setProperty("reason", error.reason)
                    }
                }

        return ResponseEntity.status(status).body(problem)
    }
}
