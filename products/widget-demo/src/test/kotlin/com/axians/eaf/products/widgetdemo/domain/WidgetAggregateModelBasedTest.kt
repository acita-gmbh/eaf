package com.axians.eaf.products.widgetdemo.domain

import com.axians.eaf.api.widget.commands.CancelWidgetCreationCommand
import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.api.widget.commands.UpdateWidgetCommand
import com.axians.eaf.api.widget.events.WidgetCreatedEvent
import com.axians.eaf.api.widget.events.WidgetCreationCancelledEvent
import com.axians.eaf.api.widget.events.WidgetUpdatedEvent
import com.axians.eaf.framework.security.tenant.TenantContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import org.axonframework.test.aggregate.AggregateTestFixture
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Model-Based Testing for Widget aggregate state machine.
 * Story 8.6: Advanced testing - validates aggregate lifecycle and state transitions.
 *
 * MODEL-BASED TESTING:
 * - Generates random sequences of valid commands
 * - Verifies state transitions are consistent
 * - Validates invariants hold across all paths
 * - Tests that illegal transitions are rejected
 *
 * STATE MACHINE (simplified):
 * - Create → ACTIVE
 * - Update (only on ACTIVE) → ACTIVE
 * - Cancel → CANCELLED (idempotent)
 * - Invariant: Cannot update CANCELLED widgets
 *
 * EXECUTION: Fast unit tests (no Testcontainers, uses Axon test fixture).
 * PERFORMANCE: Milliseconds per sequence, works with pitest.
 */
class WidgetAggregateModelBasedTest :
    FunSpec({

        context("8.6-MBT-001: Widget lifecycle state machine") {
            test("should handle creation and update sequence") {
                checkAll(500, validWidgetCommandSequenceArb()) { (createCmd, updates) ->
                    val fixture = AggregateTestFixture(Widget::class.java)

                    // Mock tenant context for the test
                    TenantContext().setCurrentTenantId(createCmd.tenantId)

                    try {
                        // 1. CREATE widget
                        fixture
                            .givenNoPriorActivity()
                            .`when`(createCmd)
                            .expectSuccessfulHandlerExecution()
                            .expectEvents(
                                WidgetCreatedEvent(
                                    widgetId = createCmd.widgetId,
                                    tenantId = createCmd.tenantId,
                                    name = createCmd.name,
                                    description = createCmd.description,
                                    value = createCmd.value,
                                    category = createCmd.category,
                                    metadata = createCmd.metadata,
                                    createdAt = Instant.now(),
                                ),
                            )

                        // 2. APPLY update sequence
                        updates.forEach { updateCmd ->
                            fixture
                                .`when`(updateCmd)
                                .expectSuccessfulHandlerExecution()
                        }
                    } finally {
                        TenantContext().clearCurrentTenant()
                    }
                }
            }

            test("should reject update on cancelled widget") {
                checkAll(100, validCreateCommandArb()) { createCmd ->
                    val fixture = AggregateTestFixture(Widget::class.java)

                    TenantContext().setCurrentTenantId(createCmd.tenantId)

                    try {
                        val cancelCmd =
                            CancelWidgetCreationCommand(
                                widgetId = createCmd.widgetId,
                                tenantId = createCmd.tenantId,
                                cancellationReason = "Test cancellation",
                                operator = "test-user",
                            )

                        val updateCmd =
                            UpdateWidgetCommand(
                                widgetId = createCmd.widgetId,
                                tenantId = createCmd.tenantId,
                                name = "Updated Name",
                                description = null,
                                value = null,
                                category = null,
                                metadata = null,
                            )

                        // Create → Cancel → Update (should fail)
                        fixture
                            .givenCommands(createCmd, cancelCmd)
                            .`when`(updateCmd)
                            .expectException(WidgetValidationException::class.java)
                    } finally {
                        TenantContext().clearCurrentTenant()
                    }
                }
            }

            test("cancellation should be idempotent") {
                checkAll(100, validCreateCommandArb()) { createCmd ->
                    val fixture = AggregateTestFixture(Widget::class.java)

                    TenantContext().setCurrentTenantId(createCmd.tenantId)

                    try {
                        val cancelCmd =
                            CancelWidgetCreationCommand(
                                widgetId = createCmd.widgetId,
                                tenantId = createCmd.tenantId,
                                cancellationReason = "Test",
                                operator = "test",
                            )

                        // Cancel twice - second should succeed without event
                        fixture
                            .givenCommands(createCmd, cancelCmd)
                            .`when`(cancelCmd)
                            .expectSuccessfulHandlerExecution()
                            .expectNoEvents() // Idempotent - no duplicate event
                    } finally {
                        TenantContext().clearCurrentTenant()
                    }
                }
            }
        }
    })

// ============================================================================
// Widget Command Generators for Model-Based Testing
// ============================================================================

private fun validCreateCommandArb(): Arb<CreateWidgetCommand> {
    val nameArb = Arb.string(2..100).map { "Widget-$it" }
    val categoryArb = Arb.of("ELECTRONICS", "SOFTWARE", "HARDWARE", "SERVICE")
    val valueArb = Arb.int(1..10000).map { BigDecimal(it) }

    return Arb.bind(nameArb, categoryArb, valueArb) { name, category, value ->
        CreateWidgetCommand(
            widgetId = UUID.randomUUID().toString(),
            tenantId = "test-tenant",
            name = name,
            description = "Test widget",
            value = value,
            category = category,
            metadata = emptyMap(),
        )
    }
}

private fun validUpdateCommandArb(
    widgetId: String,
    tenantId: String,
): Arb<UpdateWidgetCommand> {
    val nameArb = Arb.string(2..100).map { "Updated-$it" }
    return nameArb.map { name ->
        UpdateWidgetCommand(
            widgetId = widgetId,
            tenantId = tenantId,
            name = name,
            description = null,
            value = null,
            category = null,
            metadata = null,
        )
    }
}

private fun validWidgetCommandSequenceArb(): Arb<Pair<CreateWidgetCommand, List<UpdateWidgetCommand>>> =
    validCreateCommandArb().flatMap { createCmd ->
        Arb.list(validUpdateCommandArb(createCmd.widgetId, createCmd.tenantId), 0..3).map { updates ->
            Pair(createCmd, updates)
        }
    }
