package com.axians.eaf.framework.multitenancy.test

import com.axians.eaf.framework.multitenancy.TenantAwareCommand
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.modelling.command.EntityCreator
import org.axonframework.modelling.command.TargetAggregateIdentifier
import org.axonframework.spring.stereotype.Aggregate

/**
 * Test command implementing TenantAwareCommand for tenant validation testing.
 *
 * @property aggregateId Unique identifier for the test aggregate
 * @property name Name of the test entity
 * @property tenantId Tenant ID for validation (TenantAwareCommand interface)
 */
data class TestCommand(
    @TargetAggregateIdentifier
    val aggregateId: String,
    val name: String,
    override val tenantId: String,
) : TenantAwareCommand

/**
 * Test event for aggregate creation.
 */
data class TestCreatedEvent(
    val aggregateId: String,
    val name: String,
    val tenantId: String,
)

/**
 * Minimal aggregate for testing TenantValidationInterceptor.
 *
 * This aggregate is used exclusively for integration testing Layer 2 tenant validation.
 * It implements a simple create-only command handler to verify interceptor behavior.
 *
 * Epic 4, Story 4.3: Test Infrastructure
 */
@Aggregate
class TestAggregate() {
    @AggregateIdentifier
    private lateinit var aggregateId: String

    private lateinit var name: String
    private lateinit var tenantId: String

    /**
     * Command handler for creating test aggregate.
     *
     * @param command Create command with tenant context
     * @return Aggregate ID on successful creation
     */
    @CommandHandler
    @EntityCreator
    constructor(command: TestCommand) : this() {
        // TenantValidationInterceptor will validate BEFORE this handler executes
        AggregateLifecycle.apply(
            TestCreatedEvent(
                aggregateId = command.aggregateId,
                name = command.name,
                tenantId = command.tenantId,
            ),
        )
    }

    /**
     * Event sourcing handler for aggregate creation.
     */
    @EventSourcingHandler
    fun on(event: TestCreatedEvent) {
        this.aggregateId = event.aggregateId
        this.name = event.name
        this.tenantId = event.tenantId
    }
}
