package com.axians.eaf.framework.widget.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.api.widget.commands.UpdateWidgetCommand
import com.axians.eaf.api.widget.events.WidgetCreatedEvent
import com.axians.eaf.api.widget.events.WidgetUpdatedEvent
import com.axians.eaf.framework.security.tenant.TenantContext
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.spring.stereotype.Aggregate
import java.math.BigDecimal
import java.time.Instant

@Aggregate
class Widget {
    @AggregateIdentifier
    private lateinit var widgetId: String
    private lateinit var tenantId: String
    private lateinit var name: String
    private var description: String? = null
    private lateinit var value: BigDecimal
    private lateinit var category: String
    private lateinit var createdAt: Instant
    private var updatedAt: Instant? = null
    private var metadata: Map<String, Any> = emptyMap()
    private var status: WidgetStatus = WidgetStatus.ACTIVE

    enum class WidgetStatus {
        DRAFT,
        ACTIVE,
        INACTIVE,
        ARCHIVED,
    }

    constructor()

    @CommandHandler
    constructor(command: CreateWidgetCommand) {
        val currentTenant = TenantContext().getCurrentTenantId()

        require(command.tenantId == currentTenant) {
            "Tenant isolation violation: command=${command.tenantId}, context=$currentTenant"
        }

        validateCreateCommand(command).fold(
            { error -> throw IllegalArgumentException("Validation failed: $error") },
            {
                AggregateLifecycle.apply(
                    WidgetCreatedEvent(
                        widgetId = command.widgetId,
                        tenantId = command.tenantId,
                        name = command.name,
                        description = command.description,
                        value = command.value,
                        category = command.category,
                        metadata = command.metadata,
                    ),
                )
            },
        )
    }

    @CommandHandler
    fun handle(command: UpdateWidgetCommand): Either<WidgetError, Unit> {
        val currentTenant = TenantContext().getCurrentTenantId()

        val error =
            when {
                command.tenantId != currentTenant ->
                    WidgetError.TenantIsolationViolation(
                        requestedTenant = command.tenantId,
                        actualTenant = currentTenant,
                    )
                this.tenantId != currentTenant ->
                    WidgetError.TenantIsolationViolation(
                        requestedTenant = currentTenant,
                        actualTenant = this.tenantId,
                    )
                status != WidgetStatus.ACTIVE ->
                    WidgetError.BusinessRuleViolation(
                        rule = "widget.must.be.active",
                        reason = "Only active widgets can be updated",
                    )
                else -> null
            }

        if (error != null) {
            return error.left()
        }

        return validateUpdateCommand(command).fold(
            { validationError -> validationError.left() },
            {
                AggregateLifecycle.apply(
                    WidgetUpdatedEvent(
                        widgetId = this.widgetId,
                        tenantId = this.tenantId,
                        name = command.name,
                        description = command.description,
                        value = command.value,
                        category = command.category,
                        metadata = command.metadata,
                    ),
                )
                Unit.right()
            },
        )
    }

    @EventSourcingHandler
    fun on(event: WidgetCreatedEvent) {
        this.widgetId = event.widgetId
        this.tenantId = event.tenantId
        this.name = event.name
        this.description = event.description
        this.value = event.value
        this.category = event.category
        this.metadata = event.metadata
        this.createdAt = event.createdAt
        this.status = WidgetStatus.ACTIVE
    }

    @EventSourcingHandler
    fun on(event: WidgetUpdatedEvent) {
        event.name?.let { this.name = it }
        // For description, distinguish between null (don't update) and empty string (update to empty)
        if (event.description != null) {
            this.description = event.description
        }
        event.value?.let { this.value = it }
        event.category?.let { this.category = it }
        event.metadata?.let { this.metadata = it }
        this.updatedAt = event.updatedAt
    }

    companion object {
        private val NAME_PATTERN = Regex("^[A-Za-z0-9](?:[A-Za-z0-9 _-]{0,98}[A-Za-z0-9])?$")
        private val CATEGORY_PATTERN = Regex("^[A-Z][A-Z_]{2,29}$")
        private val MIN_VALUE = BigDecimal.ZERO
        private val MAX_VALUE = BigDecimal("1000000")

        fun validateCreateCommand(command: CreateWidgetCommand): Either<WidgetError, Unit> {
            val errors = mutableListOf<WidgetError>()

            validateName(command.name)?.let { errors.add(it) }
            validateCategory(command.category)?.let { errors.add(it) }
            validateValue(command.value)?.let { errors.add(it) }
            command.description?.let { validateDescription(it)?.let { errors.add(it) } }

            return if (errors.isEmpty()) Unit.right() else errors.first().left()
        }

        fun validateUpdateCommand(command: UpdateWidgetCommand): Either<WidgetError, Unit> {
            val errors = mutableListOf<WidgetError>()

            command.name?.let { validateName(it)?.let { errors.add(it) } }
            command.category?.let { validateCategory(it)?.let { errors.add(it) } }
            command.value?.let { validateValue(it)?.let { errors.add(it) } }
            command.description?.let { validateDescription(it)?.let { errors.add(it) } }

            return if (errors.isEmpty()) Unit.right() else errors.first().left()
        }

        private fun validateName(name: String): WidgetError.ValidationError? =
            when {
                name.isBlank() || name.length !in 2..100 ->
                    WidgetError.ValidationError(
                        field = "name",
                        constraint = "length",
                        invalidValue = name,
                    )
                !name.matches(NAME_PATTERN) ->
                    WidgetError.ValidationError(
                        field = "name",
                        constraint = "pattern",
                        invalidValue = name,
                    )
                else -> null
            }

        private fun validateCategory(category: String): WidgetError.ValidationError? =
            if (!category.matches(CATEGORY_PATTERN)) {
                WidgetError.ValidationError(
                    field = "category",
                    constraint = "pattern",
                    invalidValue = category,
                )
            } else {
                null
            }

        private fun validateValue(value: BigDecimal): WidgetError.ValidationError? =
            if (value < MIN_VALUE || value > MAX_VALUE) {
                WidgetError.ValidationError(
                    field = "value",
                    constraint = "range",
                    invalidValue = value,
                )
            } else {
                null
            }

        private fun validateDescription(description: String): WidgetError.ValidationError? =
            if (description.length > 1000) {
                WidgetError.ValidationError(
                    field = "description",
                    constraint = "max_length",
                    invalidValue = description.length,
                )
            } else {
                null
            }
    }
}
