package com.axians.eaf.products.widget.test

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Shared PostgreSQL Testcontainer configuration for all integration tests.
 *
 * Uses Spring Boot 3.1+ @ServiceConnection for automatic datasource configuration.
 * Singleton pattern ensures container starts once and is reused across all test classes.
 */
object PostgresTestContainer {
    @ServiceConnection
    val container: PostgreSQLContainer<*> =
        PostgreSQLContainer(DockerImageName.parse("postgres:16.10-alpine"))
            .withDatabaseName("eaf_test")
            .withUsername("test")
            .withPassword("test")
            .also { it.start() }

    /**
     * Manual property configuration for compatibility with Kotest.
     *
     * While @ServiceConnection should work automatically, this provides
     * explicit property configuration as a fallback.
     */
    @DynamicPropertySource
    @JvmStatic
    fun configureProperties(registry: DynamicPropertyRegistry) {
        registry.add("spring.datasource.url") { container.jdbcUrl }
        registry.add("spring.datasource.username") { container.username }
        registry.add("spring.datasource.password") { container.password }
    }
}
