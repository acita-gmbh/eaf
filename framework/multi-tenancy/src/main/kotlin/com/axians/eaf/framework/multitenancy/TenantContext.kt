package com.axians.eaf.framework.multitenancy

import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.Deque

/**
 * Thread-local tenant context management with stack-based nested context support.
 *
 * **Design Principles:**
 * - **Fail-closed**: `getCurrentTenantId()` throws exception if context missing
 * - **Nullable**: `current()` returns null if context missing (defensive checks)
 * - **Stack-based**: Supports nested contexts for testing scenarios
 * - **Memory-safe**: Uses WeakReference to prevent ThreadLocal leaks
 * - **Thread-isolated**: Each thread maintains its own context stack
 *
 * **Access Patterns:**
 * ```kotlin
 * // Fail-closed (use in command handlers, service methods)
 * val tenantId = TenantContext.getCurrentTenantId()
 *
 * // Nullable (use for defensive checks)
 * val tenantId = TenantContext.current()
 * if (tenantId != null) { /* ... */ }
 * ```
 *
 * **Nested Context Example:**
 * ```kotlin
 * TenantContext.setCurrentTenantId("tenant-a")  // Push tenant-a
 * TenantContext.setCurrentTenantId("tenant-b")  // Push tenant-b (nested)
 * TenantContext.getCurrentTenantId()             // Returns "tenant-b"
 * TenantContext.clearCurrentTenant()             // Pop tenant-b
 * TenantContext.getCurrentTenantId()             // Returns "tenant-a"
 * TenantContext.clearCurrentTenant()             // Pop tenant-a
 * ```
 *
 * Epic 4, Story 4.1: AC3, AC4, AC5
 *
 * @since 1.0.0
 */
object TenantContext {
    /**
     * ThreadLocal storage with WeakReference for memory safety.
     * Stores a stack (Deque) of tenant IDs to support nested contexts.
     *
     * AC5: WeakReference prevents ThreadLocal memory leaks when threads are pooled.
     */
    private val contextHolder: ThreadLocal<WeakReference<Deque<String>>> =
        ThreadLocal<WeakReference<Deque<String>>>()

    /**
     * Get the current tenant ID stack, creating it if necessary.
     *
     * @return The tenant ID stack for the current thread
     */
    private fun getContextStack(): Deque<String> {
        val weakRef = contextHolder.get()
        val stack = weakRef?.get()

        return if (stack == null) {
            // Create new stack if missing or GC'd
            val newStack = ArrayDeque<String>()
            contextHolder.set(WeakReference(newStack))
            newStack
        } else {
            stack
        }
    }

    /**
     * Get the current tenant ID (fail-closed behavior).
     *
     * **Usage:** Command handlers, service methods requiring tenant context.
     *
     * @return The current tenant ID
     * @throws IllegalStateException if tenant context is not set
     */
    fun getCurrentTenantId(): String {
        val stack = getContextStack()
        return stack.peekLast()
            ?: error("Tenant context not set for current thread")
    }

    /**
     * Get the current tenant ID (nullable behavior).
     *
     * **Usage:** Defensive checks, optional tenant context scenarios.
     *
     * @return The current tenant ID, or null if not set
     */
    fun current(): String? {
        val stack = getContextStack()
        return stack.peekLast()
    }

    /**
     * Set the current tenant ID (push onto context stack).
     *
     * **Layer 1 (Story 4.2):** Called by TenantContextFilter after JWT validation.
     *
     * **Stack Behavior:** Pushes tenant ID onto stack, supporting nested contexts
     * for testing scenarios.
     *
     * @param tenantId The tenant ID to set
     * @throws IllegalArgumentException if tenant ID is invalid
     */
    fun setCurrentTenantId(tenantId: String) {
        // Validate tenant ID format
        TenantId(tenantId) // Throws if invalid

        val stack = getContextStack()
        stack.addLast(tenantId)
    }

    /**
     * Clear the current tenant context (pop from context stack).
     *
     * **Layer 1 (Story 4.2):** Called by TenantContextFilter cleanup after request.
     *
     * **Stack Behavior:** Pops the most recent tenant ID from stack. If stack
     * becomes empty, clears the ThreadLocal entirely (AC8: memory cleanup).
     */
    fun clearCurrentTenant() {
        val stack = getContextStack()

        if (stack.isNotEmpty()) {
            stack.removeLast()
        }

        // AC8: Clear ThreadLocal if stack is empty (memory cleanup)
        if (stack.isEmpty()) {
            contextHolder.remove()
        }
    }
}
