package com.axians.eaf.api.widget.commands

import org.axonframework.modelling.command.TargetAggregateIdentifier

/**
 * Compensation command that cancels an in-flight widget creation.
 *
 * This command is dispatched from BPMN compensation flows when downstream workflow steps
 * fail (e.g., Ansible playbook execution failures). It implements the compensating action
 * pattern for managing distributed transaction rollback in CQRS/ES systems.
 *
 * **Use Case**: When a widget creation workflow fails after the aggregate has emitted
 * WidgetCreatedEvent but before the workflow completes, this command reverses the creation
 * by transitioning the widget to CANCELLED state.
 *
 * **Security**: Tenant validation is enforced in the aggregate command handler to prevent
 * cross-tenant cancellation attempts (SEC-001 mitigation).
 *
 * Story 6.5: Implement Workflow Error Handling (Compensating Actions)
 *
 * @property widgetId The aggregate identifier of the widget to cancel
 * @property tenantId The tenant identifier for multi-tenant isolation
 * @property cancellationReason Human-readable reason for cancellation (e.g., "Ansible playbook failed")
 * @property operator Optional identifier of the operator/system initiating cancellation
 */
data class CancelWidgetCreationCommand(
    @TargetAggregateIdentifier
    val widgetId: String,
    val tenantId: String,
    val cancellationReason: String,
    val operator: String = "SYSTEM",
)
