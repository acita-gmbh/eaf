package com.axians.eaf.licensing.config

import org.axonframework.eventsourcing.eventstore.EventStorageEngine
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine
import org.axonframework.serialization.Serializer
import org.axonframework.serialization.json.JacksonSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
open class AxonConfiguration {
    @Bean
    open fun eventStorageEngine(dataSource: DataSource): EventStorageEngine =
        JpaEventStorageEngine
            .builder()
            .dataSource(dataSource)
            .build()

    @Bean
    open fun eventSerializer(): Serializer = JacksonSerializer.defaultSerializer()
}
