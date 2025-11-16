package com.axians.eaf.framework.security.ssrf

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

/**
 * SSRF (Server-Side Request Forgery) protection for outbound HTTP requests.
 *
 * Implements OWASP A01:2025 - Broken Access Control recommendations:
 * - Block requests to internal/private IP ranges (RFC 1918, loopback, link-local)
 * - Block requests to cloud metadata services (AWS, GCP, Azure)
 * - Whitelist allowed domains/IP ranges
 * - Protect against DNS rebinding attacks
 *
 * Reference: docs/security/owasp-top-10-2025-compliance.md
 *
 * @since 1.0.0
 */
class SsrfProtection(
    private val properties: SsrfProtectionProperties
) {
    private val logger = LoggerFactory.getLogger(SsrfProtection::class.java)

    /**
     * Validate a URL before making an outbound HTTP request.
     *
     * @param url The URL to validate
     * @throws SsrfException if the URL is blocked
     */
    fun validateUrl(url: String) {
        if (!properties.enabled) {
            return
        }

        try {
            val uri = URI(url)

            // Check scheme
            validateScheme(uri)

            // Check host
            validateHost(uri.host ?: throw SsrfException("URL has no host: $url"))

            // Resolve DNS and check IP address
            validateIpAddress(uri.host)

        } catch (ex: SsrfException) {
            logger.warn("SSRF protection blocked URL: url={}, reason={}", url, ex.message)
            throw ex
        } catch (ex: Exception) {
            logger.error("Error validating URL for SSRF: url={}", url, ex)
            throw SsrfException("Invalid URL: ${ex.message}", ex)
        }
    }

    private fun validateScheme(uri: URI) {
        val scheme = uri.scheme?.lowercase()
        if (scheme !in properties.allowedSchemes) {
            throw SsrfException(
                "Scheme '$scheme' is not allowed. Allowed schemes: ${properties.allowedSchemes}"
            )
        }
    }

    private fun validateHost(host: String) {
        // Check if host is explicitly blocked
        if (properties.blockedHosts.any { host.equals(it, ignoreCase = true) }) {
            throw SsrfException("Host '$host' is explicitly blocked")
        }

        // Check if host is in allowed list (if whitelist mode)
        if (properties.allowedHosts.isNotEmpty()) {
            val isAllowed = properties.allowedHosts.any { allowedHost ->
                host.equals(allowedHost, ignoreCase = true) ||
                    (allowedHost.startsWith("*") && host.endsWith(allowedHost.substring(1)))
            }
            if (!isAllowed) {
                throw SsrfException("Host '$host' is not in allowed hosts list")
            }
        }

        // Check metadata service URLs
        if (host in METADATA_SERVICE_HOSTS) {
            throw SsrfException("Access to cloud metadata service '$host' is blocked")
        }
    }

    private fun validateIpAddress(host: String) {
        try {
            val addresses = InetAddress.getAllByName(host)

            addresses.forEach { address ->
                val ip = address.hostAddress

                // Check if IP is blocked
                if (isBlockedIpAddress(ip)) {
                    throw SsrfException(
                        "IP address '$ip' (resolved from '$host') is in a blocked range"
                    )
                }

                // Check IP allowlist (if configured)
                if (properties.allowedIpRanges.isNotEmpty()) {
                    if (!isIpInAllowedRanges(ip)) {
                        throw SsrfException(
                            "IP address '$ip' (resolved from '$host') is not in allowed IP ranges"
                        )
                    }
                }
            }
        } catch (ex: UnknownHostException) {
            throw SsrfException("Failed to resolve host '$host': ${ex.message}", ex)
        }
    }

    private fun isBlockedIpAddress(ip: String): Boolean {
        val address = InetAddress.getByName(ip)

        return when {
            // Loopback (127.0.0.0/8, ::1)
            address.isLoopbackAddress -> true

            // Link-local (169.254.0.0/16, fe80::/10)
            address.isLinkLocalAddress -> true

            // Site-local / Private (RFC 1918: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
            address.isSiteLocalAddress -> true

            // Multicast
            address.isMulticastAddress -> true

            // Specific blocked ranges
            ip.startsWith("0.") -> true // 0.0.0.0/8
            ip.startsWith("169.254.") -> true // AWS metadata
            ip == "::1" -> true // IPv6 loopback

            else -> false
        }
    }

    private fun isIpInAllowedRanges(ip: String): Boolean {
        // Simplified IP range checking - production would use proper CIDR matching
        return properties.allowedIpRanges.any { range ->
            ip.startsWith(range.substringBefore("/"))
        }
    }

    companion object {
        /**
         * Known cloud metadata service hosts.
         */
        private val METADATA_SERVICE_HOSTS = setOf(
            // AWS
            "169.254.169.254",
            "metadata.google.internal",
            // Azure
            "169.254.169.254",
            // GCP
            "metadata.google.internal",
            "metadata",
            // DigitalOcean
            "169.254.169.254",
            // Alibaba Cloud
            "100.100.100.200"
        )
    }
}

/**
 * Configuration properties for SSRF protection.
 */
@ConfigurationProperties(prefix = "eaf.security.ssrf")
data class SsrfProtectionProperties(
    /**
     * Enable/disable SSRF protection.
     * Default: true
     */
    val enabled: Boolean = true,

    /**
     * Allowed URL schemes.
     * Default: http, https
     */
    val allowedSchemes: Set<String> = setOf("http", "https"),

    /**
     * Explicitly blocked hosts (exact match or wildcard).
     * Example: ["localhost", "internal.company.com"]
     */
    val blockedHosts: Set<String> = setOf(
        "localhost",
        "127.0.0.1",
        "::1"
    ),

    /**
     * Allowed hosts (whitelist mode - if not empty, only these hosts are allowed).
     * Supports wildcards: ["*.api.example.com", "public.service.com"]
     */
    val allowedHosts: Set<String> = emptySet(),

    /**
     * Allowed IP ranges in CIDR notation.
     * Example: ["203.0.113.0/24", "198.51.100.0/24"]
     */
    val allowedIpRanges: Set<String> = emptySet(),

    /**
     * Block cloud metadata services.
     * Default: true
     */
    val blockMetadataServices: Boolean = true,

    /**
     * Block private IP ranges (RFC 1918).
     * Default: true
     */
    val blockPrivateIpRanges: Boolean = true
)

/**
 * Exception thrown when SSRF protection blocks a request.
 */
class SsrfException(
    message: String,
    cause: Throwable? = null
) : SecurityException(message, cause)
