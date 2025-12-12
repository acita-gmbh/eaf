# Technical Specification: Keycloak UI Integration

**Version:** 1.0
**Date:** 2025-11-29
**Status:** Draft
**Author:** Claude (Technical Specification)

---

## 1. Executive Summary

This specification describes the technical implementation of an integrated user management system that exposes Keycloak functionality through a custom UI, eliminating the need for customer admins to use the native Keycloak Admin Console.

**Architecture Decision:** Integration via Keycloak Admin REST API into the DCM portal (MVP) with later extension to a separate Control Plane (Growth phase).

---

## 2. Keycloak Admin REST API - Detailed Analysis

### 2.1 API Overview

The Keycloak Admin REST API provides full access to all administrative functions:

| API Area | Base Path | Relevance for DCM |
|----------|-----------|-------------------|
| Users | `/admin/realms/{realm}/users` | High (FR4, FR5, FR6) |
| Roles | `/admin/realms/{realm}/roles` | High (FR5, FR8) |
| Groups | `/admin/realms/{realm}/groups` | Medium (Tenant structure) |
| Clients | `/admin/realms/{realm}/clients` | Low (Setup) |
| Sessions | `/admin/realms/{realm}/sessions` | Medium (Security) |
| Events | `/admin/realms/{realm}/events` | High (Audit) |

### 2.2 Authentication against Admin API

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   DCM Backend  │────►│    Keycloak     │────►│  Keycloak       │
│   Service       │     │    Token        │     │  Admin API      │
│   Account       │     │    Endpoint     │     │                 │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │                       │
        │  client_credentials   │   access_token        │
        │  grant                │   (service account)   │
        └───────────────────────┴───────────────────────┘
```

**Service Account Setup in Keycloak:**

```json
{
  "clientId": "dcm-admin-service",
  "serviceAccountsEnabled": true,
  "clientAuthenticatorType": "client-secret",
  "secret": "${KEYCLOAK_ADMIN_SECRET}",
  "directAccessGrantsEnabled": false,
  "publicClient": false,
  "protocol": "openid-connect"
}
```

**Required Realm Roles for Service Account:**

| Role | Permissions |
|------|-------------|
| `view-users` | List users, read details |
| `manage-users` | Create, update, delete users |
| `view-realm` | Read realm settings |
| `query-users` | User search |
| `view-events` | Read audit events |

### 2.3 API Endpoints in Detail

#### 2.3.1 List Users

```http
GET /admin/realms/{realm}/users
Authorization: Bearer {service_account_token}

Query Parameters:
- search: string (Name, email search)
- first: int (Pagination offset)
- max: int (Pagination limit, default 100)
- enabled: boolean
- email: string (exact search)
- username: string
- briefRepresentation: boolean (reduced fields)
```

**Response:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "username": "max.mustermann",
    "email": "max.mustermann@example.com",
    "firstName": "Max",
    "lastName": "Mustermann",
    "enabled": true,
    "emailVerified": true,
    "createdTimestamp": 1700000000000,
    "attributes": {
      "tenant_id": ["550e8400-e29b-41d4-a716-446655440001"]
    },
    "requiredActions": []
  }
]
```

#### 2.3.2 Create User

```http
POST /admin/realms/{realm}/users
Authorization: Bearer {service_account_token}
Content-Type: application/json

{
  "username": "new.user",
  "email": "new.user@example.com",
  "firstName": "New",
  "lastName": "User",
  "enabled": true,
  "emailVerified": false,
  "attributes": {
    "tenant_id": ["550e8400-e29b-41d4-a716-446655440001"]
  },
  "requiredActions": ["UPDATE_PASSWORD", "VERIFY_EMAIL"]
}
```

**Response:** `201 Created` with `Location` header

#### 2.3.3 Deactivate User

```http
PUT /admin/realms/{realm}/users/{userId}
Authorization: Bearer {service_account_token}
Content-Type: application/json

{
  "enabled": false
}
```

#### 2.3.4 Password Reset Email

```http
PUT /admin/realms/{realm}/users/{userId}/execute-actions-email
Authorization: Bearer {service_account_token}
Content-Type: application/json

["UPDATE_PASSWORD"]

Query Parameters:
- lifespan: int (seconds, default 43200 = 12h)
- redirect_uri: string (optional)
- client_id: string (for redirect)
```

#### 2.3.5 Assign Roles

```http
POST /admin/realms/{realm}/users/{userId}/role-mappings/realm
Authorization: Bearer {service_account_token}
Content-Type: application/json

[
  {
    "id": "role-uuid",
    "name": "dcm-admin"
  }
]
```

#### 2.3.6 Sessions beenden (Force Logout)

```http
DELETE /admin/realms/{realm}/users/{userId}/sessions
Authorization: Bearer {service_account_token}
```

#### 2.3.7 Retrieve Audit Events

```http
GET /admin/realms/{realm}/events
Authorization: Bearer {service_account_token}

Query Parameters:
- type: string[] (LOGIN, LOGOUT, UPDATE_PASSWORD, etc.)
- user: string (userId)
- dateFrom: string (ISO 8601)
- dateTo: string (ISO 8601)
- first: int
- max: int
```

---

## 3. Backend Architecture

### 3.1 Module Structure

```
eaf/
├── eaf-auth/                          # Existing: IdP-agnostic interfaces
│   └── src/main/kotlin/de/acci/eaf/auth/
│       ├── IdentityProvider.kt        # Existing
│       ├── UserInfo.kt                # Existing
│       └── admin/                     # NEW: Admin interfaces
│           ├── UserAdminPort.kt
│           ├── RoleAdminPort.kt
│           ├── SessionAdminPort.kt
│           └── AuditQueryPort.kt
│
├── eaf-auth-keycloak/                 # Existing: Keycloak implementation
│   └── src/main/kotlin/de/acci/eaf/auth/keycloak/
│       ├── KeycloakIdentityProvider.kt    # Existing
│       └── admin/                         # NEW: Admin implementation
│           ├── KeycloakAdminClient.kt
│           ├── KeycloakUserAdminAdapter.kt
│           ├── KeycloakRoleAdminAdapter.kt
│           ├── KeycloakSessionAdminAdapter.kt
│           ├── KeycloakAuditQueryAdapter.kt
│           └── KeycloakAdminProperties.kt
│
dcm/
├── dcm-application/                  # NEW: Use Cases
│   └── src/main/kotlin/de/acci/dcm/application/
│       └── admin/
│           ├── InviteUserUseCase.kt
│           ├── DeactivateUserUseCase.kt
│           ├── AssignRoleUseCase.kt
│           ├── ListTenantUsersUseCase.kt
│           ├── SendPasswordResetUseCase.kt
│           └── ForceLogoutUseCase.kt
│
├── dcm-api/                          # NEW: REST Controller
│   └── src/main/kotlin/de/acci/dcm/api/
│       └── admin/
│           ├── UserAdminController.kt
│           ├── UserAdminDto.kt
│           └── UserAdminMapper.kt
```

### 3.2 Interface Definitions (eaf-auth)

```kotlin
// eaf-auth/src/main/kotlin/de/acci/eaf/auth/admin/UserAdminPort.kt
package de.acci.eaf.auth.admin

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Port for user administration operations.
 *
 * All operations are tenant-scoped - implementations MUST ensure
 * that only users belonging to the specified tenant are affected.
 */
public interface UserAdminPort {

    /**
     * Lists all users belonging to the specified tenant.
     *
     * @param tenantId The tenant to list users for
     * @param pagination Pagination parameters
     * @return Paginated list of users
     */
    public suspend fun listUsers(
        tenantId: TenantId,
        pagination: PaginationRequest = PaginationRequest()
    ): PaginatedResult<ManagedUser>

    /**
     * Retrieves a single user by ID.
     *
     * @param tenantId The tenant the user must belong to
     * @param userId The user ID
     * @return The user or null if not found/not in tenant
     */
    public suspend fun getUser(
        tenantId: TenantId,
        userId: UserId
    ): ManagedUser?

    /**
     * Creates a new user in the tenant.
     *
     * @param tenantId The tenant to create the user in
     * @param command The user creation command
     * @return Result with created user ID or error
     */
    public suspend fun createUser(
        tenantId: TenantId,
        command: CreateUserCommand
    ): Result<UserId, UserAdminError>

    /**
     * Updates an existing user.
     *
     * @param tenantId The tenant the user must belong to
     * @param userId The user ID
     * @param command The update command
     * @return Result with success or error
     */
    public suspend fun updateUser(
        tenantId: TenantId,
        userId: UserId,
        command: UpdateUserCommand
    ): Result<Unit, UserAdminError>

    /**
     * Deactivates a user (sets enabled=false).
     * Does NOT delete the user to preserve audit trail.
     *
     * @param tenantId The tenant the user must belong to
     * @param userId The user ID
     * @return Result with success or error
     */
    public suspend fun deactivateUser(
        tenantId: TenantId,
        userId: UserId
    ): Result<Unit, UserAdminError>

    /**
     * Reactivates a previously deactivated user.
     *
     * @param tenantId The tenant the user must belong to
     * @param userId The user ID
     * @return Result with success or error
     */
    public suspend fun reactivateUser(
        tenantId: TenantId,
        userId: UserId
    ): Result<Unit, UserAdminError>

    /**
     * Sends a password reset email to the user.
     *
     * @param tenantId The tenant the user must belong to
     * @param userId The user ID
     * @param options Reset options (link lifetime, redirect)
     * @return Result with success or error
     */
    public suspend fun sendPasswordResetEmail(
        tenantId: TenantId,
        userId: UserId,
        options: PasswordResetOptions = PasswordResetOptions()
    ): Result<Unit, UserAdminError>

    /**
     * Searches users by email or name.
     *
     * @param tenantId The tenant to search in
     * @param query Search query (matches email, firstName, lastName)
     * @param pagination Pagination parameters
     * @return Matching users
     */
    public suspend fun searchUsers(
        tenantId: TenantId,
        query: String,
        pagination: PaginationRequest = PaginationRequest()
    ): PaginatedResult<ManagedUser>
}

/**
 * Represents a user managed by the admin interface.
 */
public data class ManagedUser(
    val id: UserId,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val enabled: Boolean,
    val emailVerified: Boolean,
    val createdAt: Instant,
    val lastLoginAt: Instant?,
    val roles: Set<String>,
    val attributes: Map<String, List<String>>
)

public data class CreateUserCommand(
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val roles: Set<String> = emptySet(),
    val sendInvitationEmail: Boolean = true
)

public data class UpdateUserCommand(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null
)

public data class PasswordResetOptions(
    val linkLifetimeSeconds: Int = 43200, // 12 hours
    val redirectUri: String? = null
)

public data class PaginationRequest(
    val offset: Int = 0,
    val limit: Int = 20
)

public data class PaginatedResult<T>(
    val items: List<T>,
    val totalCount: Int,
    val offset: Int,
    val limit: Int
) {
    val hasMore: Boolean get() = offset + items.size < totalCount
}

public sealed interface UserAdminError {
    public data class UserNotFound(val userId: UserId) : UserAdminError
    public data class UserNotInTenant(val userId: UserId, val tenantId: TenantId) : UserAdminError
    public data class EmailAlreadyExists(val email: String) : UserAdminError
    public data class ValidationFailed(val violations: List<String>) : UserAdminError
    public data class PermissionDenied(val reason: String) : UserAdminError
    public data class IdpError(val message: String, val cause: Throwable?) : UserAdminError
}
```

