package de.acci.eaf.auth.keycloak

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Keycloak integration.
 *
 * These properties are used to configure the Keycloak identity provider.
 * They complement Spring Security's built-in OAuth2 Resource Server properties.
 *
 * @property clientId The OAuth2 client ID for role extraction from resource_access.
 * @property userInfoUri The Keycloak UserInfo endpoint URI.
 */
@ConfigurationProperties(prefix = "eaf.auth.keycloak")
public data class KeycloakProperties(
    val clientId: String = "dvmm-web",
    val userInfoUri: String = "",
)
