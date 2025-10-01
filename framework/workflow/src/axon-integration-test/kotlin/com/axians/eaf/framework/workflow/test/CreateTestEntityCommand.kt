package com.axians.eaf.framework.workflow.test

import java.math.BigDecimal

/**
 * Test command for validating Flowable→Axon command dispatch.
 *
 * Test-only command type LOCAL to framework tests (not shared with products).
 * Enables testing generic infrastructure without depending on products module.
 *
 * Architecture: Framework tests must NOT depend on products.
 */
data class CreateTestEntityCommand(
    val entityId: String,
    val tenantId: String,
    val name: String,
    val description: String?,
    val value: BigDecimal,
    val category: String,
    val metadata: Map<String, Any> = emptyMap(),
)
