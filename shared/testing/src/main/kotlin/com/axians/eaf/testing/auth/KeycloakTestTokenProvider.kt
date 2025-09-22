package com.axians.eaf.testing.auth

import com.axians.eaf.testing.containers.TestContainers
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility for retrieving authentication tokens from Keycloak test container.
 *
 * Provides convenient methods to obtain JWT tokens for different test users
 * with appropriate roles and tenant context for authentication testing.
 */
class KeycloakTestTokenProvider {
    companion object {
        private val logger = LoggerFactory.getLogger(KeycloakTestTokenProvider::class.java)
        private val httpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
        private val objectMapper = ObjectMapper()

        // Token cache for performance optimization
        private val tokenCache = ConcurrentHashMap<String, CachedToken>()

        private const val CLIENT_ID = "eaf-client"
        private const val CLIENT_SECRET = "eaf-client-secret"
        private const val TOKEN_CACHE_EXPIRATION_SECONDS = 1500 // 25 minutes

        /**
         * Gets a valid JWT token for the admin user.
         * Token includes eaf-admin role and test-tenant-admin tenant context.
         */
        fun getAdminToken(): String = getTokenForUser("admin-user", "admin-password", "admin")

        /**
         * Gets a valid JWT token for the basic user.
         * Token includes eaf-user role and test-tenant-user tenant context.
         */
        fun getBasicUserToken(): String = getTokenForUser("basic-user", "user-password", "user")

        /**
         * Gets a JWT token for a specific user with caching support.
         *
         * @param username the username for authentication
         * @param password the password for authentication
         * @param userType user type for cache key differentiation
         * @return valid JWT access token
         */
        fun getTokenForUser(
            username: String,
            password: String,
            userType: String,
        ): String {
            val cacheKey = "$userType:$username"

            // Check cache first
            tokenCache[cacheKey]?.let { cachedToken ->
                if (cachedToken.isValid()) {
                    logger.debug("Using cached token for user: $username")
                    return cachedToken.token
                } else {
                    logger.debug("Cached token expired for user: $username, refreshing...")
                    tokenCache.remove(cacheKey)
                }
            }

            // Get fresh token
            val token = retrieveTokenFromKeycloak(username, password)

            // Cache with 25-minute expiration (5 minutes before 30-minute token expiry)
            val expirationTime = Instant.now().plusSeconds(TOKEN_CACHE_EXPIRATION_SECONDS.toLong())
            tokenCache[cacheKey] = CachedToken(token, expirationTime)

            logger.debug("Retrieved and cached new token for user: $username")
            return token
        }

        /**
         * Retrieves a fresh token from Keycloak using username/password authentication.
         */
        private fun retrieveTokenFromKeycloak(
            username: String,
            password: String,
        ): String =
            try {
                performTokenRequest(username, password)
            } catch (e: TokenRetrievalException) {
                throw e
            } catch (e: java.io.IOException) {
                logger.error("Token retrieval IO error for user: $username", e)
                throw KeycloakAuthenticationException("Token retrieval failed for user: $username", e)
            } catch (e: java.lang.InterruptedException) {
                logger.error("Token retrieval interrupted for user: $username", e)
                throw KeycloakAuthenticationException("Token retrieval interrupted for user: $username", e)
            }

        /**
         * Performs the actual token request to Keycloak.
         */
        private fun performTokenRequest(
            username: String,
            password: String,
        ): String {
            val tokenEndpoint =
                TestContainers.keycloak.authServerUrl +
                    "/realms/eaf-test/protocol/openid-connect/token"

            val requestBody =
                buildString {
                    append("grant_type=").append(urlEncode("password"))
                    append("&client_id=").append(urlEncode(CLIENT_ID))
                    append("&client_secret=").append(urlEncode(CLIENT_SECRET))
                    append("&username=").append(urlEncode(username))
                    append("&password=").append(urlEncode(password))
                    append("&scope=").append(urlEncode("openid profile email"))
                }

            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build()

            logger.debug("Requesting token from Keycloak for user: $username")
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                val errorMessage =
                    "Token request failed for user $username. " +
                        "Status: ${response.statusCode()}, Body: ${response.body()}"
                logger.error(errorMessage)
                throw TokenRetrievalException(errorMessage)
            }

            val tokenResponse = objectMapper.readTree(response.body())
            val accessToken = tokenResponse.get("access_token")?.asText()

            return accessToken?.also {
                logger.info("Successfully retrieved token for user: $username")
            } ?: run {
                val errorMessage = "No access_token in response for user: $username"
                logger.error(errorMessage)
                throw TokenRetrievalException(errorMessage)
            }
        }

        /**
         * Clears the token cache.
         * Useful for test scenarios that require fresh tokens.
         */
        fun clearCache() {
            logger.debug("Clearing token cache")
            tokenCache.clear()
        }

        /**
         * Gets the OIDC discovery URL for external configuration.
         */
        fun getOidcDiscoveryUrl(): String =
            TestContainers.keycloak.authServerUrl +
                "/realms/eaf-test/.well-known/openid_configuration"

        /**
         * URL encodes a string for form data.
         */
        private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

        /**
         * Validates that Keycloak container is accessible and properly configured.
         */
        fun validateKeycloakSetup(): Boolean =
            try {
                val discoveryUrl = getOidcDiscoveryUrl()
                val request =
                    HttpRequest
                        .newBuilder()
                        .uri(URI.create(discoveryUrl))
                        .GET()
                        .timeout(Duration.ofSeconds(10))
                        .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                val isValid = response.statusCode() == 200

                if (isValid) {
                    logger.info("Keycloak setup validation successful")
                } else {
                    logger.warn("Keycloak setup validation failed: ${response.statusCode()}")
                }

                isValid
            } catch (e: java.io.IOException) {
                logger.error("Keycloak setup validation IO error", e)
                false
            } catch (e: java.lang.InterruptedException) {
                logger.error("Keycloak setup validation interrupted", e)
                false
            }
    }

    /**
     * Cached token with expiration tracking.
     */
    private data class CachedToken(
        val token: String,
        val expiresAt: Instant,
    ) {
        fun isValid(): Boolean = Instant.now().isBefore(expiresAt)
    }
}