```kotlin
// eaf-auth/src/main/kotlin/de/acci/eaf/auth/admin/RoleAdminPort.kt
package de.acci.eaf.auth.admin

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Port for role administration operations.
 */
public interface RoleAdminPort {

    /**
     * Lists all available roles for the tenant.
     */
    public suspend fun listAvailableRoles(tenantId: TenantId): List<RoleInfo>

    /**
     * Gets the roles assigned to a user.
     */
    public suspend fun getUserRoles(
        tenantId: TenantId,
        userId: UserId
    ): Set<String>

    /**
     * Assigns roles to a user (additive).
     */
    public suspend fun assignRoles(
        tenantId: TenantId,
        userId: UserId,
        roles: Set<String>
    ): Result<Unit, RoleAdminError>

    /**
     * Removes roles from a user.
     */
    public suspend fun removeRoles(
        tenantId: TenantId,
        userId: UserId,
        roles: Set<String>
    ): Result<Unit, RoleAdminError>

    /**
     * Sets the exact role set for a user (replaces existing).
     */
    public suspend fun setRoles(
        tenantId: TenantId,
        userId: UserId,
        roles: Set<String>
    ): Result<Unit, RoleAdminError>
}

public data class RoleInfo(
    val name: String,
    val description: String?,
    val composite: Boolean
)

public sealed interface RoleAdminError {
    public data class UserNotFound(val userId: UserId) : RoleAdminError
    public data class RoleNotFound(val roleName: String) : RoleAdminError
    public data class PermissionDenied(val reason: String) : RoleAdminError
    public data class IdpError(val message: String, val cause: Throwable?) : RoleAdminError
}
```

```kotlin
// eaf-auth/src/main/kotlin/de/acci/eaf/auth/admin/SessionAdminPort.kt
package de.acci.eaf.auth.admin

import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId

/**
 * Port for session management operations.
 */
public interface SessionAdminPort {

    /**
     * Lists active sessions for a user.
     */
    public suspend fun getUserSessions(
        tenantId: TenantId,
        userId: UserId
    ): List<SessionInfo>

    /**
     * Terminates all sessions for a user (force logout).
     */
    public suspend fun terminateUserSessions(
        tenantId: TenantId,
        userId: UserId
    ): Result<Int, SessionAdminError>

    /**
     * Terminates a specific session.
     */
    public suspend fun terminateSession(
        tenantId: TenantId,
        sessionId: String
    ): Result<Unit, SessionAdminError>
}

public data class SessionInfo(
    val id: String,
    val userId: UserId,
    val ipAddress: String?,
    val userAgent: String?,
    val startedAt: Instant,
    val lastAccessedAt: Instant
)

public sealed interface SessionAdminError {
    public data class UserNotFound(val userId: UserId) : SessionAdminError
    public data class SessionNotFound(val sessionId: String) : SessionAdminError
    public data class PermissionDenied(val reason: String) : SessionAdminError
    public data class IdpError(val message: String, val cause: Throwable?) : SessionAdminError
}
```

### 3.3 Keycloak Implementation (eaf-auth-keycloak)

```kotlin
// eaf-auth-keycloak/src/main/kotlin/de/acci/eaf/auth/keycloak/admin/KeycloakAdminClient.kt
package de.acci.eaf.auth.keycloak.admin

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchange
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Low-level client for Keycloak Admin REST API.
 *
 * Handles:
 * - Service account token acquisition and caching
 * - Token refresh before expiration
 * - HTTP communication with Keycloak Admin API
 *
 * This is an internal class - use the higher-level adapters.
 */
internal class KeycloakAdminClient(
    private val webClient: WebClient,
    private val properties: KeycloakAdminProperties
) {

    @Volatile
    private var cachedToken: TokenCache? = null

    private data class TokenCache(
        val accessToken: String,
        val expiresAt: Instant
    )

    /**
     * Gets a valid access token, refreshing if necessary.
     */
    suspend fun getAccessToken(): String {
        val cached = cachedToken
        val now = Instant.now()

        // Return cached token if still valid (with 30s buffer)
        if (cached != null && cached.expiresAt.isAfter(now.plusSeconds(30))) {
            return cached.accessToken
        }

        // Acquire new token
        return refreshToken()
    }

    private suspend fun refreshToken(): String {
        logger.debug { "Refreshing Keycloak admin service account token" }

        val response = webClient.post()
            .uri("${properties.serverUrl}/realms/${properties.realm}/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                .with("client_id", properties.clientId)
                .with("client_secret", properties.clientSecret))
            .retrieve()
            .awaitBody<TokenResponse>()

        val expiresAt = Instant.now().plusSeconds(response.expiresIn.toLong())
        cachedToken = TokenCache(response.accessToken, expiresAt)

        logger.debug { "Acquired admin token, expires at $expiresAt" }

        return response.accessToken
    }

    /**
     * Makes an authenticated GET request to the Admin API.
     */
    suspend inline fun <reified T> get(path: String, queryParams: Map<String, Any?> = emptyMap()): T {
        val token = getAccessToken()

        return webClient.get()
            .uri { builder ->
                builder.path("${properties.serverUrl}/admin/realms/${properties.realm}$path")
                queryParams.forEach { (key, value) ->
                    if (value != null) {
                        builder.queryParam(key, value)
                    }
                }
                builder.build()
            }
            .header("Authorization", "Bearer $token")
            .retrieve()
            .awaitBody<T>()
    }

    /**
     * Makes an authenticated POST request to the Admin API.
     */
    suspend inline fun <reified T, reified R> post(path: String, body: T): R {
        val token = getAccessToken()

        return webClient.post()
            .uri("${properties.serverUrl}/admin/realms/${properties.realm}$path")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body as Any)
            .retrieve()
            .awaitBody<R>()
    }

    /**
     * Makes an authenticated POST request expecting 201 Created with Location header.
     */
    suspend inline fun <reified T> postForLocation(path: String, body: T): String? {
        val token = getAccessToken()

        return webClient.post()
            .uri("${properties.serverUrl}/admin/realms/${properties.realm}$path")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body as Any)
            .awaitExchange { response ->
                response.headers().header("Location").firstOrNull()
            }
    }

    /**
     * Makes an authenticated PUT request.
     */
    suspend inline fun <reified T> put(path: String, body: T) {
        val token = getAccessToken()

        webClient.put()
            .uri("${properties.serverUrl}/admin/realms/${properties.realm}$path")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body as Any)
            .retrieve()
            .awaitBody<Unit>()
    }

    /**
     * Makes an authenticated DELETE request.
     */
    suspend fun delete(path: String) {
        val token = getAccessToken()

        webClient.delete()
            .uri("${properties.serverUrl}/admin/realms/${properties.realm}$path")
            .header("Authorization", "Bearer $token")
            .retrieve()
            .awaitBody<Unit>()
    }

    private data class TokenResponse(
        val access_token: String,
        val expires_in: Int,
        val token_type: String
    ) {
        val accessToken: String get() = access_token
        val expiresIn: Int get() = expires_in
    }
}
```

