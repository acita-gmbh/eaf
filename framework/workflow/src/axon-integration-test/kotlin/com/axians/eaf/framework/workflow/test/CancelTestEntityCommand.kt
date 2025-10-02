package com.axians.eaf.framework.workflow.test

import org.axonframework.modelling.command.TargetAggregateIdentifier

/**
 * Test-only cancellation command for framework compensation testing (Story 6.5).
 *
 * **Framework Test Infrastructure**: This command exists solely for testing the
 * compensating action pattern without depending on products module. It mirrors
 * CancelWidgetCreationCommand structure but remains in framework test scope.
 *
 * **Architectural Purity**: Framework tests must NOT depend on products. This
 * test command enables E2E compensation flow validation using TestEntityAggregate.
 *
 * Story 6.5 (Task 4.1) - E2E compensation test infrastructure
 *
 * @property entityId The aggregate identifier of the test entity to cancel
 * @property tenantId The tenant identifier for multi-tenant isolation
 * @property cancellationReason Human-readable reason for cancellation
 * @property operator Operator initiating cancellation (defaults to "SYSTEM")
 */
data class CancelTestEntityCommand(
    @TargetAggregateIdentifier
    val entityId: String,
    val tenantId: String,
    val cancellationReason: String,
    val operator: String = "SYSTEM",
)
