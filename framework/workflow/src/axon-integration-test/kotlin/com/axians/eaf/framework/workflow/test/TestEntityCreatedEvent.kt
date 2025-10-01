package com.axians.eaf.framework.workflow.test

import java.math.BigDecimal
import java.time.Instant

/**
 * Test event for validating Axon→Flowable event signaling.
 *
 * Test-only event type LOCAL to framework tests (not shared with products).
 * Enables testing generic infrastructure without depending on products module.
 *
 * Architecture: Framework tests must NOT depend on products.
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
