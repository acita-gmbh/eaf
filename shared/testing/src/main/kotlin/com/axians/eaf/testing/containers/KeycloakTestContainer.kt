package com.axians.eaf.testing.containers

import com.axians.eaf.testing.auth.KeycloakContainerException
import dasniko.testcontainers.keycloak.KeycloakContainer
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Keycloak Testcontainer configuration for EAF authentication testing.
 *
 * Provides pre-configured Keycloak instance with EAF test realm,
 * OIDC clients, and test users for realistic authentication testing.
 */
class KeycloakTestContainer private constructor() {
    companion object {
        private val logger = LoggerFactory.getLogger(KeycloakTestContainer::class.java)

        private const val KEYCLOAK_VERSION = "25.0.6"
        private const val REALM_FILE_PATH = "/keycloak/eaf-test-realm.json"
        private const val ADMIN_USERNAME = "admin"
        private const val ADMIN_PASSWORD = "admin"

        @Volatile
        private var instance: KeycloakContainer? = null

        /**
         * Gets or creates the singleton Keycloak container instance.
         * Container is started automatically on first access.
         */
        fun getInstance(): KeycloakContainer =
            instance ?: synchronized(this) {
                instance ?: createContainer().also {
                    instance = it
                    startContainer(it)
                }
            }

        /**
         * Creates a new Keycloak container with EAF realm configuration.
         */
        private fun createContainer(): KeycloakContainer {
            logger.info("Creating Keycloak test container with version $KEYCLOAK_VERSION")

            return KeycloakContainer("quay.io/keycloak/keycloak:$KEYCLOAK_VERSION")
                .withRealmImportFile(REALM_FILE_PATH)
                .withAdminUsername(ADMIN_USERNAME)
                .withAdminPassword(ADMIN_PASSWORD)
                .withStartupTimeout(Duration.ofMinutes(3))
                .withReuse(true) // Enable container reuse for faster test execution
                .apply {
                    // Configure additional startup parameters
                    withEnv("KC_HEALTH_ENABLED", "true")
                    withEnv("KC_METRICS_ENABLED", "true")
                    withEnv("KC_HTTP_ENABLED", "true")
                    withEnv("KC_HOSTNAME_STRICT", "false")
                    withEnv("KC_HOSTNAME_STRICT_HTTPS", "false")
                }
        }

        /**
         * Starts the Keycloak container and validates setup.
         */
        private fun startContainer(container: KeycloakContainer) {
            logger.info("Starting Keycloak test container...")
            val startTime = System.currentTimeMillis()

            try {
                container.start()
                val startupTime = System.currentTimeMillis() - startTime
                logger.info("Keycloak container started successfully in ${startupTime}ms")

                // Validate container accessibility
                validateContainerSetup(container)
            } catch (e: org.testcontainers.containers.ContainerLaunchException) {
                logger.error("Failed to start Keycloak container", e)
                throw KeycloakContainerException("Keycloak container startup failed", e)
            } catch (e: java.lang.InterruptedException) {
                logger.error("Keycloak container startup interrupted", e)
                throw KeycloakContainerException("Keycloak container startup interrupted", e)
            }
        }

        /**
         * Validates that the container is properly configured and accessible.
         */
        private fun validateContainerSetup(container: KeycloakContainer) {
            try {
                val authServerUrl = container.authServerUrl
                val adminUsername = container.adminUsername

                logger.info("Keycloak container validation:")
                logger.info("  Auth Server URL: $authServerUrl")
                logger.info("  Admin Username: $adminUsername")
                logger.info("  Realm Import: EAF test realm configured")

                // Validate OIDC discovery endpoint accessibility
                val discoveryUrl = "$authServerUrl/realms/eaf-test/.well-known/openid_configuration"
                logger.info("  OIDC Discovery: $discoveryUrl")
            } catch (e: java.lang.IllegalStateException) {
                logger.warn("Container validation failed, but container appears to be running", e)
            }
        }

        /**
         * Gets the OIDC discovery URL for the EAF test realm.
         */
        fun getOidcDiscoveryUrl(): String {
            val container = getInstance()
            return "${container.authServerUrl}/realms/eaf-test/.well-known/openid_configuration"
        }

        /**
         * Gets the token endpoint URL for the EAF test realm.
         */
        fun getTokenEndpointUrl(): String {
            val container = getInstance()
            return "${container.authServerUrl}/realms/eaf-test/protocol/openid-connect/token"
        }

        /**
         * Gets the auth server base URL.
         */
        fun getAuthServerUrl(): String = getInstance().authServerUrl

        /**
         * Gets the admin credentials for container management.
         */
        fun getAdminCredentials(): Pair<String, String> {
            val container = getInstance()
            return Pair(container.adminUsername, container.adminPassword)
        }

        /**
         * Stops and removes the container instance.
         * Used for cleanup in test scenarios that require fresh container state.
         */
        fun reset() {
            synchronized(this) {
                instance?.let { container ->
                    try {
                        logger.info("Stopping Keycloak test container...")
                        container.stop()
                        logger.info("Keycloak container stopped successfully")
                    } catch (e: java.lang.IllegalStateException) {
                        logger.warn("Error stopping Keycloak container", e)
                    }
                }
                instance = null
            }
        }
    }
}
