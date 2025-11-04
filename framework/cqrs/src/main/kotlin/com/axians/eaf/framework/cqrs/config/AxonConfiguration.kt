package com.axians.eaf.framework.cqrs.config

import org.axonframework.commandhandling.CommandBus
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.commandhandling.gateway.DefaultCommandGateway
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryBus
import org.axonframework.queryhandling.QueryGateway
import org.axonframework.queryhandling.QueryUpdateEmitter
import org.axonframework.queryhandling.SimpleQueryUpdateEmitter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Axon Framework Core Configuration
 *
 * Configures Command and Query Gateways for CQRS operations.
 * CommandBus, EventBus, and QueryBus are auto-configured by Axon Spring Boot starter.
 *
 * @see org.axonframework.springboot.autoconfig.AxonAutoConfiguration
 */
@Configuration
class AxonConfiguration {
    /**
     * Creates a CommandGateway bean for dispatching commands
     *
     * @param commandBus The auto-configured CommandBus from Axon
     * @return Configured CommandGateway instance
     */
    @Bean
    fun commandGateway(commandBus: CommandBus): CommandGateway =
        DefaultCommandGateway
            .builder()
            .commandBus(commandBus)
            .build()

    /**
     * Creates a QueryGateway bean for executing queries
     *
     * @param queryBus The auto-configured QueryBus from Axon
     * @return Configured QueryGateway instance
     */
    @Bean
    fun queryGateway(queryBus: QueryBus): QueryGateway =
        org.axonframework.queryhandling.DefaultQueryGateway
            .builder()
            .queryBus(queryBus)
            .build()
}
