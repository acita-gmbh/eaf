package com.axians.eaf.framework.workflow.test

import java.time.Instant

/**
 * Test-only cancellation event for framework compensation testing (Story 6.5).
 *
 * **Framework Test Infrastructure**: This event exists solely for testing the
 * compensating action pattern without depending on products module. It mirrors
 * WidgetCreationCancelledEvent structure but remains in framework test scope.
 *
 * Story 6.5 (Task 4.1) - E2E compensation test infrastructure
 *
 * @property entityId The aggregate identifier of the cancelled test entity
 * @property tenantId The tenant identifier for multi-tenant isolation
 * @property cancellationReason Human-readable reason for cancellation
 * @property operator Operator that initiated cancellation
 * @property cancelledAt Timestamp when cancellation was processed
 */
data class TestEntityCancelledEvent(
    val entityId: String,
    val tenantId: String,
    val cancellationReason: String,
    val operator: String,
    val cancelledAt: Instant = Instant.now(),
)
