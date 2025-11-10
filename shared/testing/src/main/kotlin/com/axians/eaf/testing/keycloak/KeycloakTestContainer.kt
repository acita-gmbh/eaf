package com.axians.eaf.testing.keycloak

import dasniko.testcontainers.keycloak.KeycloakContainer

/**
 * Shared singleton Keycloak Testcontainers instance for integration tests.
 */
object KeycloakTestContainer {
    private val container =
        KeycloakContainer("quay.io/keycloak/keycloak:26.4.2")
            .withReuse(true)
            .withRealmImportFile("keycloak/realm-export.json")

    fun start() {
        if (!container.isRunning) {
            container.start()
        }
    }

    fun getIssuerUri(): String = "${container.authServerUrl}/realms/eaf"

    fun getJwksUri(): String = "${getIssuerUri()}/protocol/openid-connect/certs"

    fun generateToken(
        username: String,
        password: String = "password",
    ): String =
        KeycloakTokenGenerator.generateToken(
            keycloakUrl = container.authServerUrl,
            realm = "eaf",
            clientId = "eaf-api",
            username = username,
            password = password,
        )
}
