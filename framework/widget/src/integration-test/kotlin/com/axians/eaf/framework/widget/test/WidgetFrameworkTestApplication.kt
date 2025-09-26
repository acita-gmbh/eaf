package com.axians.eaf.framework.widget.test

import com.axians.eaf.framework.security.tenant.TenantContext
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.axonframework.commandhandling.CommandBus
import org.axonframework.commandhandling.SimpleCommandBus
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.commandhandling.gateway.DefaultCommandGateway
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore
import org.axonframework.eventsourcing.eventstore.EventStore
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@SpringBootApplication(
    scanBasePackages = [
        "com.axians.eaf.framework.widget.domain",
        "com.axians.eaf.framework.security.tenant",
        "com.axians.eaf.framework.core",
        "org.axonframework.springboot.autoconfig",
    ],
    exclude = [
        DataSourceAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration::class,
        org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration::class,
        org.axonframework.springboot.autoconfig.AxonServerBusAutoConfiguration::class,
    ],
)
open class WidgetFrameworkTestApplication {
    @Bean
    @Primary
    open fun testMeterRegistry(): MeterRegistry = SimpleMeterRegistry()

    @Bean
    @Primary
    open fun testTenantContext(meterRegistry: MeterRegistry): TenantContext = TenantContext(meterRegistry)
}
