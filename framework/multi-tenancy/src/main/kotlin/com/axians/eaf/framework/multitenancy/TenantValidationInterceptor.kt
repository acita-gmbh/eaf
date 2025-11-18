package com.axians.eaf.framework.multitenancy

import com.axians.eaf.framework.core.exceptions.TenantIsolationException
import io.micrometer.core.instrument.MeterRegistry
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.springframework.stereotype.Component

/**
 * Axon command interceptor for Layer 2 tenant validation (fail-closed).
 *
 * **Layer 2 Defense-in-Depth:**
 * - Layer 1 (TenantContextFilter): Extracts tenant_id from JWT → ThreadLocal
 * - **Layer 2 (This Class):** Validates TenantContext matches command.tenantId
 * - Layer 3 (PostgreSQL RLS): Database-level isolation policies
 *
 * **Validation Logic (Fail-Closed):**
 * 1. Check if command implements TenantAwareCommand interface
 * 2. Get current tenant from TenantContext.getCurrentTenantId() (throws if missing)
 * 3. Verify command.tenantId == currentTenant
 * 4. Reject with TenantIsolationException if mismatch
 *
 * **Security Properties:**
 * - **Fail-closed:** Missing TenantContext always rejects command
 * - **Generic errors:** CWE-209 protection (no tenant ID leakage)
 * - **Metrics:** Emit validation failures for security monitoring
 * - **Selective:** Only validates TenantAwareCommand implementations
 *
 * **Example Validation:**
 * ```kotlin
 * // Tenant A context is set
 * TenantContext.setCurrentTenantId("tenant-a")
 *
 * // Command with matching tenant → SUCCESS
 * CreateWidgetCommand(widgetId = "123", name = "Widget", tenantId = "tenant-a")
 *
 * // Command with different tenant → TenantIsolationException
 * CreateWidgetCommand(widgetId = "456", name = "Widget", tenantId = "tenant-b")
 * ```
 *
 * Epic 4, Story 4.3: AC1, AC2, AC4, AC5, AC7
 *
 * @param meterRegistry Micrometer registry for validation failure metrics
 * @since 1.0.0
 */
@Component
class TenantValidationInterceptor(
    private val meterRegistry: MeterRegistry,
) : MessageHandlerInterceptor<CommandMessage<*>> {
    /**
     * Intercept command execution to validate tenant context.
     *
     * **Execution Order:** Runs BEFORE command handler execution.
     *
     * **Validation Steps:**
     * 1. Extract command payload
     * 2. Check if implements TenantAwareCommand
     * 3. Get current tenant (fail-closed via getCurrentTenantId())
     * 4. Verify command.tenantId matches current tenant
     * 5. Reject or proceed to command handler
     *
     * @param unitOfWork Axon unit of work containing the command
     * @param chain Interceptor chain for proceeding to command handler
     * @return Command handler result if validation passes
     * @throws TenantIsolationException if validation fails (AC4, AC5)
     */
    override fun handle(
        unitOfWork: UnitOfWork<out CommandMessage<*>>,
        chain: InterceptorChain,
    ): Any? {
        val command = unitOfWork.message.payload

        // Only validate commands implementing TenantAwareCommand
        if (command is TenantAwareCommand) {
            validateTenantContext(command)
        }

        // Proceed to command handler if validation passed
        return chain.proceed()
    }

    /**
     * Validate tenant context matches command tenantId (fail-closed).
     *
     * **AC2:** TenantContext.getCurrentTenantId() must match command.tenantId
     * **AC4:** Mismatch → TenantIsolationException with generic error
     * **AC5:** Missing context → TenantIsolationException (getCurrentTenantId() throws)
     * **AC7:** Emit metrics on validation failures
     *
     * @param command Command implementing TenantAwareCommand
     * @throws TenantIsolationException if validation fails
     */
    private fun validateTenantContext(command: TenantAwareCommand) {
        // AC5: Fail-closed - getCurrentTenantId() throws IllegalStateException if context not set
        // We wrap it in TenantIsolationException for consistent security error handling
        val currentTenant =
            @Suppress("SwallowedException") // Intentional: Wrap in security exception for consistent error handling
            try {
                TenantContext.getCurrentTenantId()
            } catch (e: IllegalStateException) {
                // AC5: Missing TenantContext → reject with generic error (fail-closed)
                meterRegistry.counter("tenant.validation.failures").increment()
                throw TenantIsolationException("Tenant context not set")
            }

        // AC2: Validate command.tenantId matches current tenant
        if (command.tenantId != currentTenant) {
            // AC7: Increment validation failure metrics
            meterRegistry.counter("tenant.validation.failures").increment()
            meterRegistry.counter("tenant.mismatch.attempts").increment()

            // AC4: Generic error message (CWE-209 protection)
            throw TenantIsolationException("Access denied: tenant context mismatch")
        }
    }
}
