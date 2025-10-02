package com.axians.eaf.framework.workflow.test

import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.spring.stereotype.Aggregate
import java.math.BigDecimal

/**
 * Test-only aggregate for framework/workflow integration testing.
 *
 * This aggregate is LOCAL to framework tests (not shared with products).
 * It enables testing Flowable→Axon→Flowable integration without depending on products module.
 *
 * Architecture: Framework tests must NOT depend on products.
 */
@Aggregate
class TestEntityAggregate() {
    @AggregateIdentifier
    private lateinit var entityId: String

    private lateinit var tenantId: String
    private lateinit var name: String
    private var description: String? = null
    private lateinit var value: BigDecimal
    private lateinit var category: String

    @CommandHandler
    constructor(command: CreateTestEntityCommand) : this() {
        // Validate tenant context would happen here in real aggregate
        AggregateLifecycle.apply(
            TestEntityCreatedEvent(
                entityId = command.entityId,
                tenantId = command.tenantId,
                name = command.name,
                description = command.description,
                value = command.value,
                category = command.category,
                metadata = command.metadata,
            ),
        )
    }

    /**
     * Handles test entity cancellation for compensation workflow testing (Story 6.5).
     *
     * **Framework Test Infrastructure**: Simplified cancellation handler for E2E
     * compensation flow validation. Unlike Widget aggregate, this test version
     * omits tenant validation to focus on testing BPMN compensation routing.
     *
     * Story 6.5 (Task 4.1) - Framework compensation test infrastructure
     */
    @CommandHandler
    fun handle(command: CancelTestEntityCommand) {
        AggregateLifecycle.apply(
            TestEntityCancelledEvent(
                entityId = this.entityId,
                tenantId = this.tenantId,
                cancellationReason = command.cancellationReason,
                operator = command.operator,
            ),
        )
    }

    @EventSourcingHandler
    fun on(event: TestEntityCreatedEvent) {
        this.entityId = event.entityId
        this.tenantId = event.tenantId
        this.name = event.name
        this.description = event.description
        this.value = event.value
        this.category = event.category
    }

    /**
     * Applies test entity cancellation state transition (Story 6.5).
     *
     * Framework test infrastructure for compensation pattern validation.
     */
    @EventSourcingHandler
    fun on(event: TestEntityCancelledEvent) {
        // Test infrastructure - real aggregates would update status field
    }
}