```kotlin
// eaf-auth-keycloak/src/main/kotlin/de/acci/eaf/auth/keycloak/admin/KeycloakUserAdminAdapter.kt
package de.acci.eaf.auth.keycloak.admin

import de.acci.eaf.auth.admin.*
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Keycloak implementation of UserAdminPort.
 *
 * IMPORTANT: All operations verify tenant membership before execution.
 * Users are associated with tenants via the "tenant_id" attribute in Keycloak.
 */
public class KeycloakUserAdminAdapter(
    private val client: KeycloakAdminClient,
    private val properties: KeycloakAdminProperties
) : UserAdminPort {

    override suspend fun listUsers(
        tenantId: TenantId,
        pagination: PaginationRequest
    ): PaginatedResult<ManagedUser> {
        logger.debug { "Listing users for tenant $tenantId" }

        // Keycloak doesn't support direct attribute filtering in list,
        // so we fetch and filter (with reasonable page size)
        val allUsers = client.get<List<KeycloakUserRepresentation>>(
            path = "/users",
            queryParams = mapOf(
                "first" to 0,
                "max" to 1000, // Fetch larger batch for filtering
                "briefRepresentation" to false
            )
        )

        // Filter by tenant_id attribute
        val tenantUsers = allUsers.filter { user ->
            user.attributes?.get("tenant_id")?.contains(tenantId.value.toString()) == true
        }

        val paged = tenantUsers
            .drop(pagination.offset)
            .take(pagination.limit)
            .map { it.toManagedUser() }

        return PaginatedResult(
            items = paged,
            totalCount = tenantUsers.size,
            offset = pagination.offset,
            limit = pagination.limit
        )
    }

    override suspend fun getUser(
        tenantId: TenantId,
        userId: UserId
    ): ManagedUser? {
        logger.debug { "Getting user $userId for tenant $tenantId" }

        return try {
            val user = client.get<KeycloakUserRepresentation>("/users/${userId.value}")

            // Verify tenant membership
            if (user.belongsToTenant(tenantId)) {
                user.toManagedUser()
            } else {
                logger.warn { "User $userId does not belong to tenant $tenantId" }
                null
            }
        } catch (e: Exception) {
            logger.debug { "User $userId not found: ${e.message}" }
            null
        }
    }

    override suspend fun createUser(
        tenantId: TenantId,
        command: CreateUserCommand
    ): Result<UserId, UserAdminError> {
        logger.info { "Creating user ${command.email} for tenant $tenantId" }

        // Check if email already exists
        val existing = client.get<List<KeycloakUserRepresentation>>(
            path = "/users",
            queryParams = mapOf("email" to command.email, "exact" to true)
        )

        if (existing.isNotEmpty()) {
            return Result.failure(UserAdminError.EmailAlreadyExists(command.email))
        }

        val representation = KeycloakUserRepresentation(
            username = command.email, // Use email as username
            email = command.email,
            firstName = command.firstName,
            lastName = command.lastName,
            enabled = true,
            emailVerified = false,
            attributes = mapOf("tenant_id" to listOf(tenantId.value.toString())),
            requiredActions = if (command.sendInvitationEmail) {
                listOf("UPDATE_PASSWORD", "VERIFY_EMAIL")
            } else {
                emptyList()
            }
        )

        return try {
            val location = client.postForLocation("/users", representation)
            val userId = location?.substringAfterLast("/")?.let { UserId(UUID.fromString(it)) }
                ?: return Result.failure(UserAdminError.IdpError("No user ID in response", null))

            // Assign initial roles if specified
            if (command.roles.isNotEmpty()) {
                // Role assignment would go here
            }

            // Send invitation email
            if (command.sendInvitationEmail) {
                client.put("/users/${userId.value}/execute-actions-email", listOf("UPDATE_PASSWORD"))
            }

            logger.info { "Created user $userId for tenant $tenantId" }
            Result.success(userId)
        } catch (e: Exception) {
            logger.error(e) { "Failed to create user ${command.email}" }
            Result.failure(UserAdminError.IdpError(e.message ?: "Unknown error", e))
        }
    }

    override suspend fun updateUser(
        tenantId: TenantId,
        userId: UserId,
        command: UpdateUserCommand
    ): Result<Unit, UserAdminError> {
        logger.info { "Updating user $userId for tenant $tenantId" }

        // Verify tenant membership
        val existing = getUser(tenantId, userId)
            ?: return Result.failure(UserAdminError.UserNotInTenant(userId, tenantId))

        val updates = mutableMapOf<String, Any?>()
        command.firstName?.let { updates["firstName"] = it }
        command.lastName?.let { updates["lastName"] = it }
        command.email?.let { updates["email"] = it }

        return try {
            client.put("/users/${userId.value}", updates)
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update user $userId" }
            Result.failure(UserAdminError.IdpError(e.message ?: "Unknown error", e))
        }
    }

    override suspend fun deactivateUser(
        tenantId: TenantId,
        userId: UserId
    ): Result<Unit, UserAdminError> {
        logger.info { "Deactivating user $userId for tenant $tenantId" }

        // Verify tenant membership
        val existing = getUser(tenantId, userId)
            ?: return Result.failure(UserAdminError.UserNotInTenant(userId, tenantId))

        return try {
            client.put("/users/${userId.value}", mapOf("enabled" to false))
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to deactivate user $userId" }
            Result.failure(UserAdminError.IdpError(e.message ?: "Unknown error", e))
        }
    }

    override suspend fun reactivateUser(
        tenantId: TenantId,
        userId: UserId
    ): Result<Unit, UserAdminError> {
        logger.info { "Reactivating user $userId for tenant $tenantId" }

        // Verify tenant membership
        val existing = getUser(tenantId, userId)
            ?: return Result.failure(UserAdminError.UserNotInTenant(userId, tenantId))

        return try {
            client.put("/users/${userId.value}", mapOf("enabled" to true))
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to reactivate user $userId" }
            Result.failure(UserAdminError.IdpError(e.message ?: "Unknown error", e))
        }
    }

    override suspend fun sendPasswordResetEmail(
        tenantId: TenantId,
        userId: UserId,
        options: PasswordResetOptions
    ): Result<Unit, UserAdminError> {
        logger.info { "Sending password reset email for user $userId" }

        // Verify tenant membership
        val existing = getUser(tenantId, userId)
            ?: return Result.failure(UserAdminError.UserNotInTenant(userId, tenantId))

        return try {
            val queryParams = buildString {
                append("?lifespan=${options.linkLifetimeSeconds}")
                options.redirectUri?.let { append("&redirect_uri=$it") }
            }
            client.put("/users/${userId.value}/execute-actions-email$queryParams", listOf("UPDATE_PASSWORD"))
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to send password reset email for user $userId" }
            Result.failure(UserAdminError.IdpError(e.message ?: "Unknown error", e))
        }
    }

    override suspend fun searchUsers(
        tenantId: TenantId,
        query: String,
        pagination: PaginationRequest
    ): PaginatedResult<ManagedUser> {
        logger.debug { "Searching users in tenant $tenantId with query '$query'" }

        val results = client.get<List<KeycloakUserRepresentation>>(
            path = "/users",
            queryParams = mapOf(
                "search" to query,
                "first" to 0,
                "max" to 500
            )
        )

        // Filter by tenant
        val tenantUsers = results.filter { it.belongsToTenant(tenantId) }

        val paged = tenantUsers
            .drop(pagination.offset)
            .take(pagination.limit)
            .map { it.toManagedUser() }

        return PaginatedResult(
            items = paged,
            totalCount = tenantUsers.size,
            offset = pagination.offset,
            limit = pagination.limit
        )
    }

    // Helper methods

    private fun KeycloakUserRepresentation.belongsToTenant(tenantId: TenantId): Boolean {
        return attributes?.get("tenant_id")?.contains(tenantId.value.toString()) == true
    }

    private fun KeycloakUserRepresentation.toManagedUser(): ManagedUser {
        return ManagedUser(
            id = UserId(UUID.fromString(id)),
            email = email ?: "",
            firstName = firstName,
            lastName = lastName,
            enabled = enabled ?: true,
            emailVerified = emailVerified ?: false,
            createdAt = createdTimestamp?.let { Instant.ofEpochMilli(it) } ?: Instant.EPOCH,
            lastLoginAt = null, // Would need to query sessions
            roles = emptySet(), // Would need separate role query
            attributes = attributes ?: emptyMap()
        )
    }
}

/**
 * Keycloak User Representation DTO.
 */
internal data class KeycloakUserRepresentation(
    val id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val enabled: Boolean? = null,
    val emailVerified: Boolean? = null,
    val createdTimestamp: Long? = null,
    val attributes: Map<String, List<String>>? = null,
    val requiredActions: List<String>? = null,
    val realmRoles: List<String>? = null
)
```

```kotlin
// eaf-auth-keycloak/src/main/kotlin/de/acci/eaf/auth/keycloak/admin/KeycloakAdminProperties.kt
package de.acci.eaf.auth.keycloak.admin

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "eaf.auth.keycloak.admin")
public data class KeycloakAdminProperties(
    /** Keycloak server URL (e.g., https://keycloak.example.com) */
    val serverUrl: String,

    /** Realm name */
    val realm: String,

    /** Service account client ID */
    val clientId: String,

    /** Service account client secret */
    val clientSecret: String
)
```

### 3.4 Application Layer (dcm-application)

