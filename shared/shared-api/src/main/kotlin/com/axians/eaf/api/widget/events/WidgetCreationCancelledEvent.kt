package com.axians.eaf.api.widget.events

import java.time.Instant

/**
 * Event emitted when widget creation is cancelled due to workflow compensation.
 *
 * This event is part of the compensating action pattern for managing distributed
 * transaction rollback in CQRS/ES systems. When downstream workflow steps fail
 * (e.g., Ansible playbook execution), this event reverses the WidgetCreatedEvent
 * by transitioning the widget to CANCELLED state.
 *
 * **Projection Handling**: Event handlers MUST implement idempotent processing to handle
 * duplicate event replay safely (DATA-001 mitigation). This is critical for read-model
 * consistency during event store recovery scenarios.
 *
 * **Observability**: The cancellationReason and operator fields provide audit trail
 * context for operational debugging and compliance reporting.
 *
 * Story 6.5: Implement Workflow Error Handling (Compensating Actions)
 *
 * @property widgetId The aggregate identifier of the cancelled widget
 * @property tenantId The tenant identifier for multi-tenant isolation
 * @property cancellationReason Human-readable reason for cancellation (e.g., "Ansible playbook failed")
 * @property operator Identifier of the operator/system that initiated cancellation
 * @property cancelledAt Timestamp when the cancellation was processed
 */
data class WidgetCreationCancelledEvent(
    val widgetId: String,
    val tenantId: String,
    val cancellationReason: String,
    val operator: String,
    val cancelledAt: Instant = Instant.now(),
)
