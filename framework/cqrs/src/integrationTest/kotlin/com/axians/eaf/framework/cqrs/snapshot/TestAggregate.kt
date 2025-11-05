package com.axians.eaf.framework.cqrs.snapshot

import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.spring.stereotype.Aggregate
import java.util.UUID

/**
 * Test aggregate for snapshot integration testing
 *
 * This aggregate is used to verify snapshot creation and loading behavior.
 * It tracks a counter that is incremented with each UpdateTestAggregateCommand.
 */
@Aggregate(snapshotTriggerDefinition = "snapshotTriggerDefinition")
data class TestAggregate(
    @AggregateIdentifier
    var id: UUID? = null,
    var counter: Int = 0,
) {
    @CommandHandler
    constructor(command: CreateTestAggregateCommand) : this() {
        AggregateLifecycle.apply(TestAggregateCreatedEvent(command.id))
    }

    @CommandHandler
    fun handle(command: UpdateTestAggregateCommand) {
        AggregateLifecycle.apply(TestAggregateUpdatedEvent(command.id, counter + 1))
    }

    @EventSourcingHandler
    fun on(event: TestAggregateCreatedEvent) {
        this.id = event.id
        this.counter = 0
    }

    @EventSourcingHandler
    fun on(event: TestAggregateUpdatedEvent) {
        this.counter = event.newCounter
    }
}

// Commands
data class CreateTestAggregateCommand(
    val id: UUID,
)

data class UpdateTestAggregateCommand(
    val id: UUID,
)

// Events
data class TestAggregateCreatedEvent(
    val id: UUID,
)

data class TestAggregateUpdatedEvent(
    val id: UUID,
    val newCounter: Int,
)
