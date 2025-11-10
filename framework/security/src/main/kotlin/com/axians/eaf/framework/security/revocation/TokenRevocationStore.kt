package com.axians.eaf.framework.security.revocation

import java.time.Instant

/**
 * Abstraction over token revocation storage for JWT Layer 7.
 */
interface TokenRevocationStore {
    fun isRevoked(jti: String): Boolean

    fun revoke(jti: String, expiresAt: Instant?)
}
