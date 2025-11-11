package com.axians.eaf.testing.keycloak

import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

/**
 * Helper for generating JWT tokens from a Keycloak instance via password grant.
 */
object KeycloakTokenGenerator {
    private val restTemplate = RestTemplate()

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
        headers.add("X-Forwarded-Proto", "https")

        val body =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "password")
                add("client_id", clientId)
                add("username", username)
                add("password", password)
            }

        val request = HttpEntity(body, headers)
        val response = restTemplate.postForEntity(tokenUrl, request, Map::class.java)

        @Suppress("UNCHECKED_CAST")
        val responseBody =
            response.body as? Map<String, Any>
                ?: error("Failed to decode Keycloak token response")

        return responseBody["access_token"] as? String
            ?: error("Missing access_token in Keycloak response")
    }
}
