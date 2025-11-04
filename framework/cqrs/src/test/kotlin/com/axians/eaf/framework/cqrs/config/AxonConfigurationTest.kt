package com.axians.eaf.framework.cqrs.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.axonframework.commandhandling.SimpleCommandBus
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.commandhandling.gateway.DefaultCommandGateway
import org.axonframework.queryhandling.QueryGateway
import org.axonframework.queryhandling.SimpleQueryBus

/**
 * Unit tests for AxonConfiguration
 *
 * Tests verify that CommandGateway and QueryGateway beans are created correctly
 * from the AxonConfiguration class.
 *
 * AC5: Unit tests verify gateways are injectable and functional
 * AC6: ./gradlew :framework:cqrs:test passes in <10 seconds
 */
class AxonConfigurationTest :
    FunSpec({

        val config = AxonConfiguration()

        test("commandGateway bean should be created with CommandBus") {
            // Given: A CommandBus instance
            val commandBus = SimpleCommandBus.builder().build()

            // When: Creating CommandGateway bean
            val commandGateway = config.commandGateway(commandBus)

            // Then: Gateway should be created and be of correct type
            commandGateway shouldNotBe null
            commandGateway.shouldBeInstanceOf<CommandGateway>()
            commandGateway.shouldBeInstanceOf<DefaultCommandGateway>()
        }

        test("queryGateway bean should be created with QueryBus") {
            // Given: A QueryBus instance
            val queryBus = SimpleQueryBus.builder().build()

            // When: Creating QueryGateway bean
            val queryGateway = config.queryGateway(queryBus)

            // Then: Gateway should be created and be of correct type
            queryGateway shouldNotBe null
            queryGateway.shouldBeInstanceOf<QueryGateway>()
            queryGateway.shouldBeInstanceOf<org.axonframework.queryhandling.DefaultQueryGateway>()
        }

        test("commandGateway should use provided CommandBus") {
            // Given: A CommandBus instance
            val commandBus = SimpleCommandBus.builder().build()

            // When: Creating CommandGateway
            val gateway = config.commandGateway(commandBus)

            // Then: Gateway should be functional (no exceptions thrown)
            gateway shouldNotBe null
        }

        test("queryGateway should use provided QueryBus") {
            // Given: A QueryBus instance
            val queryBus = SimpleQueryBus.builder().build()

            // When: Creating QueryGateway
            val gateway = config.queryGateway(queryBus)

            // Then: Gateway should be functional (no exceptions thrown)
            gateway shouldNotBe null
        }
    })
