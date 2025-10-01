package com.axians.eaf.framework.workflow.test

import java.math.BigDecimal

/**
 * Test-only command types for framework/workflow integration testing.
 *
 * These types are LOCAL to framework tests (not shared with products).
 * They enable testing generic infrastructure without depending on products module.
 *
 * Architecture: Framework tests must NOT depend on products.
 */

/**
 * Test command for validating Flowable→Axon command dispatch.
 *
 * Minimal structure for testing DispatchAxonCommandTask.
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
