package com.axians.eaf.framework.security.tenant

import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.AfterThrowing
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * Aspect that ensures tenant session variable cleanup after transactional methods.
 *
 * Defense-in-depth: While RLS policies use NULLIF to handle empty strings safely,
 * this aspect prevents connection pool state leakage by explicitly resetting
 * the session variable after each transaction.
 *
 * Execution Order: Must run AFTER Spring's transaction management aspect (@Order(1000))
 * to ensure cleanup occurs after COMMIT/ROLLBACK.
 *
 * Security Model:
 * - Primary defense: NULLIF wrapper in RLS policies (database-enforced)
 * - Secondary defense: This aspect (application-enforced cleanup)
 *
 * Performance: <1% transaction overhead (single RESET statement)
 *
 * References:
 * - Story 4.3: Layer 3 Database RLS Implementation
 * - Research: docs/prototypes/4.3-sec-002-research-synthesis.md
 * - PostgreSQL Behavior: Custom GUC variables default to empty string after SET LOCAL
 */
@Aspect
@Component
@Order(1000)
class TenantSessionCleanupAspect(
    private val dataSource: DataSource
) {
    private val logger = LoggerFactory.getLogger(TenantSessionCleanupAspect::class.java)

    companion object {
        private const val SESSION_VAR_NAME = "app.current_tenant"
    }

    @AfterReturning("@annotation(org.springframework.transaction.annotation.Transactional)")
    fun cleanupAfterSuccessfulTransaction() {
        executeCleanup("commit")
    }

    @AfterThrowing("@annotation(org.springframework.transaction.annotation.Transactional)")
    fun cleanupAfterFailedTransaction() {
        executeCleanup("rollback")
    }

    private fun executeCleanup(reason: String) {
        try {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("RESET $SESSION_VAR_NAME")
                    logger.trace("Tenant session variable reset after {}", reason)
                }
            }
        } catch (e: Exception) {
            logger.warn(
                "Failed to reset tenant session variable after {}: {}",
                reason,
                e.message
            )
        }
    }
}