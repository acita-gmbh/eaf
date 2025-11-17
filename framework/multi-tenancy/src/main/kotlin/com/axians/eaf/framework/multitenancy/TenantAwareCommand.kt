package com.axians.eaf.framework.multitenancy

/**
 * Marker interface for commands that include tenant context.
 *
 * All multi-tenant commands MUST implement this interface to enable Layer 2
 * tenant validation via TenantValidationInterceptor.
 *
 * **Design Rationale:**
 * - Type-safe enforcement of tenantId field presence
 * - Enables compile-time verification that commands include tenant context
 * - Allows interceptor to selectively validate only tenant-aware commands
 * - System commands (non-tenant-aware) bypass validation safely
 *
 * **Usage Pattern:**
 * ```kotlin
 * data class CreateWidgetCommand(
 *     @TargetAggregateIdentifier
 *     val widgetId: UUID,
 *     val name: String,
 *     override val tenantId: String  // Required by interface
 * ) : TenantAwareCommand
 * ```
 *
 * **Layer 2 Validation:**
 * TenantValidationInterceptor validates:
 * ```kotlin
 * if (command is TenantAwareCommand) {
 *     val currentTenant = TenantContext.getCurrentTenantId()
 *     require(command.tenantId == currentTenant) {
 *         "Access denied: tenant context mismatch"
 *     }
 * }
 * ```
 *
 * Epic 4, Story 4.3: AC3
 *
 * @property tenantId Tenant identifier extracted from JWT (Layer 1)
 * @since 1.0.0
 */
interface TenantAwareCommand {
    /**
     * Tenant identifier for this command.
     *
     * **Source:** JWT `tenant_id` claim (set by Layer 1: TenantContextFilter)
     *
     * **Validation:** TenantValidationInterceptor verifies this matches
     * TenantContext.getCurrentTenantId() before command execution.
     */
    val tenantId: String
}
