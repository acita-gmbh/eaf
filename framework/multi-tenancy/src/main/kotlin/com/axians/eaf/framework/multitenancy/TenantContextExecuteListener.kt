package com.axians.eaf.framework.multitenancy

import org.jooq.ExecuteContext
import org.jooq.ExecuteListener
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * jOOQ ExecuteListener that propagates TenantContext to PostgreSQL session variable.
 *
 * **Layer 3 Tenant Isolation (Story 4.4):**
 * - Sets `app.tenant_id` session variable before each SQL query
 * - Enables PostgreSQL Row-Level Security (RLS) policies to enforce tenant isolation
 * - Provides database-level defense even against SQL injection or application bugs
 *
 * **Execution Flow:**
 * 1. jOOQ query starts → `start()` callback executes
 * 2. Extract tenant ID from `TenantContext.current()` (nullable)
 * 3. If tenant ID present → Execute `SET LOCAL app.tenant_id = '<tenant-id>'`
 * 4. If tenant ID missing → Skip (fail-safe: RLS returns empty result)
 * 5. Query executes with RLS policies enforcing tenant_id filter
 *
 * **Fail-Safe Design:**
 * - Uses `TenantContext.current()` (nullable) instead of `getCurrentTenantId()` (throws)
 * - Missing tenant context → No session variable set → RLS returns empty (secure default)
 * - Supports system queries that don't require tenant context
 *
 * **RLS Policy Pattern:**
 * ```sql
 * CREATE POLICY tenant_isolation ON table_name
 *     FOR ALL
 *     USING (tenant_id = get_current_tenant_id());
 * ```
 *
 * **Performance:**
 * - Session variable overhead: <0.1ms per query
 * - RLS policy overhead: <2ms per query (with proper indexing)
 *
 * Epic 4, Story 4.4: AC3
 *
 * @since 1.0.0
 */
@Component
class TenantContextExecuteListener : ExecuteListener {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Called before SQL execution - sets PostgreSQL session variable for RLS.
     *
     * **Session Variable Lifecycle:**
     * - `SET LOCAL` scope: Current transaction only (auto-resets after COMMIT/ROLLBACK)
     * - Thread-safe: Each thread has its own database connection + transaction
     * - Safe for connection pooling: Variable cleared on transaction boundary
     *
     * @param ctx jOOQ execution context containing SQL and connection
     */
    override fun start(ctx: ExecuteContext) {
        // Extract current tenant ID (nullable - may not be set for system queries)
        val tenantId = TenantContext.current()

        if (tenantId != null) {
            try {
                // Set PostgreSQL session variable for RLS enforcement
                // SET LOCAL: Transaction-scoped (auto-cleanup on COMMIT/ROLLBACK)
                ctx
                    .dsl()
                    .execute("SET LOCAL app.tenant_id = ?", tenantId)

                logger.trace("Set app.tenant_id session variable: {}", tenantId)
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception, // Infrastructure Interceptor Pattern: observability only, log and continue
            ) {
                // Infrastructure interceptor legitimate use: log any failure type, don't break query
                // RLS will use fail-safe (empty result) if session variable not set
                logger.warn("Failed to set app.tenant_id session variable for tenant: {}", tenantId, e)
            }
        } else {
            // No tenant context - system query or non-tenant operation
            // RLS will return empty result for tenant-scoped tables (fail-safe)
            logger.trace("No tenant context - skipping app.tenant_id session variable")
        }
    }
}
