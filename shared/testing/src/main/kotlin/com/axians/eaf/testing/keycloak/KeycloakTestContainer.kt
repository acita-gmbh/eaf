package com.axians.eaf.testing.keycloak

import dasniko.testcontainers.keycloak.KeycloakContainer

private const val KEYCLOAK_REALM = "eaf"
private const val KEYCLOAK_CLIENT_ID = "eaf-api"

/**
 * Shared singleton Keycloak Testcontainers instance for integration tests.
 */
object KeycloakTestContainer {
    private val container =
        KeycloakContainer("quay.io/keycloak/keycloak:26.4.2")
            .withReuse(true)
            .withRealmImportFile("keycloak/realm-export.json")
            .withEnv("KC_HTTP_ENABLED", "true")
            .withEnv("KC_HOSTNAME_STRICT", "false")

    fun start() {
        if (!container.isRunning) {
            container.start()
        }
    }

    fun getIssuerUri(): String = "${container.authServerUrl}/realms/$KEYCLOAK_REALM"

    fun getJwksUri(): String = "${getIssuerUri()}/protocol/openid-connect/certs"

    fun generateToken(
        username: String,
        password: String = "password",
    ): String =
        KeycloakTokenGenerator.generateToken(
            keycloakUrl = container.authServerUrl,
            realm = KEYCLOAK_REALM,
            clientId = KEYCLOAK_CLIENT_ID,
            username = username,
            password = password,
        )
}
