package de.acci.eaf.auth.keycloak

import com.fasterxml.jackson.annotation.JsonProperty
import de.acci.eaf.auth.IdentityProvider
import de.acci.eaf.auth.InvalidTokenException
import de.acci.eaf.auth.TokenClaims
import de.acci.eaf.auth.UserInfo
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant

/**
 * Keycloak implementation of [IdentityProvider].
 *
 * This adapter validates JWTs against Keycloak's JWKS endpoint and extracts
 * claims according to Keycloak's token structure (realm_access.roles,
 * resource_access.{client}.roles, etc.).
 *
 * @property jwtDecoder Spring Security's reactive JWT decoder configured with Keycloak JWKS.
 * @property userInfoClient WebClient for calling Keycloak UserInfo endpoint.
 * @property clientId The OAuth2 client ID for extracting client-specific roles.
 */
public class KeycloakIdentityProvider(
    private val jwtDecoder: ReactiveJwtDecoder,
    private val userInfoClient: WebClient,
    private val clientId: String,
) : IdentityProvider {

    override suspend fun validateToken(token: String): TokenClaims {
        val jwt = try {
            jwtDecoder.decode(token)
                .onErrorMap { e -> mapDecoderException(e) }
                .awaitSingle()
        } catch (e: InvalidTokenException) {
            throw e
        } catch (e: Exception) {
            throw InvalidTokenException.validationFailed("JWT validation failed: ${e.message}", e)
        }

        return extractClaims(jwt)
    }

    override suspend fun getUserInfo(accessToken: String): UserInfo {
        val response = userInfoClient.get()
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .bodyToMono(KeycloakUserInfoResponse::class.java)
            .onErrorMap { e ->
                InvalidTokenException.validationFailed("UserInfo request failed: ${e.message}", e)
            }
            .awaitSingle()

        return UserInfo(
            id = UserId.fromString(response.sub),
            tenantId = response.tenantId?.let { TenantId.fromString(it) }
                ?: throw InvalidTokenException.missingClaim("tenant_id"),
            email = response.email ?: throw InvalidTokenException.missingClaim("email"),
            name = response.name,
            givenName = response.givenName,
            familyName = response.familyName,
            emailVerified = response.emailVerified ?: false,
            roles = extractRolesFromUserInfo(response),
        )
    }

    private fun extractClaims(jwt: Jwt): TokenClaims {
        val subject = jwt.subject
            ?: throw InvalidTokenException.missingClaim("sub")

        val tenantIdClaim = jwt.getClaimAsString("tenant_id")
            ?: throw InvalidTokenException.missingClaim("tenant_id")

        val roles = extractRoles(jwt)

        return TokenClaims(
            subject = UserId.fromString(subject),
            tenantId = TenantId.fromString(tenantIdClaim),
            roles = roles,
            email = jwt.getClaimAsString("email"),
            expiresAt = jwt.expiresAt ?: Instant.now().plusSeconds(3600),
            issuedAt = jwt.issuedAt ?: Instant.now(),
            issuer = jwt.issuer?.toString() ?: "",
        )
    }

    /**
     * Extracts roles from Keycloak JWT structure.
     * Combines realm_access.roles and resource_access.{clientId}.roles.
     */
    private fun extractRoles(jwt: Jwt): Set<String> {
        val roles = mutableSetOf<String>()

        // Extract realm roles from realm_access.roles
        @Suppress("UNCHECKED_CAST")
        val realmAccess = jwt.getClaim<Map<String, Any>>("realm_access")
        if (realmAccess != null) {
            val realmRoles = realmAccess["roles"] as? List<String>
            realmRoles?.let { roles.addAll(it) }
        }

        // Extract client roles from resource_access.{clientId}.roles
        @Suppress("UNCHECKED_CAST")
        val resourceAccess = jwt.getClaim<Map<String, Any>>("resource_access")
        if (resourceAccess != null) {
            @Suppress("UNCHECKED_CAST")
            val clientAccess = resourceAccess[clientId] as? Map<String, Any>
            if (clientAccess != null) {
                val clientRoles = clientAccess["roles"] as? List<String>
                clientRoles?.let { roles.addAll(it) }
            }
        }

        return roles
    }

    private fun extractRolesFromUserInfo(response: KeycloakUserInfoResponse): Set<String> {
        val roles = mutableSetOf<String>()
        response.realmAccess?.roles?.let { roles.addAll(it) }
        response.resourceAccess?.get(clientId)?.roles?.let { roles.addAll(it) }
        return roles
    }

    private fun mapDecoderException(e: Throwable): InvalidTokenException {
        val message = e.message ?: "Unknown error"
        return when {
            message.contains("expired", ignoreCase = true) -> InvalidTokenException.expired(message)
            message.contains("signature", ignoreCase = true) -> InvalidTokenException.invalidSignature(message)
            message.contains("issuer", ignoreCase = true) -> InvalidTokenException.validationFailed(message)
            else -> InvalidTokenException.validationFailed(message, e)
        }
    }
}

/**
 * Data class for Keycloak UserInfo endpoint response.
 *
 * Uses @JsonProperty annotations to map OIDC snake_case claim names
 * to Kotlin camelCase property names.
 */
internal data class KeycloakUserInfoResponse(
    val sub: String,
    val email: String?,
    val name: String?,
    @JsonProperty("given_name")
    val givenName: String?,
    @JsonProperty("family_name")
    val familyName: String?,
    @JsonProperty("email_verified")
    val emailVerified: Boolean?,
    @JsonProperty("tenant_id")
    val tenantId: String?,
    @JsonProperty("realm_access")
    val realmAccess: RealmAccess?,
    @JsonProperty("resource_access")
    val resourceAccess: Map<String, ClientAccess>?,
) {
    data class RealmAccess(val roles: List<String>?)
    data class ClientAccess(val roles: List<String>?)
}
