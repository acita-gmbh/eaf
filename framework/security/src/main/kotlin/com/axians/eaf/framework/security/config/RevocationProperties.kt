package com.axians.eaf.framework.security.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Configuration properties for Layer 7 token revocation controls.
 *
 * Controls:
 * - failClosed: whether Redis outages should reject requests (security-over-availability)
 * - keyPrefix: Redis key prefix for revoked JWT entries
 * - defaultTtl: fallback TTL if JWT exp is missing/expired (default aligns with token lifetime)
 */
@Component
@ConfigurationProperties(prefix = "eaf.security.revocation")
data class RevocationProperties(
    var failClosed: Boolean = false,
    var keyPrefix: String = "jwt:revoked:",
    var defaultTtl: Duration = Duration.ofMinutes(10),
)
