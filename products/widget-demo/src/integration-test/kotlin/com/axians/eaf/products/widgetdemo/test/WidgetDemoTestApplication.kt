package com.axians.eaf.products.widgetdemo.test

import com.axians.eaf.framework.security.jwt.JwtClaimsValidator
import com.axians.eaf.framework.security.jwt.JwtFormatValidator
import com.axians.eaf.framework.security.jwt.JwtSecurityValidator
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.persistence.EntityManager
import org.axonframework.common.jpa.EntityManagerProvider
import org.axonframework.common.transaction.TransactionManager
import org.axonframework.eventhandling.tokenstore.TokenStore
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore
import org.axonframework.eventsourcing.eventstore.EventStorageEngine
import org.axonframework.eventsourcing.eventstore.EventStore
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine
import org.axonframework.serialization.json.JacksonSerializer
import org.axonframework.spring.messaging.unitofwork.SpringTransactionManager
import org.axonframework.springboot.util.jpa.ContainerManagedEntityManagerProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

/**
 * Test configuration for widget-demo integration tests.
 *
 * Provides test-specific beans to override defaults.
 */
@Configuration
open class WidgetDemoTestApplication {
    private val logger = LoggerFactory.getLogger(WidgetDemoTestApplication::class.java)

    @Bean
    @Primary
    open fun testMeterRegistry(): MeterRegistry = SimpleMeterRegistry()

    @Bean
    @Primary
    open fun testJwtDecoder(): JwtDecoder =
        NullableJwtDecoder.createNull(
            defaultClaims =
                mapOf(
                    "tenant_id" to "550e8400-e29b-41d4-a716-446655440000",
                    "iss" to "http://localhost:8180/realms/eaf",
                    "aud" to "eaf-backend",
                ),
            defaultRoles = listOf("USER", "widget:create", "widget:read"),
        )

    @Bean
    @Primary
    open fun testRedisTemplate(): RedisTemplate<String, String> = NullableRedisTemplate.createNull()

    @Bean
    @Primary
    open fun testJwtFormatValidator(
        jwtDecoder: JwtDecoder,
        meterRegistry: MeterRegistry,
    ): JwtFormatValidator = JwtFormatValidator(jwtDecoder, meterRegistry)

    @Bean
    @Primary
    open fun testJwtClaimsValidator(meterRegistry: MeterRegistry): JwtClaimsValidator = JwtClaimsValidator(meterRegistry)

    @Bean
    @Primary
    open fun testJwtSecurityValidator(
        redisTemplate: RedisTemplate<String, String>,
        meterRegistry: MeterRegistry,
    ): JwtSecurityValidator = JwtSecurityValidator(redisTemplate, meterRegistry)

    @Bean
    @Primary
    open fun testEntityManagerProvider(entityManager: EntityManager): EntityManagerProvider =
        ContainerManagedEntityManagerProvider().apply {
            setEntityManager(entityManager)
        }

    @Bean
    @Primary
    open fun testTransactionManager(platformTransactionManager: PlatformTransactionManager): TransactionManager =
        SpringTransactionManager(platformTransactionManager)

    @Bean
    @Primary
    @DependsOn("testAxonSchemaInitializer")
    open fun testEventStorageEngine(
        dataSource: DataSource,
        entityManagerProvider: EntityManagerProvider,
        transactionManager: TransactionManager,
    ): EventStorageEngine =
        JpaEventStorageEngine
            .builder()
            .dataSource(dataSource)
            .entityManagerProvider(entityManagerProvider)
            .transactionManager(transactionManager)
            .eventSerializer(buildJacksonSerializer())
            .snapshotSerializer(buildJacksonSerializer())
            .build()

    @Bean(name = ["eventStore"])
    @Primary
    @DependsOn("testAxonSchemaInitializer")
    open fun testEventStore(storageEngine: EventStorageEngine): EventStore =
        EmbeddedEventStore.builder().storageEngine(storageEngine).build()

    @Bean
    @DependsOn("entityManagerFactory")
    open fun testAxonSchemaInitializer(dataSource: DataSource): InitializingBean =
        InitializingBean {
            logger.info("Executing Axon schema initializer for integration tests")

            val jdbcTemplate = JdbcTemplate(dataSource)
            val schemaName = jdbcTemplate.queryForObject("select current_schema()", String::class.java)

            val sagaColumnType =
                jdbcTemplate.queryForObject(
                    "select data_type from information_schema.columns where table_schema = ? and table_name = 'saga_entry' and column_name = 'serialized_saga'",
                    String::class.java,
                    schemaName,
                )
            if (sagaColumnType != null && sagaColumnType.lowercase() != "oid") {
                logger.info("Recreating saga_entry to align serialized_saga column type (found={})", sagaColumnType)
                jdbcTemplate.execute("DROP TABLE $schemaName.saga_entry CASCADE")
            }

            ResourceDatabasePopulator(ClassPathResource("db/axon/schema.sql")).execute(dataSource)

            runCatching {
                val tableNames =
                    listOf(
                        "association_value_entry",
                        "saga_entry",
                        "token_entry",
                        "snapshot_event_entry",
                        "domain_event_entry",
                    )
                for (tableName in tableNames) {
                    val regClass =
                        jdbcTemplate.queryForObject(
                            "select to_regclass(?::text)",
                            String::class.java,
                            "$schemaName.$tableName",
                        )
                    if (regClass != null) {
                        jdbcTemplate.execute("TRUNCATE TABLE $schemaName.$tableName CASCADE")
                    }
                }

                val sequenceNames = listOf("domain_event_entry_seq", "snapshot_event_entry_seq")
                for (sequenceName in sequenceNames) {
                    val regClass =
                        jdbcTemplate.queryForObject(
                            "select to_regclass(?::text)",
                            String::class.java,
                            "$schemaName.$sequenceName",
                        )
                    if (regClass != null) {
                        jdbcTemplate.execute("ALTER SEQUENCE $schemaName.$sequenceName RESTART WITH 1")
                    }
                }

                val sequenceIncrement =
                    jdbcTemplate.queryForObject(
                        "select increment from information_schema.sequences where sequence_name = 'domain_event_entry_seq' and sequence_schema = ?",
                        Long::class.java,
                        schemaName,
                    )
                val tableExists =
                    jdbcTemplate.queryForObject(
                        "select to_regclass(?::text)",
                        String::class.java,
                        "$schemaName.domain_event_entry",
                    ) != null

                logger.info(
                    "Initialized Axon schema for integration tests (schema={}, domain_event_entry present={}, seq increment={})",
                    schemaName,
                    tableExists,
                    sequenceIncrement,
                )
            }.onFailure { ex ->
                logger.warn("Failed to verify Axon schema initialization", ex)
            }
        }

    private fun buildJacksonSerializer(): JacksonSerializer {
        val objectMapper =
            jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        return JacksonSerializer
            .builder()
            .objectMapper(objectMapper)
            .build()
    }

    @Bean
    @Primary
    open fun testTokenStore(): TokenStore = InMemoryTokenStore()
}
