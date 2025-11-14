package com.axians.eaf.framework.security.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Keycloak OIDC configuration properties.
 *
 * Configures Keycloak OIDC discovery and JWKS integration:
 * - OIDC issuer URI for metadata discovery
 * - JWKS endpoint URI for public key retrieval
 * - JWT audience claim validation
 * - JWKS cache duration for performance optimization
 *
 * Story 3.2: Keycloak OIDC Discovery and JWKS Integration
 */
@org.springframework.stereotype.Component
@ConfigurationProperties(prefix = "eaf.security.jwt")
data class KeycloakOidcConfiguration(
    /**
     * Keycloak OIDC issuer URI (e.g., http://keycloak:8080/realms/eaf).
     * Used for OIDC metadata discovery and issuer claim validation.
     */
    var issuerUri: String = "http://keycloak:8080/realms/eaf",
    /**
     * JWKS endpoint URI for public key retrieval.
     * Default: {issuerUri}/protocol/openid-connect/certs
     */
    var jwksUri: String = "http://keycloak:8080/realms/eaf/protocol/openid-connect/certs",
    /**
     * Expected JWT audience claim value.
     * Tokens must include this value in the 'aud' claim.
     */
    var audience: String = "eaf-api",
    /**
     * JWKS cache duration for public key caching.
     * Default: 10 minutes (balances performance and key rotation responsiveness)
     *
     * Longer duration = better performance, slower key rotation response
     * Shorter duration = more JWKS fetches, faster key rotation response
     */
    var jwksCacheDuration: Duration = Duration.ofMinutes(10),
    /**
     * Layer 9 toggle: when true, JWT subjects are validated against the user directory
     * to ensure the referenced user still exists and remains active.
     */
    var validateUser: Boolean = false,
)
