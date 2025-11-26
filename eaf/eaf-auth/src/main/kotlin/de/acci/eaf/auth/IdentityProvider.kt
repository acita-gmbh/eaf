package de.acci.eaf.auth

/**
 * Identity provider abstraction for authentication.
 *
 * This interface defines the contract for identity providers (Keycloak, Auth0, etc.)
 * following ADR-002 (IdP-Agnostic Authentication). Implementations are located in
 * provider-specific modules (e.g., eaf-auth-keycloak).
 *
 * The EAF framework uses this abstraction to:
 * - Validate access tokens and extract claims
 * - Retrieve user information from the IdP
 *
 * Products choose their IdP implementation via configuration.
 *
 * @see TokenClaims for the extracted claim structure
 * @see UserInfo for user profile information
 * @see InvalidTokenException for validation failures
 */
public interface IdentityProvider {

    /**
     * Validates an access token and extracts claims.
     *
     * This method verifies the token signature against the IdP's JWKS,
     * checks expiration and other validity constraints, and extracts
     * the relevant claims into a [TokenClaims] object.
     *
     * @param token The raw access token (without "Bearer " prefix).
     * @return The extracted and validated token claims.
     * @throws InvalidTokenException if the token is invalid, expired, or cannot be verified.
     */
    public suspend fun validateToken(token: String): TokenClaims

    /**
     * Retrieves user information from the identity provider.
     *
     * This method calls the OIDC UserInfo endpoint to fetch
     * the user's profile information.
     *
     * @param accessToken A valid access token for the user.
     * @return The user's profile information.
     * @throws InvalidTokenException if the access token is invalid.
     */
    public suspend fun getUserInfo(accessToken: String): UserInfo
}