```kotlin
// dcm-application/src/main/kotlin/de/acci/dcm/application/admin/InviteUserUseCase.kt
package de.acci.dcm.application.admin

import de.acci.eaf.auth.admin.CreateUserCommand
import de.acci.eaf.auth.admin.UserAdminPort
import de.acci.eaf.auth.admin.UserAdminError
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.tenant.TenantContext
import de.acci.dcm.domain.admin.UserInvitedEvent
import de.acci.dcm.domain.admin.UserInvitationRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Use case for inviting a new user to the tenant.
 *
 * This use case:
 * 1. Validates the requesting user has ADMIN role
 * 2. Creates the user in Keycloak with tenant_id attribute
 * 3. Records a UserInvitedEvent for audit
 * 4. Triggers invitation email via Keycloak
 */
public class InviteUserUseCase(
    private val userAdminPort: UserAdminPort,
    private val eventPublisher: DomainEventPublisher,
    private val clock: java.time.Clock
) {

    public suspend fun execute(command: InviteUserCommand): Result<UserId, InviteUserError> {
        val tenantId = TenantContext.current()

        logger.info { "Inviting user ${command.email} to tenant $tenantId" }

        // Create user in Keycloak
        val keycloakCommand = CreateUserCommand(
            email = command.email,
            firstName = command.firstName,
            lastName = command.lastName,
            roles = command.roles,
            sendInvitationEmail = true
        )

        return when (val result = userAdminPort.createUser(tenantId, keycloakCommand)) {
            is Result.Success -> {
                // Publish audit event
                val event = UserInvitedEvent(
                    tenantId = tenantId,
                    userId = result.value,
                    email = command.email,
                    invitedBy = command.invitedBy,
                    roles = command.roles,
                    occurredAt = clock.instant()
                )
                eventPublisher.publish(event)

                Result.success(result.value)
            }
            is Result.Failure -> {
                logger.warn { "Failed to invite user ${command.email}: ${result.error}" }
                Result.failure(result.error.toInviteUserError())
            }
        }
    }

    private fun UserAdminError.toInviteUserError(): InviteUserError {
        return when (this) {
            is UserAdminError.EmailAlreadyExists -> InviteUserError.EmailAlreadyExists(email)
            is UserAdminError.ValidationFailed -> InviteUserError.ValidationFailed(violations)
            is UserAdminError.PermissionDenied -> InviteUserError.PermissionDenied(reason)
            is UserAdminError.IdpError -> InviteUserError.SystemError(message)
            else -> InviteUserError.SystemError("Unexpected error")
        }
    }
}

public data class InviteUserCommand(
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val roles: Set<String>,
    val invitedBy: UserId
)

public sealed interface InviteUserError {
    public data class EmailAlreadyExists(val email: String) : InviteUserError
    public data class ValidationFailed(val violations: List<String>) : InviteUserError
    public data class PermissionDenied(val reason: String) : InviteUserError
    public data class SystemError(val message: String) : InviteUserError
}
```

---

## 4. API-Design (dcm-api)

### 4.1 REST-Endpunkte

```yaml
openapi: 3.0.3
info:
  title: DCM User Administration API
  version: 1.0.0

paths:
  /api/admin/users:
    get:
      summary: List users in tenant
      security:
        - bearerAuth: []
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 0
        - name: size
          in: query
          schema:
            type: integer
            default: 20
            maximum: 100
        - name: search
          in: query
          schema:
            type: string
      responses:
        '200':
          description: Paginated user list
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserListResponse'
        '403':
          description: Forbidden - requires ADMIN role

    post:
      summary: Invite new user
      security:
        - bearerAuth: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/InviteUserRequest'
      responses:
        '201':
          description: User invited
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserResponse'
        '400':
          description: Validation error
        '409':
          description: Email already exists

  /api/admin/users/{userId}:
    get:
      summary: Get user details
      security:
        - bearerAuth: []
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: User details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserResponse'
        '404':
          description: User not found

    put:
      summary: Update user
      security:
        - bearerAuth: []
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdateUserRequest'
      responses:
        '200':
          description: User updated
        '404':
          description: User not found

  /api/admin/users/{userId}/deactivate:
    post:
      summary: Deactivate user
      security:
        - bearerAuth: []
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: User deactivated
        '404':
          description: User not found

  /api/admin/users/{userId}/reactivate:
    post:
      summary: Reactivate user
      security:
        - bearerAuth: []
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: User reactivated
        '404':
          description: User not found

  /api/admin/users/{userId}/password-reset:
    post:
      summary: Send password reset email
      security:
        - bearerAuth: []
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Password reset email sent
        '404':
          description: User not found

  /api/admin/users/{userId}/roles:
    get:
      summary: Get user roles
      security:
        - bearerAuth: []
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: User roles
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserRolesResponse'

    put:
      summary: Set user roles
      security:
        - bearerAuth: []
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/SetRolesRequest'
      responses:
        '200':
          description: Roles updated
        '404':
          description: User not found

  /api/admin/users/{userId}/sessions:
    get:
      summary: Get user sessions
      security:
        - bearerAuth: []
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: User sessions
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SessionListResponse'

    delete:
      summary: Terminate all user sessions (force logout)
      security:
        - bearerAuth: []
      parameters:
        - name: userId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Sessions terminated
        '404':
          description: User not found

  /api/admin/roles:
    get:
      summary: List available roles
      security:
        - bearerAuth: []
      responses:
        '200':
          description: Available roles
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RoleListResponse'

components:
  schemas:
    InviteUserRequest:
      type: object
      required:
        - email
      properties:
        email:
          type: string
          format: email
        firstName:
          type: string
          maxLength: 100
        lastName:
          type: string
          maxLength: 100
        roles:
          type: array
          items:
            type: string
          default: ["user"]

    UpdateUserRequest:
      type: object
      properties:
        firstName:
          type: string
          maxLength: 100
        lastName:
          type: string
          maxLength: 100

    UserResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        email:
          type: string
        firstName:
          type: string
        lastName:
          type: string
        enabled:
          type: boolean
        emailVerified:
          type: boolean
        createdAt:
          type: string
          format: date-time
        lastLoginAt:
          type: string
          format: date-time
        roles:
          type: array
          items:
            type: string

    UserListResponse:
      type: object
      properties:
        items:
          type: array
          items:
            $ref: '#/components/schemas/UserResponse'
        totalCount:
          type: integer
        page:
          type: integer
        size:
          type: integer
        hasMore:
          type: boolean

    SetRolesRequest:
      type: object
      required:
        - roles
      properties:
        roles:
          type: array
          items:
            type: string

    UserRolesResponse:
      type: object
      properties:
        userId:
          type: string
          format: uuid
        roles:
          type: array
          items:
            type: string

    SessionListResponse:
      type: object
      properties:
        sessions:
          type: array
          items:
            $ref: '#/components/schemas/SessionInfo'

    SessionInfo:
      type: object
      properties:
        id:
          type: string
        ipAddress:
          type: string
        userAgent:
          type: string
        startedAt:
          type: string
          format: date-time
        lastAccessedAt:
          type: string
          format: date-time

    RoleListResponse:
      type: object
      properties:
        roles:
          type: array
          items:
            $ref: '#/components/schemas/RoleInfo'

    RoleInfo:
      type: object
      properties:
        name:
          type: string
        description:
          type: string

  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

### 4.2 Controller-Implementation

```kotlin
// dcm-api/src/main/kotlin/de/acci/dcm/api/admin/UserAdminController.kt
package de.acci.dcm.api.admin

