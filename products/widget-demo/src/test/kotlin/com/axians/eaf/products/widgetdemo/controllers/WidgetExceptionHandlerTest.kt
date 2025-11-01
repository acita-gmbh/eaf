package com.axians.eaf.products.widgetdemo.controllers

import com.axians.eaf.products.widgetdemo.domain.WidgetError
import com.axians.eaf.products.widgetdemo.domain.WidgetValidationException
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.axonframework.commandhandling.CommandExecutionException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import java.math.BigDecimal

/**
 * Unit tests for WidgetExceptionHandler RFC 7807 Problem Details mapping.
 *
 * Tests verify that domain errors are correctly mapped to HTTP responses
 * with appropriate status codes, problem details, and properties.
 *
 * Story 8.5: Architectural Patterns Alignment
 */
class WidgetExceptionHandlerTest :
    FunSpec({
        val handler = WidgetExceptionHandler()

        context("WidgetValidationException handling") {
            test("8.5-UNIT-EH-001: should map ValidationError to 400 Bad Request with field details") {
                // Given: ValidationError for empty name
                val error =
                    WidgetError.ValidationError(
                        field = "name",
                        constraint = "length",
                        invalidValue = "",
                    )
                val exception = WidgetValidationException(error)

                // When: Handling the exception
                val response = handler.handleWidgetValidation(exception)

                // Then: Should return 400 with validation error details
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                val problem = response.body
                problem shouldNotBe null
                problem!!.status shouldBe 400
                problem.title shouldBe "Widget Creation Failed"
                problem.type.toString() shouldBe "/problems/validation-error"
                problem.detail shouldBe "Validation failed for field \"name\" with constraint \"length\"."
                problem.properties!!["field"] shouldBe "name"
                problem.properties!!["constraint"] shouldBe "length"
                problem.properties!!["invalidValue"] shouldBe ""
            }

            test("8.5-UNIT-EH-002: should map ValidationError with numeric invalidValue") {
                // Given: ValidationError for out-of-range value
                val error =
                    WidgetError.ValidationError(
                        field = "value",
                        constraint = "range",
                        invalidValue = BigDecimal("-50.00"),
                    )
                val exception = WidgetValidationException(error)

                // When: Handling the exception
                val response = handler.handleWidgetValidation(exception)

                // Then: Should include numeric value in properties
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                val problem = response.body!!
                problem.properties!!["field"] shouldBe "value"
                problem.properties!!["constraint"] shouldBe "range"
                problem.properties!!["invalidValue"] shouldBe BigDecimal("-50.00")
            }

            test("8.5-UNIT-EH-003: should map BusinessRuleViolation to 400 Bad Request") {
                // Given: BusinessRuleViolation for inactive widget update
                val error =
                    WidgetError.BusinessRuleViolation(
                        rule = "widget.must.be.active",
                        reason = "Only active widgets can be updated",
                    )
                val exception = WidgetValidationException(error)

                // When: Handling the exception
                val response = handler.handleWidgetValidation(exception)

                // Then: Should return 400 with business rule details
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                val problem = response.body!!
                problem.title shouldBe "Widget Creation Failed"
                problem.type.toString() shouldBe "/problems/business-rule-violation"
                problem.detail shouldBe "Business rule violated: widget.must.be.active (Only active widgets can be updated)."
                problem.properties!!["rule"] shouldBe "widget.must.be.active"
                problem.properties!!["reason"] shouldBe "Only active widgets can be updated"
            }

            test("8.5-UNIT-EH-004: should map TenantIsolationViolation to 403 Forbidden") {
                // Given: TenantIsolationViolation (no tenant IDs exposed)
                val error = WidgetError.TenantIsolationViolation
                val exception = WidgetValidationException(error)

                // When: Handling the exception
                val response = handler.handleWidgetValidation(exception)

                // Then: Should return 403 with generic message (CWE-209 protection)
                response.statusCode shouldBe HttpStatus.FORBIDDEN
                val problem = response.body!!
                problem.status shouldBe 403
                problem.title shouldBe "Tenant Validation Failed"
                problem.type.toString() shouldBe "/problems/tenant-isolation"
                problem.detail shouldBe "Access denied: tenant context mismatch."
                problem.properties!!["reason"] shouldBe "tenant_mismatch"
                // SECURITY: Verify no tenant IDs in properties
                problem.properties!!.keys shouldBe setOf("reason")
            }

            test("8.5-UNIT-EH-005: should map NotFound to 404 Not Found") {
                // Given: NotFound error
                val widgetId = "550e8400-e29b-41d4-a716-446655440000"
                val error = WidgetError.NotFound(widgetId = widgetId)
                val exception = WidgetValidationException(error)

                // When: Handling the exception
                val response = handler.handleWidgetValidation(exception)

                // Then: Should return 404
                response.statusCode shouldBe HttpStatus.NOT_FOUND
                val problem = response.body!!
                problem.title shouldBe "Widget Not Found"
                problem.type.toString() shouldBe "/problems/widget-not-found"
                problem.detail shouldBe "Widget with id \"$widgetId\" not found."
                problem.properties!!["widgetId"] shouldBe widgetId
            }

            test("8.5-UNIT-EH-006: should map Conflict to 409 Conflict") {
                // Given: Conflict error
                val error =
                    WidgetError.Conflict(
                        reason = "Widget with same name already exists",
                    )
                val exception = WidgetValidationException(error)

                // When: Handling the exception
                val response = handler.handleWidgetValidation(exception)

                // Then: Should return 409
                response.statusCode shouldBe HttpStatus.CONFLICT
                val problem = response.body!!
                problem.title shouldBe "Widget Conflict"
                problem.type.toString() shouldBe "/problems/widget-conflict"
                problem.detail shouldBe "Request conflict: Widget with same name already exists."
                problem.properties!!["reason"] shouldBe "Widget with same name already exists"
            }
        }

        context("CommandExecutionException handling") {
            test("8.5-UNIT-EH-007: should unwrap WidgetValidationException from CommandExecutionException") {
                // Given: CommandExecutionException wrapping WidgetValidationException
                val domainError =
                    WidgetError.ValidationError(
                        field = "category",
                        constraint = "pattern",
                        invalidValue = "invalid-category",
                    )
                val validationException = WidgetValidationException(domainError)
                val commandException = CommandExecutionException("Command failed", validationException)

                // When: Handling the command exception
                val response = handler.handleCommandExecution(commandException)

                // Then: Should unwrap and map the validation error
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                val problem = response.body!!
                problem.title shouldBe "Widget Creation Failed"
                problem.type.toString() shouldBe "/problems/validation-error"
                problem.properties!!["field"] shouldBe "category"
                problem.properties!!["constraint"] shouldBe "pattern"
                problem.properties!!["invalidValue"] shouldBe "invalid-category"
            }

            test("8.5-UNIT-EH-008: should rethrow CommandExecutionException when cause is not WidgetValidationException") {
                // Given: CommandExecutionException with non-widget cause
                val commandException =
                    CommandExecutionException(
                        "Command failed",
                        IllegalArgumentException("Generic error"),
                    )

                // When/Then: Should rethrow the exception
                try {
                    handler.handleCommandExecution(commandException)
                    error("Should have thrown exception")
                } catch (e: CommandExecutionException) {
                    e shouldBe commandException
                }
            }

            test("8.5-UNIT-EH-009: should rethrow CommandExecutionException when cause is null") {
                // Given: CommandExecutionException with null cause
                val commandException = CommandExecutionException("Command failed", null)

                // When/Then: Should rethrow the exception
                try {
                    handler.handleCommandExecution(commandException)
                    error("Should have thrown exception")
                } catch (e: CommandExecutionException) {
                    e shouldBe commandException
                }
            }
        }

        context("RFC 7807 Problem Details structure validation") {
            test("8.5-UNIT-EH-010: should include all required RFC 7807 fields") {
                // Given: Any widget error
                val error =
                    WidgetError.ValidationError(
                        field = "name",
                        constraint = "pattern",
                        invalidValue = "test@invalid",
                    )
                val exception = WidgetValidationException(error)

                // When: Mapping to problem response
                val response = handler.handleWidgetValidation(exception)
                val problem = response.body!!

                // Then: Should include all RFC 7807 required fields
                problem.type shouldNotBe null // Required
                problem.title shouldNotBe null // Required
                problem.status shouldNotBe null // Required
                problem.detail shouldNotBe null // Required
                problem.instance // Optional - may be null
            }

            test("8.5-UNIT-EH-011: should use consistent problem type URIs") {
                // Given: All widget error types
                val errors =
                    listOf(
                        WidgetError.ValidationError("field", "constraint", "value") to "/problems/validation-error",
                        WidgetError.BusinessRuleViolation("rule", "reason") to "/problems/business-rule-violation",
                        WidgetError.TenantIsolationViolation to "/problems/tenant-isolation",
                        WidgetError.NotFound("widget-id") to "/problems/widget-not-found",
                        WidgetError.Conflict("conflict reason") to "/problems/widget-conflict",
                    )

                // When/Then: Each should have correct problem type URI
                errors.forEach { (error, expectedType) ->
                    val response = handler.handleWidgetValidation(WidgetValidationException(error))
                    response.body!!.type.toString() shouldBe expectedType
                }
            }
        }

        context("Security: Tenant ID protection (CWE-209)") {
            test("8.5-UNIT-EH-012: TenantIsolationViolation should never expose tenant IDs") {
                // Given: TenantIsolationViolation error (data object singleton)
                val error = WidgetError.TenantIsolationViolation
                val exception = WidgetValidationException(error)

                // When: Mapping to problem response
                val response = handler.handleWidgetValidation(exception)
                val problem = response.body!!

                // Then: Should contain only generic properties, NO tenant IDs
                problem.detail shouldBe "Access denied: tenant context mismatch."
                problem.properties!!.keys shouldBe setOf("reason")
                problem.properties!!["reason"] shouldBe "tenant_mismatch"

                // Verify no properties contain "tenant" in key name (strict check)
                problem.properties!!.keys.none { it.contains("tenant", ignoreCase = true) } shouldBe true

                // DEFENSE-IN-DEPTH: Also check JSON serialization for UUID patterns (Copilot suggestion)
                // This validates that serialization doesn't accidentally expose tenant IDs even if
                // ProblemDetail's internal representation changes
                val jsonString =
                    com.fasterxml.jackson.databind
                        .ObjectMapper()
                        .writeValueAsString(problem)
                val uuidPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                uuidPattern.containsMatchIn(jsonString) shouldBe false
            }

            test("8.5-UNIT-EH-013: TenantIsolationViolation toString should return generic message") {
                // Given: TenantIsolationViolation (data object singleton)
                val error = WidgetError.TenantIsolationViolation

                // When: Converting to string
                val message = error.toString()

                // Then: Should be generic (no tenant IDs)
                message shouldBe "Access denied: tenant context mismatch"
            }
        }

        context("Error type coverage") {
            test("8.5-UNIT-EH-014: should handle all WidgetError sealed class variants") {
                // Given: All WidgetError types
                val errors: List<Pair<WidgetError, HttpStatus>> =
                    listOf(
                        WidgetError.ValidationError("name", "length", "") to HttpStatus.BAD_REQUEST,
                        WidgetError.BusinessRuleViolation("rule", "reason") to HttpStatus.BAD_REQUEST,
                        WidgetError.TenantIsolationViolation to HttpStatus.FORBIDDEN,
                        WidgetError.NotFound("id") to HttpStatus.NOT_FOUND,
                        WidgetError.Conflict("reason") to HttpStatus.CONFLICT,
                    )

                // When/Then: Each error type should map to correct HTTP status
                errors.forEach { (error, expectedStatus) ->
                    val response = handler.handleWidgetValidation(WidgetValidationException(error))
                    response.statusCode shouldBe expectedStatus
                    response.body shouldNotBe null
                    response.body!!.shouldBeInstanceOf<ProblemDetail>()
                }
            }
        }
    })
