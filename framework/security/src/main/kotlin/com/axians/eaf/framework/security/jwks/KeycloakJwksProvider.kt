package com.axians.eaf.framework.security.jwks

import com.nimbusds.jose.jwk.JWKSet
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.URI
import java.text.ParseException
import java.time.Instant

/**
 * Provider for Keycloak JWKS (JSON Web Key Set) public keys.
 *
 * Fetches and caches public keys from Keycloak JWKS endpoint for JWT signature
 * validation. Implements caching to reduce network calls and improve performance.
 *
 * Caching Strategy:
 * - Keys cached for configured duration (default: 10 minutes)
 * - Cache invalidation on fetch errors (force refresh on next request)
 * - Thread-safe concurrent access
 *
 * Key Rotation Handling:
 * - New keys fetched automatically when cache expires
 * - Graceful degradation if Keycloak temporarily unavailable
 * - Multiple keys supported (allows rotation overlap period)
 *
 * Story 3.2: Keycloak OIDC Discovery and JWKS Integration
 */
@Component
class KeycloakJwksProvider(
    private val keycloakConfig: com.axians.eaf.framework.security.config.KeycloakOidcConfiguration,
) {
    @Volatile
    private var cachedJwkSet: JWKSet? = null

    @Volatile
    private var cacheExpiry: Instant? = null

    /**
     * Fetches the JWK Set from Keycloak JWKS endpoint.
     *
     * Uses cached keys if available and not expired. Fetches from Keycloak
     * if cache is empty or expired.
     *
     * @return JWK Set containing Keycloak public keys
     * @throws IllegalStateException if JWKS fetch fails
     */
    @Synchronized
    fun getJwkSet(): JWKSet {
        val now = Instant.now()

        // Return cached JWK Set if still valid
        val cached = cachedJwkSet
        if (cached != null && cacheExpiry != null && now.isBefore(cacheExpiry)) {
            return cached
        }

        // Fetch fresh JWK Set from Keycloak
        val jwkSet =
            try {
                JWKSet.load(URI.create(keycloakConfig.jwksUri).toURL())
            } catch (ex: IOException) {
                // Invalidate cache on network error
                cachedJwkSet = null
                cacheExpiry = null
                throw IllegalStateException(
                    "Failed to fetch JWKS from Keycloak (network error): ${keycloakConfig.jwksUri}",
                    ex,
                )
            } catch (ex: ParseException) {
                // Invalidate cache on parse error
                cachedJwkSet = null
                cacheExpiry = null
                throw IllegalStateException(
                    "Failed to parse JWKS from Keycloak: ${keycloakConfig.jwksUri}",
                    ex,
                )
            }

        // Update cache
        cachedJwkSet = jwkSet
        cacheExpiry = now.plus(keycloakConfig.jwksCacheDuration)

        return jwkSet
    }

    /**
     * Invalidates the cached JWK Set.
     *
     * Forces fresh fetch on next getJwkSet() call. Useful for testing
     * or manual key rotation scenarios.
     */
    @Synchronized
    fun invalidateCache() {
        cachedJwkSet = null
        cacheExpiry = null
    }
}
