package com.axians.eaf.framework.widget.query

import com.axians.eaf.api.widget.queries.FindWidgetByIdQuery
import com.axians.eaf.api.widget.queries.FindWidgetsQuery
import com.axians.eaf.framework.persistence.entities.WidgetProjection
import com.axians.eaf.framework.widget.infrastructure.NullableWidgetProjectionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import java.time.Instant

/**
 * Unit tests for WidgetQueryHandler.
 * Tests query handling functionality and data transformation.
 * Uses Nullable Design Pattern instead of mocking frameworks.
 */
class WidgetQueryHandlerTest :
    FunSpec({

        test("2.4-UNIT-001: FindWidgetByIdQuery returns widget response when found") {
            // Given - Using Nullable Design Pattern
            val widgetId = "widget-123"
            val tenantId = "tenant-456"
            val projection =
                WidgetProjection(
                    widgetId = widgetId,
                    tenantId = tenantId,
                    name = "Test Widget",
                    description = "Test Description",
                    value = BigDecimal("100.50"),
                    category = "TEST",
                    metadata = """{"key": "value"}""",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            val repository = NullableWidgetProjectionRepository.createNull()
                .withProjection(projection)
            val objectMapper = ObjectMapper()
            val handler = WidgetQueryHandler(repository, objectMapper)

            // When
            val result = handler.handle(FindWidgetByIdQuery(widgetId, tenantId))

            // Then
            result shouldNotBe null
            result!!.id shouldBe widgetId
            result.name shouldBe "Test Widget"
            result.description shouldBe "Test Description"
            result.value shouldBe BigDecimal("100.50")
            result.category shouldBe "TEST"
            result.metadata shouldNotBe null
            result.metadata!!["key"] shouldBe "value"
        }

        test("2.4-UNIT-002: FindWidgetByIdQuery returns null when not found") {
            // Given - Empty repository using Nullable Design Pattern
            val repository = NullableWidgetProjectionRepository.createNull()
            val objectMapper = ObjectMapper()
            val handler = WidgetQueryHandler(repository, objectMapper)

            val widgetId = "nonexistent"
            val tenantId = "tenant-456"

            // When
            val result = handler.handle(FindWidgetByIdQuery(widgetId, tenantId))

            // Then
            result shouldBe null
        }

        test("2.4-UNIT-003: FindWidgetsQuery handles pagination correctly") {
            // Given - Repository with test data using Nullable Design Pattern
            val tenantId = "tenant-456"
            val projection1 = WidgetProjection(
                widgetId = "widget-1",
                tenantId = tenantId,
                name = "Widget 1",
                description = null,
                value = BigDecimal("100.00"),
                category = "TEST",
                metadata = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
            val projection2 = WidgetProjection(
                widgetId = "widget-2",
                tenantId = tenantId,
                name = "Widget 2",
                description = null,
                value = BigDecimal("200.00"),
                category = "TEST",
                metadata = null,
                createdAt = Instant.now().minusSeconds(10),
                updatedAt = Instant.now(),
            )

            val repository = NullableWidgetProjectionRepository.createNull()
                .withProjection(projection1)
                .withProjection(projection2)
            val objectMapper = ObjectMapper()
            val handler = WidgetQueryHandler(repository, objectMapper)

            // When
            val result = handler.handle(FindWidgetsQuery(tenantId = tenantId, page = 0, size = 1))

            // Then
            result shouldNotBe null
            result.content.size shouldBe 1
            result.totalElements shouldBe 2
            result.totalPages shouldBe 2
            result.number shouldBe 0
            result.size shouldBe 1
        }

        test("2.4-UNIT-004: WidgetResponse excludes sensitive fields") {
            // Given - Repository with test data using Nullable Design Pattern
            val widgetId = "widget-123"
            val tenantId = "tenant-456"
            val projection =
                WidgetProjection(
                    widgetId = widgetId,
                    tenantId = tenantId,
                    name = "Security Test Widget",
                    description = "Testing field exclusion",
                    value = BigDecimal("999.99"),
                    category = "SECURITY",
                    metadata = null,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            val repository = NullableWidgetProjectionRepository.createNull()
                .withProjection(projection)
            val objectMapper = ObjectMapper().registerModule(JavaTimeModule())
            val handler = WidgetQueryHandler(repository, objectMapper)

            // When
            val result = handler.handle(FindWidgetByIdQuery(widgetId, tenantId))

            // Then - Convert to JSON to verify excluded fields
            val json = objectMapper.writeValueAsString(result)
            json.contains("tenantId") shouldBe false
            json.contains("updatedAt") shouldBe false
            json.contains("createdAt") shouldBe true // This should be included
        }

        test("2.4-UNIT-005: Query validation ensures required parameters") {
            // Given - Invalid query parameters should fail validation

            // When/Then - Test query parameter validation
            val validQuery =
                FindWidgetsQuery(
                    tenantId = "valid-tenant",
                    page = 0,
                    size = 20,
                )
            validQuery.tenantId shouldBe "valid-tenant"

            // Test validation constraints
            try {
                FindWidgetsQuery(tenantId = "", page = 0, size = 20)
                throw AssertionError("Should have failed validation")
            } catch (e: IllegalArgumentException) {
                e.message shouldBe "Tenant ID must not be blank"
            }

            try {
                FindWidgetsQuery(tenantId = "valid", page = -1, size = 20)
                throw AssertionError("Should have failed validation")
            } catch (e: IllegalArgumentException) {
                e.message shouldBe "Page number must be non-negative"
            }

            try {
                FindWidgetsQuery(tenantId = "valid", page = 0, size = 101)
                throw AssertionError("Should have failed validation")
            } catch (e: IllegalArgumentException) {
                e.message shouldBe "Page size must be between 1 and 100"
            }
        }
    })