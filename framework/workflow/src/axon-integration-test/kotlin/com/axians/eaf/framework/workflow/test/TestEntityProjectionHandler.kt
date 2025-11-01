package com.axians.eaf.framework.workflow.test

import org.axonframework.eventhandling.EventHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Test-only projection handler for framework/workflow integration testing.
 *
 * This handler is LOCAL to framework tests (not shared with products).
 * It enables end-to-end testing of Flowable→Axon→Flowable flow without products dependency.
 *
 * Architecture: Framework tests must NOT depend on products.
 *
 * Note: This is a simplified handler (no database persistence) for testing event processing.
 * Real product handlers would write to projections database.
 */
@Component
class TestEntityProjectionHandler {
    private val logger = LoggerFactory.getLogger(TestEntityProjectionHandler::class.java)

    // In-memory storage for test validation
    private val entities = mutableMapOf<String, TestEntityCreatedEvent>()

    @EventHandler
    fun on(event: TestEntityCreatedEvent) {
        logger.debug("Processing TestEntityCreatedEvent for entityId: ${event.entityId}")
        entities[event.entityId] = event
        logger.debug("TestEntity projection created for entityId: ${event.entityId}")
    }

    // Test helper methods
    fun findById(entityId: String): TestEntityCreatedEvent? = entities[entityId]

    fun clear() = entities.clear()
}
