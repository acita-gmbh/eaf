package com.axians.eaf.framework.workflow.test

import java.math.BigDecimal
import java.time.Instant

/**
 * Test-only event types for framework/workflow integration testing.
 *
 * These types are LOCAL to framework tests (not shared with products).
 * They enable testing generic infrastructure without depending on products module.
 *
 * Architecture: Framework tests must NOT depend on products.
 */

/**
 * Test event for validating Axon→Flowable event signaling.
 *
 * Minimal structure for testing AxonEventSignalHandler.
 */
data class TestEntityCreatedEvent(
    val entityId: String,
    val tenantId: String,
    val name: String,
    val description: String? = null,
    val value: BigDecimal,
    val category: String,
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Instant = Instant.now(),
)
