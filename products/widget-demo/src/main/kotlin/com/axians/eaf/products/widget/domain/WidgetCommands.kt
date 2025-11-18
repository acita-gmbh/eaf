package com.axians.eaf.products.widget.domain

import com.axians.eaf.framework.multitenancy.TenantAwareCommand
import org.axonframework.modelling.command.TargetAggregateIdentifier

/**
 * Command to create a new Widget aggregate.
 *
 * **Multi-Tenancy (Story 4.6):**
 * - Implements TenantAwareCommand for Layer 2 tenant validation
 * - tenantId extracted from JWT via TenantContextFilter (Layer 1)
 * - TenantValidationInterceptor validates tenantId matches TenantContext
 *
 * @property widgetId Unique identifier for the widget to create
 * @property name Display name for the widget (must not be blank)
 * @property tenantId Tenant identifier from authenticated JWT (Layer 1)
 */
data class CreateWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId,
    val name: String,
    override val tenantId: String,
) : TenantAwareCommand

/**
 * Command to update an existing Widget's name.
 *
 * **Multi-Tenancy (Story 4.6):**
 * - Implements TenantAwareCommand for Layer 2 tenant validation
 *
 * @property widgetId Identifier of the widget to update
 * @property name New display name for the widget
 * @property tenantId Tenant identifier from authenticated JWT
 */
data class UpdateWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId,
    val name: String,
    override val tenantId: String,
) : TenantAwareCommand

/**
 * Command to publish a Widget, making it publicly available.
 *
 * **Multi-Tenancy (Story 4.6):**
 * - Implements TenantAwareCommand for Layer 2 tenant validation
 *
 * @property widgetId Identifier of the widget to publish
 * @property tenantId Tenant identifier from authenticated JWT
 */
data class PublishWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId,
    override val tenantId: String,
) : TenantAwareCommand
