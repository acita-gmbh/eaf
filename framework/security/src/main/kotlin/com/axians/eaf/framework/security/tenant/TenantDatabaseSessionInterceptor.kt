package com.axians.eaf.framework.security.tenant

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * AOP aspect that sets PostgreSQL session variable before @Transactional methods,
 * enabling Row-Level Security (RLS) enforcement.
 *
 * ## Story 9.2 Fix: Use DSLContext for Transaction Participation
 *
 * **Previous Implementation**: Used DataSource.connection which created a NEW connection
 * separate from the Spring transaction, causing SET LOCAL to execute on wrong connection.
 *
 * **Current Implementation**: Uses jOOQ DSLContext which participates in Spring transactions,
 * ensuring SET LOCAL executes on the same connection as repository operations.
 *
 * ## Integration with Story 4.4
 *
 * This interceptor works seamlessly with async event processing:
 *
 * 1. **Command Flow**: TenantContext populated from JWT (Layer 1 filter)
 * 2. **Event Flow**: TenantEventMessageInterceptor restores TenantContext in async processors
 * 3. **Database Write**: **This aspect executes**, sets PostgreSQL session variable
 * 4. **RLS Enforcement**: PostgreSQL applies tenant_id filter on INSERT/UPDATE/SELECT
 *
 * ## Session Variable Management
 *
 * **Variable Name**: `app.current_tenant`
 * **Scope**: Transaction-local (SET LOCAL)
 * **Lifecycle**:
 * - **Set**: Before @Transactional method execution (this aspect)
 * - **Used**: During repository operations (RLS policies reference it)
 * - **Reset**: Automatic on transaction commit/rollback (SET LOCAL scope)
 *
 * ## Thread Safety
 *
 * This aspect reads TenantContext ThreadLocal, which is:
 * - Set by Layer 1 filter (synchronous command flow)
 * - Set by TenantEventMessageInterceptor (asynchronous event processors)
 * - Cleaned up in finally blocks (prevents cross-tenant leakage)
 *
 * **Fail-Closed Design**: If TenantContext is missing, getCurrentTenantId() throws exception,
 * preventing database writes without tenant isolation.
 *
 * @param tenantContext ThreadLocal tenant context from Story 4.1
 * @param dsl jOOQ DSLContext participating in Spring transactions
 *
 * @see com.axians.eaf.framework.cqrs.interceptors.TenantEventMessageInterceptor Async propagation
 */
@Aspect
@Component
@ConditionalOnBean(DSLContext::class)
@Order(0)
class TenantDatabaseSessionInterceptor(
    private val tenantContext: TenantContext,
    private val dsl: DSLContext,
) {
    private val logger = LoggerFactory.getLogger(TenantDatabaseSessionInterceptor::class.java)

    companion object {
        private const val SESSION_VAR_NAME = "app.current_tenant"
    }

    /**
     * Sets PostgreSQL session variable before @Transactional method execution.
     *
     * **Story 9.2 Fix**: Uses jOOQ DSLContext.execute() which participates in the
     * Spring transaction, ensuring the session variable is set on the correct connection.
     *
     * **Execution Flow**:
     * 1. Read tenantId from TenantContext (fail-closed if missing)
     * 2. Execute: `SET LOCAL app.current_tenant = 'tenant-id'` via DSLContext
     * 3. Proceed with @Transactional method (repository operations use same connection)
     * 4. RLS policies filter by app.current_tenant
     *
     * **Fail-Closed Security**: TenantContext.getCurrentTenantId() throws exception if missing,
     * preventing unfiltered database access.
     *
     * @param joinPoint Transactional method being intercepted
     * @return Method result
     * @throws IllegalStateException if tenant context missing (fail-closed)
     */
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    fun setSessionVariableBeforeTransaction(joinPoint: ProceedingJoinPoint): Any? {
        val tenantId = tenantContext.getCurrentTenantId()

        // Story 9.2: Use DSLContext to execute on transactional connection
        dsl.execute("SET LOCAL $SESSION_VAR_NAME = '$tenantId'")

        logger.trace(
            "Tenant session variable set: {} for method: {}",
            tenantId,
            joinPoint.signature.toShortString(),
        )

        return joinPoint.proceed()
    }
}
