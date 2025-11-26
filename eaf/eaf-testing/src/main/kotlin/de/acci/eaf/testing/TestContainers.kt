package de.acci.eaf.testing

import dasniko.testcontainers.keycloak.KeycloakContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

public object TestContainers {

    public val postgres: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("dvmm_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
            .apply { start() }
    }

    public val keycloak: KeycloakContainer by lazy {
        KeycloakContainer("quay.io/keycloak/keycloak:26.0")
            .withRealmImportFile("/test-realm.json")
            .withReuse(true)
            .apply { start() }
    }
}
