package com.axians.eaf.products.widget.test.config

import jakarta.annotation.PostConstruct
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

/**
 * Provides a Testcontainers-backed DataSource plus DSLContext for integration tests.
 *
 * **Active ONLY for "test" profile** (Story 3.10).
 *
 * **Why "rbac-test" is excluded:**
 * - "rbac-test" uses RbacTestContainersConfig with Spring-managed container bean
 * - @ServiceConnection creates JdbcConnectionDetails → DataSourceAutoConfiguration
 * - Manual DataSource bean is INCOMPATIBLE with @ServiceConnection (Spring Boot Issue #44046)
 * - Isolation prevents bean competition and timing race conditions
 * - Zero regression for existing "test" profile tests
 */
@TestConfiguration
@Profile("test")
open class TestDslConfiguration(
    private val environment: Environment,
) {
    @Bean
    @Primary
    open fun testDataSource(): DataSource {
        val url = environment.getProperty("spring.datasource.url") ?: DEFAULT_JDBC_URL
        val username = environment.getProperty("spring.datasource.username") ?: DEFAULT_USERNAME
        val password = environment.getProperty("spring.datasource.password") ?: DEFAULT_PASSWORD
        val driver = environment.getProperty("spring.datasource.driver-class-name") ?: DEFAULT_DRIVER

        return DataSourceBuilder
            .create()
            .url(url)
            .username(username)
            .password(password)
            .driverClassName(driver)
            .build()
    }

    @Bean
    @Primary
    open fun dslContext(dataSource: DataSource): DSLContext = DSL.using(dataSource, SQLDialect.POSTGRES)

    @Bean
    @Primary
    open fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
        DataSourceTransactionManager(dataSource)

    @PostConstruct
    fun logAutoconfigureExcludes() {
        logger.debug(
            "spring.autoconfigure.exclude={}",
            environment.getProperty("spring.autoconfigure.exclude"),
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TestDslConfiguration::class.java)

        private const val DEFAULT_JDBC_URL = "jdbc:tc:postgresql:16.10-alpine:///eaf_test"
        private const val DEFAULT_USERNAME = "test"
        private const val DEFAULT_PASSWORD = "test"
        private const val DEFAULT_DRIVER = "org.testcontainers.jdbc.ContainerDatabaseDriver"
    }
}
