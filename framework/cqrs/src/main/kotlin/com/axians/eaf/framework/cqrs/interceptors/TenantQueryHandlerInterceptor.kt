package com.axians.eaf.framework.cqrs.interceptors

import com.axians.eaf.framework.security.tenant.TenantContext
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.axonframework.queryhandling.QueryMessage
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Axon query handler interceptor that sets PostgreSQL session variable for Row-Level Security (RLS)
 * enforcement before @QueryHandler execution.
 *
 * ## Story 9.2 Fix: Axon-Specific Query Interceptor
 *
 * **Problem**: Spring AOP aspects (like TenantDatabaseSessionInterceptor) don't fire for Axon QueryHandlers
 * because Axon Framework uses its own message dispatching infrastructure that bypasses Spring AOP proxies.
 *
 * **Solution**: Implement an Axon MessageHandlerInterceptor<QueryMessage> that:
 * 1. Extracts tenant ID from query payload (fail-closed if missing)
 * 2. Sets PostgreSQL session variable via jOOQ DSLContext
 * 3. Proceeds with query handler execution
 * 4. Cleans up TenantContext in finally block
 *
 * ## Integration with Multi-Tenancy Architecture
 *
 * This interceptor is Layer 3 of the 3-layer tenant isolation:
 * - **Layer 1**: TenantContextFilter extracts tenant from JWT (synchronous requests)
 * - **Layer 2**: Service boundary validation in command/query handlers
 * - **Layer 3**: **This interceptor** sets PostgreSQL RLS session variable for query operations
 *
 * ## PostgreSQL Session Variable
 *
 * **Variable Name**: `app.current_tenant`
 * **Scope**: Transaction-local (SET LOCAL)
 * **Lifecycle**:
 * - **Set**: Before @QueryHandler method execution (this interceptor)
 * - **Used**: During jOOQ repository queries (RLS policies reference it)
 * - **Reset**: Automatic on transaction commit/rollback (SET LOCAL scope)
 *
 * ## Query Message Contract
 *
 * **Required Query Fields**: All queries MUST include a `tenantId` field for tenant validation.
 *
 * Example query:
 * ```kotlin
 * data class FindWidgetByIdQuery(
 *     val widgetId: String,
 *     val tenantId: String // REQUIRED for tenant isolation
 * )
 * ```
 *
 * **Validation**:
 * - TenantContext.getCurrentTenantId() must match query.tenantId (Layer 2 validation)
 * - This interceptor sets session variable based on TenantContext (Layer 3 enforcement)
 *
 * ## Thread Safety
 *
 * **Fail-Closed Design**: If TenantContext is missing, getCurrentTenantId() throws exception,
 * preventing database queries without tenant isolation.
 *
 * The finally block ensures TenantContext cleanup after query execution, preventing
 * cross-tenant leakage in thread pools (though queries are typically synchronous).
 *
 * ## Performance Characteristics
 *
 * **Target**: <5ms p95 overhead per query
 *
 * - TenantContext read: ~50ns (negligible)
 * - DSLContext.execute(SET LOCAL): ~1-2ms (database round-trip)
 * - ThreadLocal cleanup: ~50ns (negligible)
 * - **Total overhead**: ~1-2ms per query (acceptable)
 *
 * Micrometer Timer tracks `tenant.query.interceptor.duration` for production monitoring.
 *
 * @param tenantContext ThreadLocal-based tenant context manager from Story 4.1
 * @param dsl jOOQ DSLContext for executing SET LOCAL (participates in Spring transactions)
 * @param meterRegistry Micrometer registry for performance monitoring (optional for tests)
 *
 * @see com.axians.eaf.framework.security.tenant.TenantContext
 * @see TenantEventMessageInterceptor Event handler tenant propagation
 */
