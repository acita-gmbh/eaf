package com.axians.eaf.framework.security.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Configuration for Keycloak admin client access used by Layer 9 user validation.
 */
@Component
@ConfigurationProperties(prefix = "eaf.keycloak")
data class KeycloakAdminProperties(
    /**
     * Base URL for the Keycloak server (e.g., http://keycloak:8080).
     */
    var baseUrl: String = "http://keycloak:8080",
    /**
     * Realm that contains both application users and the admin service account.
     */
    var realm: String = "eaf",
    /**
     * Client ID with service-account access to read users via the admin API.
     */
    var adminClientId: String = "eaf-admin",
    /**
     * Client secret for the admin client. Must be provided via environment variable in non-test environments.
     */
    var adminClientSecret: String = "",
    /**
     * Cache duration for successful user lookups (active or inactive users).
     */
    var userCacheTtl: Duration = Duration.ofSeconds(60),
    /**
     * Cache duration for negative lookups (user not found) to avoid hammering Keycloak.
     */
    var negativeUserCacheTtl: Duration = Duration.ofSeconds(15),
    /**
     * Safety window subtracted from access-token expiry to ensure we refresh before expiration.
     */
    var tokenExpirySkew: Duration = Duration.ofSeconds(5),
)
