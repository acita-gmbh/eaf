package com.axians.eaf.framework.security.util

import jakarta.servlet.http.HttpServletRequest

/**
 * Utility for extracting JWT tokens from HTTP requests.
 * Extracted from filters for unit testability (Story 8.6).
 */
object TokenExtractor {
    private const val AUTHORIZATION_HEADER = "Authorization"
    private const val BEARER_PREFIX = "Bearer "

    /**
     * Extracts Bearer token from Authorization header.
     *
     * @param request The HTTP request
     * @return Token string if present, null otherwise
     */
    fun extractBearerToken(request: HttpServletRequest): String? {
        val authHeader = request.getHeader(AUTHORIZATION_HEADER)
        return extractBearerTokenFromHeader(authHeader)
    }

    /**
     * Extracts Bearer token from Authorization header value.
     *
     * @param authHeader The Authorization header value
     * @return Token string if valid Bearer token, null otherwise
     */
    fun extractBearerTokenFromHeader(authHeader: String?): String? =
        if (authHeader?.startsWith(BEARER_PREFIX) == true) {
            authHeader.substring(BEARER_PREFIX.length)
        } else {
            null
        }
}