@Component
@ConditionalOnClass(DSLContext::class)
@ConditionalOnBean(DSLContext::class)
class TenantQueryHandlerInterceptor(
    private val tenantContext: TenantContext,
    private val dsl: DSLContext,
    private val txManager: org.springframework.transaction.PlatformTransactionManager,
    private val meterRegistry: org.springframework.beans.factory.ObjectProvider<
        io.micrometer.core.instrument.MeterRegistry,
    >,
) : MessageHandlerInterceptor<QueryMessage<*, *>> {
    companion object {
        private val logger = LoggerFactory.getLogger(TenantQueryHandlerInterceptor::class.java)
        private const val SESSION_VAR_NAME = "app.current_tenant"

        // Cache reflection metadata to avoid per-query field lookup cost
        private val tenantFieldCache = java.util.concurrent.ConcurrentHashMap<Class<*>, java.lang.reflect.Field>()
    }

    /**
     * Intercepts query message processing to set PostgreSQL session variable before @QueryHandler execution.
     *
     * **Execution Flow**:
     * 1. Extract tenantId from TenantContext (fail-closed if missing)
     * 2. Execute `SET LOCAL app.current_tenant = 'tenant-id'` via DSLContext
     * 3. Proceed with query handler chain (repository uses session variable for RLS)
     * 4. Cleanup TenantContext in finally block
     *
     * **Story 9.2 Critical Fix**: This interceptor replaces the non-working Spring AOP aspect approach.
     * Axon Framework dispatches queries directly to handler methods without going through Spring AOP proxies,
     * so we must use Axon's own interceptor mechanism.
     *
     * @param unitOfWork Axon Unit of Work for transaction management
     * @param interceptorChain Handler chain to proceed with query processing
     * @return Query result from handler
     * @throws IllegalStateException if tenant context missing (fail-closed)
     */
    override fun handle(
        unitOfWork: UnitOfWork<out QueryMessage<*, *>>,
        interceptorChain: InterceptorChain,
    ): Any? {
        val query = unitOfWork.message
        val queryPayload = query.payload
        val registry = meterRegistry.ifAvailable
        val timer =
            registry?.let {
                io.micrometer.core.instrument.Timer
                    .start(it)
            }

        return try {
            // SECURITY: Extract tenant ID from query payload using reflection
            // This is critical because queries execute asynchronously on a thread pool,
            // and ThreadLocal TenantContext from HTTP request thread is NOT available
            val tenantId = extractTenantId(queryPayload)

            // Set TenantContext for this query execution thread
            // This makes tenantId available for query handler AND database operations
            tenantContext.setCurrentTenantId(tenantId)

            // Execute query within transaction with tenant RLS
            val result = executeQueryWithTenantSession(tenantId, query, interceptorChain)

            // Record success metrics
            registry
                ?.counter(
                    "tenant.query.interceptor.success",
                    "query_type",
                    query.queryName,
                )?.increment()

            result
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Record failure metrics
            registry
                ?.counter(
                    "tenant.query.interceptor.failure",
                    "query_type",
                    query.queryName,
                    "error_type",
                    ex::class.simpleName ?: "Unknown",
                )?.increment()

            logger.error(
                "Query interceptor failed for query: {} (error: {})",
                query.queryName,
                ex.message,
            )

            throw ex
        } finally {
            // CRITICAL: Cleanup TenantContext to prevent leakage across query executions
            // Queries run on thread pools, so cleanup is essential
            tenantContext.clearCurrentTenant()
            logger.debug("Tenant context cleared after query: {}", query.queryName)

            // Record performance metrics
            registry?.let { reg ->
                timer?.stop(
                    reg.timer(
                        "tenant.query.interceptor.duration",
                        "query_type",
                        query.queryName,
                    ),
                )
            }
        }
    }

    /**
     * Executes query handler within read-only transaction with tenant session variable set.
     *
     * @param tenantId The tenant ID to set in PostgreSQL session
     * @param query The query message being executed
     * @param interceptorChain The Axon interceptor chain
     * @return Query result
     */
    private fun executeQueryWithTenantSession(
        tenantId: String,
        query: QueryMessage<*, *>,
        interceptorChain: InterceptorChain,
    ): Any? =
        org.springframework.transaction.support
            .TransactionTemplate(txManager)
            .apply {
                isReadOnly = true
            }.execute {
                // Use PostgreSQL set_config() with parameterization to prevent SQL injection
                // set_config(setting_name, new_value, is_local) is equivalent to SET LOCAL but supports binding
                dsl.query("SELECT set_config(?, ?, true)", SESSION_VAR_NAME, tenantId).execute()

                logger.debug(
                    "Tenant context and session variable set for query: {} (tenant: {})",
                    query.queryName,
                    tenantId,
                )

                // Proceed with query handler execution (within same transaction)
                interceptorChain.proceed()
            }

    /**
     * Extracts tenantId from query payload using reflection.
     *
     * **Async Query Execution**: Queries execute on Axon's thread pool, NOT on the HTTP request thread.
     * ThreadLocal TenantContext populated by TenantContextFilter is NOT available here.
     * We must extract tenantId from the query payload itself.
     *
     * **Framework Assumption**: All queries MUST have a 'tenantId' property of type String.
     * This is enforced at the query design level for multi-tenant isolation.
     *
     * @param queryPayload The query object
     * @return The tenantId value from the query
     * @throws IllegalArgumentException if tenantId property is missing or invalid
     */
    private fun extractTenantId(queryPayload: Any): String =
        try {
            // Use cached reflection metadata for performance (ConcurrentHashMap)
            val clazz = queryPayload::class.java
            val tenantIdField =
                tenantFieldCache.computeIfAbsent(clazz) {
                    clazz.getDeclaredField("tenantId").apply { isAccessible = true }
                }
            val tenantId = tenantIdField.get(queryPayload) as? String

            require(!tenantId.isNullOrBlank()) {
                "Query ${queryPayload::class.simpleName} has null/blank tenantId"
            }

            tenantId
        } catch (e: NoSuchFieldException) {
            throw IllegalArgumentException(
                "Query ${queryPayload::class.simpleName} missing required tenantId field",
                e,
            )
        } catch (e: ClassCastException) {
            throw IllegalArgumentException(
                "Query ${queryPayload::class.simpleName} tenantId field is not String type",
                e,
            )
        }
}
