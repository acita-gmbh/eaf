package com.axians.eaf.framework.security.user

/**
 * Abstraction over the backing identity store (Keycloak) for Layer 9 user validation.
 */
interface UserDirectory {
    /**
     * @return [UserRecord] when the subject exists, `null` when missing.
     * @throws UserValidationException when the directory cannot be consulted securely.
     */
    fun findById(userId: String): UserRecord?
}
