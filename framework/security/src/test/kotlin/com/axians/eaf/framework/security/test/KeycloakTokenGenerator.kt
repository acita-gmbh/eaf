package com.axians.eaf.framework.security.test

import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

/**
 * Alternative JWT token generator using direct HTTP calls to Keycloak token endpoint.
 *
 * Uses Resource Owner Password Credentials Grant (Direct Access Grant) to obtain
 * access tokens for test users.
 *
 * Story 3.3: Testcontainers Keycloak for Integration Tests
 */
object KeycloakTokenGenerator {
    private val restTemplate = RestTemplate()

    /**
     * Generates a JWT token by calling Keycloak token endpoint directly.
     *
     * @param keycloakUrl Keycloak server URL
     * @param realm Keycloak realm name
     * @param clientId Client ID
     * @param username Test user username
     * @param password Test user password
     * @return JWT access token
     */
    fun generateToken(
        keycloakUrl: String,
        realm: String,
        clientId: String,
        username: String,
        password: String,
    ): String {
        val tokenUrl = "$keycloakUrl/realms/$realm/protocol/openid-connect/token"

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val body = LinkedMultiValueMap<String, String>()
        body.add("grant_type", "password")
        body.add("client_id", clientId)
        body.add("username", username)
        body.add("password", password)

        val request = HttpEntity(body, headers)

        val response = restTemplate.postForEntity(tokenUrl, request, Map::class.java)

        @Suppress("UNCHECKED_CAST")
        val responseBody =
            response.body as? Map<String, Any>
                ?: error("Failed to get token from Keycloak")

        return responseBody["access_token"] as? String
            ?: error("No access_token in Keycloak response")
    }
}
