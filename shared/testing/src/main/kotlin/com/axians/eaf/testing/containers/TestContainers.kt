package com.axians.eaf.testing.containers

import dasniko.testcontainers.keycloak.KeycloakContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Shared Testcontainers bootstrap for integration tests.
 * Provides reusable PostgreSQL, Redis, and Keycloak containers started once per JVM.
 */
object TestContainers {
    @JvmStatic
    val postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer(DockerImageName.parse("postgres:16.1-alpine"))
            .withDatabaseName("eaf_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)

    @JvmStatic
    val redis: GenericContainer<*> =
        GenericContainer(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379)
            .withReuse(true)

    @JvmStatic
    val keycloak: KeycloakContainer =
        KeycloakContainer("quay.io/keycloak/keycloak:26.0.0")
            .withRealmImportFile("test-realm.json")
            .withReuse(true)

    init {
        startIfNeeded(postgres)
        startIfNeeded(redis)
        startIfNeeded(keycloak)
    }

    @JvmStatic
    fun startAll() {
        startIfNeeded(postgres)
        startIfNeeded(redis)
        startIfNeeded(keycloak)
    }

    private fun startIfNeeded(container: GenericContainer<*>) {
        if (!container.isRunning) {
            container.start()
        }
    }

    private fun startIfNeeded(container: PostgreSQLContainer<*>) = startIfNeeded(container as GenericContainer<*>)

    private fun startIfNeeded(container: KeycloakContainer) = startIfNeeded(container as GenericContainer<*>)
}
