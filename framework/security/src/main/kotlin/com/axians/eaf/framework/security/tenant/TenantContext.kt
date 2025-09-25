package com.axians.eaf.framework.security.tenant

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.Deque

/**
 * ThreadLocal stack-based tenant context with memory-leak protection.
 * Provides secure, request-scoped access to tenant information with automatic cleanup.
 */
@Component
class TenantContext(
    private val meterRegistry: MeterRegistry? = null,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(TenantContext::class.java)

        // ThreadLocal stack storage with WeakReference for memory safety
        private val contextStack: ThreadLocal<Deque<WeakReference<String>>> =
            ThreadLocal.withInitial { ArrayDeque() }
    }

    /**
     * Sets the current tenant ID by pushing it onto the ThreadLocal stack.
     * Stores tenant ID as WeakReference to allow garbage collection.
     *
     * @param tenantId The tenant ID to set as current context
     * @throws IllegalArgumentException if tenantId is null or blank
     */
    fun setCurrentTenantId(tenantId: String) {
        require(tenantId.isNotBlank()) { "Tenant ID cannot be null or blank" }

        val stack = contextStack.get()
        stack.push(WeakReference(tenantId))

        logger.debug("Tenant context set: {} (stack depth: {})", tenantId, stack.size)
        meterRegistry?.counter("tenant.context.set")?.increment()
    }

    /**
     * Peeks the current tenant ID from the stack top without removing it.
     * Handles garbage collected WeakReferences by cleaning them up.
     *
     * @return The current tenant ID or null if stack is empty
     */
    fun current(): String? {
        val stack = contextStack.get()

        while (stack.isNotEmpty()) {
            val weakRef = stack.peek()
            val tenantId = weakRef?.get()

            if (tenantId != null) {
                return tenantId
            }

            // Remove garbage collected reference and continue
            stack.poll()
        }

        return null
    }

    /**
     * Gets the current tenant ID, maintaining existing API contract.
     * Implements fail-closed design by throwing exception if no tenant context.
     *
     * @return The current tenant ID
     * @throws IllegalStateException if no tenant context found (fail-closed design)
     */
    fun getCurrentTenantId(): String = current() ?: error("Missing or invalid tenant_id claim in JWT token")

    /**
     * Clears the current tenant by popping from stack.
     * Completely removes ThreadLocal when stack becomes empty to prevent memory leaks.
     */
    fun clearCurrentTenant() {
        val stack = contextStack.get()
        if (stack.isNotEmpty()) {
            stack.poll()
            logger.debug("Tenant context cleared (stack depth: {})", stack.size)
            meterRegistry?.counter("tenant.context.clear")?.increment()
        }

        // Complete ThreadLocal cleanup when stack is empty
        if (stack.isEmpty()) {
            contextStack.remove()
            logger.debug("ThreadLocal removed - complete cleanup")
            meterRegistry?.counter("tenant.context.threadlocal_removed")?.increment()
        }
    }

    /**
     * Production monitoring hook to detect potential memory leaks.
     * Emits metric/alert if stack depth > 0 after request completion.
     *
     * @return Current stack depth (should be 0 after request completion)
     */
    fun getStackDepth(): Int {
        val depth = contextStack.get().size
        if (depth > 0) {
            logger.warn("Potential tenant context leak detected - stack depth: {}", depth)
            meterRegistry
                ?.counter("tenant.context.leak_detected", "depth", depth.toString())
                ?.increment()
        }
        return depth
    }
}