import de.acci.dcm.application.admin.*
import de.acci.eaf.auth.UserInfo
import de.acci.eaf.auth.admin.ManagedUser
import de.acci.eaf.auth.admin.PaginationRequest
import de.acci.eaf.auth.admin.UserAdminPort
import de.acci.eaf.auth.admin.RoleAdminPort
import de.acci.eaf.auth.admin.SessionAdminPort
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.tenant.TenantContext
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController(
    private val userAdminPort: UserAdminPort,
    private val roleAdminPort: RoleAdminPort,
    private val sessionAdminPort: SessionAdminPort,
    private val inviteUserUseCase: InviteUserUseCase,
    private val deactivateUserUseCase: DeactivateUserUseCase,
    private val assignRoleUseCase: AssignRoleUseCase
) {

    @GetMapping("/users")
    public suspend fun listUsers(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
        @RequestParam(required = false) search: String?
    ): UserListResponse {
        val tenantId = TenantContext.current()
        val pagination = PaginationRequest(offset = page * size, limit = size)

        val result = if (search.isNullOrBlank()) {
            userAdminPort.listUsers(tenantId, pagination)
        } else {
            userAdminPort.searchUsers(tenantId, search, pagination)
        }

        return UserListResponse(
            items = result.items.map { it.toResponse() },
            totalCount = result.totalCount,
            page = page,
            size = size,
            hasMore = result.hasMore
        )
    }

    @GetMapping("/users/{userId}")
    public suspend fun getUser(
        @PathVariable userId: UUID
    ): ResponseEntity<UserResponse> {
        val tenantId = TenantContext.current()

        val user = userAdminPort.getUser(tenantId, UserId(userId))
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(user.toResponse())
    }

    @PostMapping("/users")
    public suspend fun inviteUser(
        @Valid @RequestBody request: InviteUserRequest,
        @AuthenticationPrincipal userInfo: UserInfo
    ): ResponseEntity<UserResponse> {
        val command = InviteUserCommand(
            email = request.email,
            firstName = request.firstName,
            lastName = request.lastName,
            roles = request.roles?.toSet() ?: setOf("user"),
            invitedBy = userInfo.userId
        )

        return when (val result = inviteUserUseCase.execute(command)) {
            is Result.Success -> {
                val tenantId = TenantContext.current()
                val user = userAdminPort.getUser(tenantId, result.value)!!
                ResponseEntity.status(HttpStatus.CREATED).body(user.toResponse())
            }
            is Result.Failure -> when (result.error) {
                is InviteUserError.EmailAlreadyExists ->
                    ResponseEntity.status(HttpStatus.CONFLICT).build()
                is InviteUserError.ValidationFailed ->
                    ResponseEntity.badRequest().build()
                else ->
                    ResponseEntity.internalServerError().build()
            }
        }
    }

    @PutMapping("/users/{userId}")
    public suspend fun updateUser(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: UpdateUserRequest
    ): ResponseEntity<UserResponse> {
        val tenantId = TenantContext.current()

        val command = de.acci.eaf.auth.admin.UpdateUserCommand(
            firstName = request.firstName,
            lastName = request.lastName
        )

        return when (val result = userAdminPort.updateUser(tenantId, UserId(userId), command)) {
            is Result.Success -> {
                val user = userAdminPort.getUser(tenantId, UserId(userId))!!
                ResponseEntity.ok(user.toResponse())
            }
            is Result.Failure -> ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/users/{userId}/deactivate")
    public suspend fun deactivateUser(
        @PathVariable userId: UUID,
        @AuthenticationPrincipal userInfo: UserInfo
    ): ResponseEntity<Unit> {
        val tenantId = TenantContext.current()

        return when (userAdminPort.deactivateUser(tenantId, UserId(userId))) {
            is Result.Success -> ResponseEntity.noContent().build()
            is Result.Failure -> ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/users/{userId}/reactivate")
    public suspend fun reactivateUser(
        @PathVariable userId: UUID
    ): ResponseEntity<Unit> {
        val tenantId = TenantContext.current()

        return when (userAdminPort.reactivateUser(tenantId, UserId(userId))) {
            is Result.Success -> ResponseEntity.noContent().build()
            is Result.Failure -> ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/users/{userId}/password-reset")
    public suspend fun sendPasswordReset(
        @PathVariable userId: UUID
    ): ResponseEntity<Unit> {
        val tenantId = TenantContext.current()

        return when (userAdminPort.sendPasswordResetEmail(tenantId, UserId(userId))) {
            is Result.Success -> ResponseEntity.noContent().build()
            is Result.Failure -> ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/users/{userId}/roles")
    public suspend fun getUserRoles(
        @PathVariable userId: UUID
    ): ResponseEntity<UserRolesResponse> {
        val tenantId = TenantContext.current()

        val roles = roleAdminPort.getUserRoles(tenantId, UserId(userId))

        return ResponseEntity.ok(UserRolesResponse(
            userId = userId,
            roles = roles.toList()
        ))
    }

    @PutMapping("/users/{userId}/roles")
    public suspend fun setUserRoles(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: SetRolesRequest
    ): ResponseEntity<UserRolesResponse> {
        val tenantId = TenantContext.current()

        return when (roleAdminPort.setRoles(tenantId, UserId(userId), request.roles.toSet())) {
            is Result.Success -> {
                val roles = roleAdminPort.getUserRoles(tenantId, UserId(userId))
                ResponseEntity.ok(UserRolesResponse(userId, roles.toList()))
            }
            is Result.Failure -> ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/users/{userId}/sessions")
    public suspend fun getUserSessions(
        @PathVariable userId: UUID
    ): ResponseEntity<SessionListResponse> {
        val tenantId = TenantContext.current()

        val sessions = sessionAdminPort.getUserSessions(tenantId, UserId(userId))

        return ResponseEntity.ok(SessionListResponse(
            sessions = sessions.map { it.toResponse() }
        ))
    }

    @DeleteMapping("/users/{userId}/sessions")
    public suspend fun terminateUserSessions(
        @PathVariable userId: UUID
    ): ResponseEntity<Unit> {
        val tenantId = TenantContext.current()

        return when (sessionAdminPort.terminateUserSessions(tenantId, UserId(userId))) {
            is Result.Success -> ResponseEntity.noContent().build()
            is Result.Failure -> ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/roles")
    public suspend fun listAvailableRoles(): RoleListResponse {
        val tenantId = TenantContext.current()

        val roles = roleAdminPort.listAvailableRoles(tenantId)

        return RoleListResponse(
            roles = roles.map { RoleInfoDto(it.name, it.description) }
        )
    }

    // Extension functions for mapping

    private fun ManagedUser.toResponse(): UserResponse {
        return UserResponse(
            id = id.value,
            email = email,
            firstName = firstName,
            lastName = lastName,
            enabled = enabled,
            emailVerified = emailVerified,
            createdAt = createdAt,
            lastLoginAt = lastLoginAt,
            roles = roles.toList()
        )
    }

    private fun de.acci.eaf.auth.admin.SessionInfo.toResponse(): SessionInfoDto {
        return SessionInfoDto(
            id = id,
            ipAddress = ipAddress,
            userAgent = userAgent,
            startedAt = startedAt,
            lastAccessedAt = lastAccessedAt
        )
    }
}

// DTOs

public data class InviteUserRequest(
    @field:Email
    val email: String,
    @field:Size(max = 100)
    val firstName: String?,
    @field:Size(max = 100)
    val lastName: String?,
    val roles: List<String>?
)

public data class UpdateUserRequest(
    @field:Size(max = 100)
    val firstName: String?,
    @field:Size(max = 100)
    val lastName: String?
)

public data class SetRolesRequest(
    val roles: List<String>
)

public data class UserResponse(
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val enabled: Boolean,
    val emailVerified: Boolean,
    val createdAt: Instant,
    val lastLoginAt: Instant?,
    val roles: List<String>
)

public data class UserListResponse(
    val items: List<UserResponse>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
    val hasMore: Boolean
)

public data class UserRolesResponse(
    val userId: UUID,
    val roles: List<String>
)

public data class SessionListResponse(
    val sessions: List<SessionInfoDto>
)

public data class SessionInfoDto(
    val id: String,
    val ipAddress: String?,
    val userAgent: String?,
    val startedAt: Instant,
    val lastAccessedAt: Instant
)

public data class RoleListResponse(
    val roles: List<RoleInfoDto>
)

public data class RoleInfoDto(
    val name: String,
    val description: String?
)
```

---

## 5. Multi-Tenancy und Sicherheitskonzept

### 5.1 Tenant-Isolation in Keycloak

```
┌─────────────────────────────────────────────────────────────────────┐
│                        DCM Keycloak Realm                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────┐     ┌─────────────────┐     ┌───────────────┐ │
│  │  Tenant A       │     │  Tenant B       │     │  Tenant C     │ │
│  │  (Group)        │     │  (Group)        │     │  (Group)      │ │
│  │  ┌───────────┐  │     │  ┌───────────┐  │     │  ┌─────────┐  │ │
│  │  │ User A1   │  │     │  │ User B1   │  │     │  │ User C1 │  │ │
│  │  │ User A2   │  │     │  │ User B2   │  │     │  │ User C2 │  │ │
│  │  │ User A3   │  │     │  │           │  │     │  │         │  │ │
│  │  └───────────┘  │     │  └───────────┘  │     │  └─────────┘  │ │
│  │  tenant_id:     │     │  tenant_id:     │     │  tenant_id:   │ │
│  │  uuid-a         │     │  uuid-b         │     │  uuid-c       │ │
│  └─────────────────┘     └─────────────────┘     └───────────────┘ │
│                                                                     │
│  Realm Roles: user, admin, manager                                  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Two Strategies for Tenant Assignment:**

#### Option A: User Attribute (Recommended for MVP)

```json
{
  "id": "user-uuid",
  "attributes": {
    "tenant_id": ["tenant-uuid"]
  }
}
```

**Advantages:**
- Simple setup, no Keycloak schema change
- Flexible assignment possible

**Disadvantages:**
- No native Keycloak filtering
- Filtering in application layer

#### Option B: Keycloak Groups

```json
{
  "id": "user-uuid",
  "groups": ["/tenants/tenant-uuid-a"]
}
```

**Advantages:**
- Native Keycloak hierarchy
- Group-based queries possible

**Disadvantages:**
- More complex queries
- Admin must manage groups

**Recommendation:** Option A for MVP, evaluate Option B for Growth phase.

### 5.2 Authorization Matrix

| Operation | User | Admin | Manager | CSP Admin |
|-----------|------|-------|---------|-----------|
| View own profile | ✓ | ✓ | ✓ | ✓ |
| List tenant users | - | ✓ | ✓ | ✓ |
| Invite user | - | ✓ | - | ✓ |
| Deactivate user | - | ✓ | - | ✓ |
| Assign roles | - | ✓ | - | ✓ |
| Force logout | - | ✓ | - | ✓ |
| View audit logs | - | ✓ | ✓ | ✓ |
| Manage all tenants | - | - | - | ✓ |

### 5.3 Security Checklist

| Check | Description | Implementation |
|-------|-------------|----------------|
| Tenant Boundary | Every operation verifies tenant membership | `belongsToTenant()` in Adapter |
| Service Account Scope | Minimal permissions for admin client | `view-users`, `manage-users` only |
| Secret Management | Client secret not in code | Environment Variable / Vault |
| Token Caching | No token logging | `cachedToken` without logging |
| Rate Limiting | Protection against brute-force | Spring Rate Limiter |
| Audit Trail | Log all admin actions | Event Sourcing |
| Self-Modification | Admin cannot delete themselves | Business rule in UseCase |

### 5.4 Self-Modification Prevention

```kotlin
// In DeactivateUserUseCase
public class DeactivateUserUseCase(...) {

    public suspend fun execute(
        targetUserId: UserId,
        executingUser: UserInfo
    ): Result<Unit, DeactivateUserError> {

        // Prevent self-deactivation
        if (targetUserId == executingUser.userId) {
            return Result.failure(DeactivateUserError.CannotDeactivateSelf)
        }

        // Prevent deactivating last admin
        val tenantId = TenantContext.current()
        val adminCount = countAdminsInTenant(tenantId)
        val targetRoles = roleAdminPort.getUserRoles(tenantId, targetUserId)

        if (targetRoles.contains("admin") && adminCount <= 1) {
            return Result.failure(DeactivateUserError.CannotDeactivateLastAdmin)
        }

        // Proceed with deactivation
        return userAdminPort.deactivateUser(tenantId, targetUserId)
    }
}
```

---

## 6. Event Sourcing Integration (Audit Trail)

### 6.1 Admin Event Structure

```kotlin
// dcm-domain/src/main/kotlin/de/acci/dcm/domain/admin/AdminEvents.kt
package de.acci.dcm.domain.admin

import de.acci.eaf.core.domain.DomainEvent
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import java.time.Instant
import java.util.UUID

/**
 * Base class for all admin events.
 *
 * Admin events are NOT aggregate events - they are standalone audit records
 * that capture administrative actions performed via the Keycloak Admin API.
 */
public sealed class AdminEvent : DomainEvent {
    abstract val adminEventId: UUID
    abstract val tenantId: TenantId
    abstract val performedBy: UserId
    abstract val performedAt: Instant
    abstract val targetUserId: UserId?
    abstract val ipAddress: String?
    abstract val userAgent: String?
}

public data class UserInvitedEvent(
    override val adminEventId: UUID = UUID.randomUUID(),
    override val tenantId: TenantId,
    override val performedBy: UserId,
    override val performedAt: Instant,
    override val targetUserId: UserId? = null,
    override val ipAddress: String? = null,
    override val userAgent: String? = null,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val assignedRoles: Set<String>
) : AdminEvent() {
    override val eventType: String = "UserInvited"
}

public data class UserDeactivatedEvent(
    override val adminEventId: UUID = UUID.randomUUID(),
    override val tenantId: TenantId,
    override val performedBy: UserId,
    override val performedAt: Instant,
    override val targetUserId: UserId,
    override val ipAddress: String? = null,
    override val userAgent: String? = null,
    val reason: String?
) : AdminEvent() {
    override val eventType: String = "UserDeactivated"
}

public data class UserReactivatedEvent(
    override val adminEventId: UUID = UUID.randomUUID(),
    override val tenantId: TenantId,
    override val performedBy: UserId,
    override val performedAt: Instant,
    override val targetUserId: UserId,
    override val ipAddress: String? = null,
    override val userAgent: String? = null
) : AdminEvent() {
    override val eventType: String = "UserReactivated"
}

public data class UserRolesChangedEvent(
    override val adminEventId: UUID = UUID.randomUUID(),
    override val tenantId: TenantId,
    override val performedBy: UserId,
    override val performedAt: Instant,
    override val targetUserId: UserId,
    override val ipAddress: String? = null,
    override val userAgent: String? = null,
    val previousRoles: Set<String>,
    val newRoles: Set<String>
) : AdminEvent() {
    override val eventType: String = "UserRolesChanged"

    val addedRoles: Set<String> get() = newRoles - previousRoles
    val removedRoles: Set<String> get() = previousRoles - newRoles
}

public data class PasswordResetRequestedEvent(
    override val adminEventId: UUID = UUID.randomUUID(),
    override val tenantId: TenantId,
    override val performedBy: UserId,
    override val performedAt: Instant,
    override val targetUserId: UserId,
    override val ipAddress: String? = null,
    override val userAgent: String? = null
) : AdminEvent() {
    override val eventType: String = "PasswordResetRequested"
}

public data class UserSessionsTerminatedEvent(
    override val adminEventId: UUID = UUID.randomUUID(),
    override val tenantId: TenantId,
    override val performedBy: UserId,
    override val performedAt: Instant,
    override val targetUserId: UserId,
    override val ipAddress: String? = null,
    override val userAgent: String? = null,
    val terminatedSessionCount: Int
) : AdminEvent() {
    override val eventType: String = "UserSessionsTerminated"
}
```

### 6.2 Admin-Event-Store

```kotlin
// dcm-infrastructure/src/main/kotlin/de/acci/dcm/infrastructure/admin/AdminEventStore.kt
package de.acci.dcm.infrastructure.admin

import de.acci.dcm.domain.admin.AdminEvent
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Repository for admin audit events.
 *
 * Unlike aggregate event stores, admin events are standalone records
 * that are only appended (never replayed to reconstruct state).
 */
public interface AdminEventStore {

    /**
     * Appends an admin event to the store.
     */
    public suspend fun append(event: AdminEvent)

    /**
     * Queries admin events by tenant.
     */
    public fun findByTenant(
        tenantId: TenantId,
        from: Instant? = null,
        to: Instant? = null,
        eventTypes: Set<String>? = null,
        limit: Int = 100
    ): Flow<AdminEvent>

    /**
     * Queries admin events affecting a specific user.
     */
    public fun findByTargetUser(
        tenantId: TenantId,
        userId: UserId,
        from: Instant? = null,
        to: Instant? = null
    ): Flow<AdminEvent>

    /**
     * Queries admin events performed by a specific admin.
     */
    public fun findByPerformer(
        tenantId: TenantId,
        performedBy: UserId,
        from: Instant? = null,
        to: Instant? = null
    ): Flow<AdminEvent>
}
```

### 6.3 Database Schema

```sql
-- Flyway migration: V010__admin_events.sql

CREATE TABLE "ADMIN_EVENTS" (
    "ID" UUID PRIMARY KEY,
    "TENANT_ID" UUID NOT NULL,
    "EVENT_TYPE" VARCHAR(100) NOT NULL,
    "PERFORMED_BY" UUID NOT NULL,
    "PERFORMED_AT" TIMESTAMP WITH TIME ZONE NOT NULL,
    "TARGET_USER_ID" UUID,
    "IP_ADDRESS" VARCHAR(45),
    "USER_AGENT" VARCHAR(500),
    "PAYLOAD" JSONB NOT NULL,
    "CREATED_AT" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common queries
CREATE INDEX "IDX_ADMIN_EVENTS_TENANT" ON "ADMIN_EVENTS" ("TENANT_ID", "PERFORMED_AT" DESC);
CREATE INDEX "IDX_ADMIN_EVENTS_TARGET_USER" ON "ADMIN_EVENTS" ("TENANT_ID", "TARGET_USER_ID", "PERFORMED_AT" DESC);
CREATE INDEX "IDX_ADMIN_EVENTS_PERFORMER" ON "ADMIN_EVENTS" ("TENANT_ID", "PERFORMED_BY", "PERFORMED_AT" DESC);
CREATE INDEX "IDX_ADMIN_EVENTS_TYPE" ON "ADMIN_EVENTS" ("TENANT_ID", "EVENT_TYPE", "PERFORMED_AT" DESC);

-- [jooq ignore start]
-- Row-Level Security
ALTER TABLE "ADMIN_EVENTS" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "ADMIN_EVENTS" FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON "ADMIN_EVENTS"
    FOR ALL
    USING ("TENANT_ID" = current_setting('app.tenant_id', true)::uuid);

GRANT SELECT, INSERT ON "ADMIN_EVENTS" TO eaf_app;
-- [jooq ignore stop]
```

---

## 7. Frontend Specification

### 7.1 Component Hierarchy

```
src/
├── pages/
│   └── admin/
│       ├── UsersPage.tsx           # User list with search/filter
│       ├── UserDetailPage.tsx      # Single user view
│       └── AuditLogPage.tsx        # Admin action log
├── components/
│   └── admin/
│       ├── UserTable.tsx           # Data table with actions
│       ├── InviteUserDialog.tsx    # Modal for user invitation
│       ├── RoleSelector.tsx        # Dropdown for role assignment
│       ├── UserStatusBadge.tsx     # Active/Inactive indicator
│       ├── ConfirmDeactivateDialog.tsx
│       ├── SessionsPanel.tsx       # Active sessions list
│       └── AuditEventCard.tsx      # Single audit event display
├── hooks/
│   └── admin/
│       ├── useUsers.ts             # React Query hook for users
│       ├── useUserMutations.ts     # Invite, deactivate, etc.
│       ├── useRoles.ts             # Available roles
│       └── useAdminAudit.ts        # Audit log queries
└── api/
    └── admin/
        └── userAdminApi.ts         # API client functions
```

### 7.2 Main Components

#### UsersPage.tsx

```tsx
// pages/admin/UsersPage.tsx
import { useState } from 'react';
import { useUsers, useUserMutations } from '@/hooks/admin';
import { UserTable } from '@/components/admin/UserTable';
import { InviteUserDialog } from '@/components/admin/InviteUserDialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PlusIcon, SearchIcon } from 'lucide-react';

export function UsersPage() {
  const [search, setSearch] = useState('');
  const [inviteDialogOpen, setInviteDialogOpen] = useState(false);

  const { data: users, isLoading, refetch } = useUsers({ search });
  const { inviteUser, deactivateUser, reactivateUser } = useUserMutations();

  const handleInvite = async (data: InviteUserData) => {
    await inviteUser.mutateAsync(data);
    setInviteDialogOpen(false);
    refetch();
  };

  const handleDeactivate = async (userId: string) => {
    await deactivateUser.mutateAsync(userId);
    refetch();
  };

  const handleReactivate = async (userId: string) => {
    await reactivateUser.mutateAsync(userId);
    refetch();
  };

  return (
    <div className="container py-6">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">User Management</h1>
        <Button onClick={() => setInviteDialogOpen(true)}>
          <PlusIcon className="h-4 w-4 mr-2" />
          Invite User
        </Button>
      </div>

      <div className="flex items-center gap-4 mb-6">
        <div className="relative flex-1 max-w-sm">
          <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search by name or email..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
      </div>

      <UserTable
        users={users?.items ?? []}
        isLoading={isLoading}
        onDeactivate={handleDeactivate}
        onReactivate={handleReactivate}
      />

      <InviteUserDialog
        open={inviteDialogOpen}
        onOpenChange={setInviteDialogOpen}
        onInvite={handleInvite}
      />
    </div>
  );
}
```

#### InviteUserDialog.tsx

```tsx
// components/admin/InviteUserDialog.tsx
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { RoleSelector } from './RoleSelector';

const inviteSchema = z.object({
  email: z.string().email('Invalid email address'),
  firstName: z.string().optional(),
  lastName: z.string().optional(),
  roles: z.array(z.string()).min(1, 'At least one role required'),
});

type InviteFormData = z.infer<typeof inviteSchema>;

interface InviteUserDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onInvite: (data: InviteFormData) => Promise<void>;
}

export function InviteUserDialog({ open, onOpenChange, onInvite }: InviteUserDialogProps) {
  const form = useForm<InviteFormData>({
    resolver: zodResolver(inviteSchema),
    defaultValues: {
      email: '',
      firstName: '',
      lastName: '',
      roles: ['user'],
    },
  });

  const handleSubmit = async (data: InviteFormData) => {
    await onInvite(data);
    form.reset();
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Invite New User</DialogTitle>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="email">Email Address *</Label>
            <Input
              id="email"
              type="email"
              {...form.register('email')}
              placeholder="user@example.com"
            />
            {form.formState.errors.email && (
              <p className="text-sm text-destructive">
                {form.formState.errors.email.message}
              </p>
            )}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="firstName">First Name</Label>
              <Input id="firstName" {...form.register('firstName')} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="lastName">Last Name</Label>
              <Input id="lastName" {...form.register('lastName')} />
            </div>
          </div>

          <div className="space-y-2">
            <Label>Roles *</Label>
            <RoleSelector
              value={form.watch('roles')}
              onChange={(roles) => form.setValue('roles', roles)}
            />
            {form.formState.errors.roles && (
              <p className="text-sm text-destructive">
                {form.formState.errors.roles.message}
              </p>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting ? 'Inviting...' : 'Invite'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
```

### 7.3 API-Client

```typescript
// api/admin/userAdminApi.ts
import { apiClient } from '@/lib/apiClient';

export interface User {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  enabled: boolean;
  emailVerified: boolean;
  createdAt: string;
  lastLoginAt: string | null;
  roles: string[];
}

export interface UserListResponse {
  items: User[];
  totalCount: number;
  page: number;
  size: number;
  hasMore: boolean;
}

export interface InviteUserRequest {
  email: string;
  firstName?: string;
  lastName?: string;
  roles: string[];
}

export const userAdminApi = {
  listUsers: async (params: { page?: number; size?: number; search?: string }) => {
    const response = await apiClient.get<UserListResponse>('/api/admin/users', { params });
    return response.data;
  },

  getUser: async (userId: string) => {
    const response = await apiClient.get<User>(`/api/admin/users/${userId}`);
    return response.data;
  },

  inviteUser: async (request: InviteUserRequest) => {
    const response = await apiClient.post<User>('/api/admin/users', request);
    return response.data;
  },

  updateUser: async (userId: string, request: { firstName?: string; lastName?: string }) => {
    const response = await apiClient.put<User>(`/api/admin/users/${userId}`, request);
    return response.data;
  },

  deactivateUser: async (userId: string) => {
    await apiClient.post(`/api/admin/users/${userId}/deactivate`);
  },

  reactivateUser: async (userId: string) => {
    await apiClient.post(`/api/admin/users/${userId}/reactivate`);
  },

  sendPasswordReset: async (userId: string) => {
    await apiClient.post(`/api/admin/users/${userId}/password-reset`);
  },

  getUserRoles: async (userId: string) => {
    const response = await apiClient.get<{ userId: string; roles: string[] }>(
      `/api/admin/users/${userId}/roles`
    );
    return response.data;
  },

  setUserRoles: async (userId: string, roles: string[]) => {
    const response = await apiClient.put<{ userId: string; roles: string[] }>(
      `/api/admin/users/${userId}/roles`,
      { roles }
    );
    return response.data;
  },

  getUserSessions: async (userId: string) => {
    const response = await apiClient.get<{ sessions: SessionInfo[] }>(
      `/api/admin/users/${userId}/sessions`
    );
    return response.data;
  },

  terminateUserSessions: async (userId: string) => {
    await apiClient.delete(`/api/admin/users/${userId}/sessions`);
  },

  listAvailableRoles: async () => {
    const response = await apiClient.get<{ roles: RoleInfo[] }>('/api/admin/roles');
    return response.data;
  },
};

export interface SessionInfo {
  id: string;
  ipAddress: string | null;
  userAgent: string | null;
  startedAt: string;
  lastAccessedAt: string;
}

export interface RoleInfo {
  name: string;
  description: string | null;
}
```

---

## 8. Test Strategy

### 8.1 Test Pyramid

```
                    ┌───────────────┐
                    │   E2E Tests   │  Playwright
                    │   (5-10)      │  Real Keycloak
                    └───────────────┘
               ┌─────────────────────────┐
               │   Integration Tests     │  Testcontainers
               │   (20-30)               │  Keycloak + Postgres
               └─────────────────────────┘
          ┌───────────────────────────────────┐
          │        Unit Tests                  │  MockK
          │        (50-100)                    │  Isolated
          └───────────────────────────────────┘
```

### 8.2 Unit Tests

```kotlin
// eaf-auth-keycloak/src/test/kotlin/de/acci/eaf/auth/keycloak/admin/KeycloakUserAdminAdapterTest.kt
package de.acci.eaf.auth.keycloak.admin

import de.acci.eaf.auth.admin.CreateUserCommand
import de.acci.eaf.auth.admin.PaginationRequest
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class KeycloakUserAdminAdapterTest {

    private lateinit var mockClient: KeycloakAdminClient
    private lateinit var adapter: KeycloakUserAdminAdapter
    private lateinit var properties: KeycloakAdminProperties

    private val tenantId = TenantId(UUID.randomUUID())
    private val userId = UserId(UUID.randomUUID())

    @BeforeEach
    fun setup() {
        mockClient = mockk()
        properties = KeycloakAdminProperties(
            serverUrl = "http://keycloak:8080",
            realm = "dcm",
            clientId = "dcm-admin",
            clientSecret = "secret"
        )
        adapter = KeycloakUserAdminAdapter(mockClient, properties)
    }

    @Test
    fun `listUsers filters by tenant_id attribute`() = runTest {
        // Given
        val otherTenantId = UUID.randomUUID()
        val keycloakUsers = listOf(
            createKeycloakUser(tenantId = tenantId.value.toString()),
            createKeycloakUser(tenantId = tenantId.value.toString()),
            createKeycloakUser(tenantId = otherTenantId.toString())
        )

        coEvery { mockClient.get<List<KeycloakUserRepresentation>>(any(), any()) } returns keycloakUsers

        // When
        val result = adapter.listUsers(tenantId, PaginationRequest(offset = 0, limit = 20))

        // Then
        assertEquals(2, result.items.size)
        assertEquals(2, result.totalCount)
    }

    @Test
    fun `getUser returns null for user in different tenant`() = runTest {
        // Given
        val otherTenantId = UUID.randomUUID()
        val keycloakUser = createKeycloakUser(
            id = userId.value.toString(),
            tenantId = otherTenantId.toString()
        )

        coEvery { mockClient.get<KeycloakUserRepresentation>(any()) } returns keycloakUser

        // When
        val result = adapter.getUser(tenantId, userId)

        // Then
        assertNull(result)
    }

    @Test
    fun `createUser sets tenant_id attribute`() = runTest {
        // Given
        val command = CreateUserCommand(
            email = "test@example.com",
            firstName = "Test",
            lastName = "User",
            roles = setOf("user"),
            sendInvitationEmail = true
        )

        val createdUserId = UUID.randomUUID()
        coEvery {
            mockClient.get<List<KeycloakUserRepresentation>>(any(), any())
        } returns emptyList()
        coEvery {
            mockClient.postForLocation(any(), any<KeycloakUserRepresentation>())
        } returns "http://keycloak/users/$createdUserId"
        coEvery {
            mockClient.put(any(), any<List<String>>())
        } just runs

        // When
        val result = adapter.createUser(tenantId, command)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(createdUserId, result.getOrNull()?.value)

        coVerify {
            mockClient.postForLocation(
                "/users",
                match<KeycloakUserRepresentation> { user ->
                    user.attributes?.get("tenant_id")?.contains(tenantId.value.toString()) == true
                }
            )
        }
    }

    @Test
    fun `deactivateUser verifies tenant membership before deactivation`() = runTest {
        // Given
        val otherTenantId = UUID.randomUUID()
        val keycloakUser = createKeycloakUser(
            id = userId.value.toString(),
            tenantId = otherTenantId.toString()
        )

        coEvery { mockClient.get<KeycloakUserRepresentation>(any()) } returns keycloakUser

        // When
        val result = adapter.deactivateUser(tenantId, userId)

        // Then
        assertTrue(result.isFailure)

        // Verify put was never called (deactivation was prevented)
        coVerify(exactly = 0) { mockClient.put(any(), any<Map<String, Any>>()) }
    }

    private fun createKeycloakUser(
        id: String = UUID.randomUUID().toString(),
        tenantId: String
    ): KeycloakUserRepresentation {
        return KeycloakUserRepresentation(
            id = id,
            username = "user-$id",
            email = "user-$id@example.com",
            firstName = "Test",
            lastName = "User",
            enabled = true,
            emailVerified = true,
            createdTimestamp = System.currentTimeMillis(),
            attributes = mapOf("tenant_id" to listOf(tenantId))
        )
    }
}
```

### 8.3 Integration Tests mit Testcontainers

```kotlin
// eaf-auth-keycloak/src/test/kotlin/de/acci/eaf/auth/keycloak/admin/KeycloakAdminIntegrationTest.kt
package de.acci.eaf.auth.keycloak.admin

import dasniko.testcontainers.keycloak.KeycloakContainer
import de.acci.eaf.auth.admin.CreateUserCommand
import de.acci.eaf.auth.admin.PaginationRequest
import de.acci.eaf.core.types.TenantId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.web.reactive.function.client.WebClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeycloakAdminIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val keycloak = KeycloakContainer("quay.io/keycloak/keycloak:24.0")
            .withRealmImportFile("test-realm.json")
    }

    private lateinit var adapter: KeycloakUserAdminAdapter
    private val tenantId = TenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

    @BeforeAll
    fun setup() {
        val properties = KeycloakAdminProperties(
            serverUrl = keycloak.authServerUrl,
            realm = "dcm-test",
            clientId = "admin-cli",
            clientSecret = "test-secret"
        )

        val webClient = WebClient.builder().build()
        val client = KeycloakAdminClient(webClient, properties)
        adapter = KeycloakUserAdminAdapter(client, properties)
    }

    @Test
    fun `can create and retrieve user`() = runTest {
        // Given
        val command = CreateUserCommand(
            email = "integration-test-${UUID.randomUUID()}@example.com",
            firstName = "Integration",
            lastName = "Test",
            roles = setOf("user"),
            sendInvitationEmail = false
        )

        // When
        val createResult = adapter.createUser(tenantId, command)

        // Then
        assertTrue(createResult.isSuccess)
        val userId = createResult.getOrNull()!!

        val user = adapter.getUser(tenantId, userId)
        assertNotNull(user)
        assertEquals(command.email, user!!.email)
        assertEquals(command.firstName, user.firstName)
        assertEquals(command.lastName, user.lastName)
    }

    @Test
    fun `cannot access user from different tenant`() = runTest {
        // Given
        val otherTenantId = TenantId(UUID.randomUUID())
        val command = CreateUserCommand(
            email = "other-tenant-${UUID.randomUUID()}@example.com",
            firstName = "Other",
            lastName = "Tenant",
            roles = emptySet(),
            sendInvitationEmail = false
        )

        // Create user in different tenant
        val createResult = adapter.createUser(otherTenantId, command)
        assertTrue(createResult.isSuccess)
        val userId = createResult.getOrNull()!!

        // When - try to access from our tenant
        val user = adapter.getUser(tenantId, userId)

        // Then
        assertNull(user)
    }

    @Test
    fun `can deactivate and reactivate user`() = runTest {
        // Given
        val command = CreateUserCommand(
            email = "deactivate-test-${UUID.randomUUID()}@example.com",
            firstName = "Deactivate",
            lastName = "Test",
            roles = emptySet(),
            sendInvitationEmail = false
        )

        val createResult = adapter.createUser(tenantId, command)
        val userId = createResult.getOrNull()!!

        // When - deactivate
        val deactivateResult = adapter.deactivateUser(tenantId, userId)
        assertTrue(deactivateResult.isSuccess)

        // Then
        val deactivatedUser = adapter.getUser(tenantId, userId)
        assertFalse(deactivatedUser!!.enabled)

        // When - reactivate
        val reactivateResult = adapter.reactivateUser(tenantId, userId)
        assertTrue(reactivateResult.isSuccess)

        // Then
        val reactivatedUser = adapter.getUser(tenantId, userId)
        assertTrue(reactivatedUser!!.enabled)
    }

    @Test
    fun `listUsers only returns tenant users`() = runTest {
        // Given - create users in both tenants
        val ourTenant = tenantId
        val otherTenant = TenantId(UUID.randomUUID())

        val ourUsers = (1..3).map { i ->
            val command = CreateUserCommand(
                email = "list-test-our-$i-${UUID.randomUUID()}@example.com",
                firstName = "Our",
                lastName = "User$i",
                roles = emptySet(),
                sendInvitationEmail = false
            )
            adapter.createUser(ourTenant, command)
        }

        val otherUsers = (1..2).map { i ->
            val command = CreateUserCommand(
                email = "list-test-other-$i-${UUID.randomUUID()}@example.com",
                firstName = "Other",
                lastName = "User$i",
                roles = emptySet(),
                sendInvitationEmail = false
            )
            adapter.createUser(otherTenant, command)
        }

        // When
        val result = adapter.listUsers(ourTenant, PaginationRequest(offset = 0, limit = 100))

        // Then
        val ourUserIds = ourUsers.mapNotNull { it.getOrNull()?.value }
        val resultUserIds = result.items.map { it.id.value }

        assertTrue(resultUserIds.containsAll(ourUserIds))

        // Verify no users from other tenant
        val otherUserIds = otherUsers.mapNotNull { it.getOrNull()?.value }
        assertTrue(otherUserIds.none { it in resultUserIds })
    }
}
```

### 8.4 Test-Realm-Konfiguration

```json
// src/test/resources/test-realm.json
{
  "realm": "dcm-test",
  "enabled": true,
  "clients": [
    {
      "clientId": "admin-cli",
      "enabled": true,
      "serviceAccountsEnabled": true,
      "clientAuthenticatorType": "client-secret",
      "secret": "test-secret",
      "directAccessGrantsEnabled": true
    },
    {
      "clientId": "dcm-web",
      "enabled": true,
      "publicClient": true,
      "redirectUris": ["http://localhost:3000/*"],
      "webOrigins": ["http://localhost:3000"]
    }
  ],
  "roles": {
    "realm": [
      { "name": "user", "description": "Standard user role" },
      { "name": "admin", "description": "Tenant administrator" },
      { "name": "manager", "description": "Manager with reporting access" }
    ]
  },
  "users": [
    {
      "username": "service-account-admin-cli",
      "enabled": true,
      "serviceAccountClientId": "admin-cli",
      "realmRoles": ["default-roles-dcm-test"],
      "clientRoles": {
        "realm-management": [
          "view-users",
          "manage-users",
          "view-realm",
          "query-users"
        ]
      }
    }
  ]
}
```

---

## 9. Configuration

### 9.1 Application Properties

```yaml
# application.yml
eaf:
  auth:
    keycloak:
      # Standard auth config (existing)
      server-url: ${KEYCLOAK_SERVER_URL:http://localhost:8080}
      realm: ${KEYCLOAK_REALM:dcm}
      client-id: ${KEYCLOAK_CLIENT_ID:dcm-web}

      # Admin API config (new)
      admin:
        server-url: ${KEYCLOAK_SERVER_URL:http://localhost:8080}
        realm: ${KEYCLOAK_REALM:dcm}
        client-id: ${KEYCLOAK_ADMIN_CLIENT_ID:dcm-admin-service}
        client-secret: ${KEYCLOAK_ADMIN_CLIENT_SECRET:}
```

### 9.2 Keycloak Client Setup (Realm Export)

```json
{
  "clientId": "dcm-admin-service",
  "name": "DCM Admin Service Account",
  "description": "Service account for DCM user administration",
  "enabled": true,
  "clientAuthenticatorType": "client-secret",
  "secret": "GENERATE_SECURE_SECRET",
  "serviceAccountsEnabled": true,
  "publicClient": false,
  "directAccessGrantsEnabled": false,
  "standardFlowEnabled": false,
  "implicitFlowEnabled": false,
  "protocol": "openid-connect",
  "attributes": {
    "access.token.lifespan": "300"
  }
}
```

**Service Account Role Mapping:**
```
Realm Management:
  - view-users
  - manage-users
  - query-users
  - view-realm
```

---

## 10. Migration & Rollout

### 10.1 Phase Plan

| Phase | Scope | Stories |
|-------|-------|---------|
| Phase 1 | User listing (read-only) | Story X.1 |
| Phase 2 | User invitation | Story X.2 |
| Phase 3 | Deactivate/Reactivate | Story X.3 |
| Phase 4 | Role management | Story X.4 |
| Phase 5 | Session management | Story X.5 |
| Phase 6 | Audit log UI | Story X.6 |

### 10.2 Feature Flags

```kotlin
// For gradual rollout
@ConfigurationProperties(prefix = "eaf.features.admin")
data class AdminFeatureFlags(
    val userListEnabled: Boolean = true,
    val userInviteEnabled: Boolean = false,
    val userDeactivateEnabled: Boolean = false,
    val roleManagementEnabled: Boolean = false,
    val sessionManagementEnabled: Boolean = false
)
```

---

## 11. Open Questions / Decisions

| # | Question | Options | Recommendation |
|---|----------|---------|----------------|
| 1 | Tenant assignment | User Attribute vs. Groups | Attribute (MVP) |
| 2 | Realm structure | Single Realm vs. Multi-Realm | Single Realm (MVP) |
| 3 | Email provider | Keycloak SMTP vs. own service | Keycloak SMTP |
| 4 | MFA management | Via Keycloak Account Console | Account Console (iFrame) |
| 5 | Password policy | Keycloak-native vs. Custom | Keycloak-native |

---

## 12. References

- [Keycloak Admin REST API Documentation](https://www.keycloak.org/docs-api/24.0/rest-api/index.html)
- [DCM Security Architecture](./security-architecture.md)
- [DCM Architecture](./architecture.md)
- [Epic 2: Authentication & Authorization](./epics.md#epic-2)

---

*Created as part of the Keycloak UI Integration Evaluation*
