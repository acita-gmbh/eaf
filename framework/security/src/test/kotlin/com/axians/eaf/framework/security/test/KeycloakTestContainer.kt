package com.axians.eaf.framework.security.test

import dasniko.testcontainers.keycloak.KeycloakContainer

/**
 * Keycloak Testcontainer utility for security integration tests.
 *
 * Provides:
 * - Singleton Keycloak container with realm import
 * - Test realm with preconfigured users, roles, and tenant_id attributes
 * - JWT token generation via password grant for test users
 * - Container reuse for performance (start once per test suite)
 *
 * Story 3.3: Testcontainers Keycloak for Integration Tests
 */
object KeycloakTestContainer {
    private val container =
        KeycloakContainer("quay.io/keycloak/keycloak:26.4.2")
            .withRealmImportFile("keycloak/realm-export.json")
            .withReuse(true)

    /**
     * Starts the Keycloak container if not already running.
     *
     * Container startup takes ~20-30 seconds on first run.
     * Subsequent calls reuse the running container for performance.
     */
    fun start() {
        if (!container.isRunning) {
            container.start()
        }
    }

    /**
     * Gets the Keycloak issuer URI for the test realm.
     *
     * @return Issuer URI (e.g., http://localhost:12345/realms/eaf)
     */
    fun getIssuerUri(): String = "${container.authServerUrl}/realms/eaf"

    /**
     * Gets the JWKS endpoint URI for the test realm.
     *
     * @return JWKS URI for public key retrieval
     */
    fun getJwksUri(): String = "${getIssuerUri()}/protocol/openid-connect/certs"

    /**
     * Generates a JWT token for the specified test user.
     *
     * Uses password grant type to authenticate and obtain access token
     * from the test Keycloak instance via direct HTTP call.
     *
     * @param username Test user email (e.g., admin@eaf.com)
     * @param password Test user password (default: "password")
     * @return JWT access token as string
     */
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
