package com.axians.eaf.framework.cqrs.interceptors

import com.axians.eaf.framework.security.tenant.TenantContext
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Command handler interceptor that sets tenant context for command processing.
 *
 * Extracts tenantId from command payload and populates TenantContext ThreadLocal
 * before command handler execution. Ensures tenant isolation during synchronous
 * command processing.
 *
 * **Execution Flow**:
 * 1. Command dispatched to aggregate
 * 2. **This interceptor executes** (sets TenantContext from command.tenantId)
 * 3. Command handler runs (can access TenantContext.getCurrentTenantId())
 * 4. Aggregate lifecycle completes
 * 5. **This interceptor cleanup** (clears ThreadLocal in finally block)
 *
 * **Fail-Closed Design**: If command lacks tenantId field, interceptor throws
 * IllegalArgumentException to prevent tenant isolation bypass.
 *
 * Story 4.4: Tenant Context Propagation for Commands
 */
@Component
class TenantCommandInterceptor(
    private val tenantContext: TenantContext,
) : MessageHandlerInterceptor<CommandMessage<*>> {
    companion object {
        private val logger = LoggerFactory.getLogger(TenantCommandInterceptor::class.java)
    }

    override fun handle(
        unitOfWork: UnitOfWork<out CommandMessage<*>>,
        interceptorChain: InterceptorChain,
    ): Any {
        val command = unitOfWork.message
        val commandPayload = command.payload

        return try {
            // Extract tenantId from command payload using reflection
            val tenantId = extractTenantId(commandPayload)

            // Set tenant context for this command execution
            tenantContext.setCurrentTenantId(tenantId)

            logger.debug(
                "Tenant context set for command: {} (tenant: {})",
                command.commandName,
                tenantId,
            )

            // Proceed with command handler execution
            interceptorChain.proceed()
        } finally {
            // Guaranteed cleanup to prevent context leakage between commands
            tenantContext.clearCurrentTenant()
            logger.debug("Tenant context cleared after command: {}", command.commandName)
        }
    }

    /**
     * Extracts tenantId from command payload using reflection.
     *
     * Assumes all commands have a 'tenantId' property of type String.
     * This is a framework-level assumption enforced by command design.
     *
     * @param commandPayload The command object
     * @return The tenantId value
     * @throws IllegalArgumentException if tenantId property is missing or invalid
     */
    private fun extractTenantId(commandPayload: Any): String =
        try {
            // TODO: Consider caching field metadata for better performance in high-throughput scenarios
            val tenantIdField = commandPayload::class.java.getDeclaredField("tenantId")
            tenantIdField.isAccessible = true
            val tenantId = tenantIdField.get(commandPayload) as? String

            require(!tenantId.isNullOrBlank()) {
                "Command ${commandPayload::class.simpleName} has null/blank tenantId"
            }

            tenantId
        } catch (e: NoSuchFieldException) {
            throw IllegalArgumentException(
                "Command ${commandPayload::class.simpleName} missing required tenantId field",
                e,
            )
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(
                "Command ${commandPayload::class.simpleName} tenantId field is not String type",
                e,
            )
        }
}
