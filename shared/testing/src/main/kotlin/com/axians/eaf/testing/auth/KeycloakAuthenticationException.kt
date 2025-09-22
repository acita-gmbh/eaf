package com.axians.eaf.testing.auth

/**
 * Exception thrown when Keycloak authentication operations fail.
 */
class KeycloakAuthenticationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Exception thrown when Keycloak container setup or validation fails.
 */
class KeycloakContainerException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Exception thrown when token retrieval from Keycloak fails.
 */
class TokenRetrievalException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
