package com.axians.eaf.framework.security.headers

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Auto-configuration for security HTTP headers.
 *
 * Implements OWASP A02:2025 - Security Misconfiguration recommendations:
 * - Strict-Transport-Security (HSTS)
 * - Content-Security-Policy (CSP)
 * - X-Frame-Options
 * - X-Content-Type-Options
 * - X-XSS-Protection
 * - Referrer-Policy
 * - Permissions-Policy
 *
 * Reference: docs/security/owasp-top-10-2025-compliance.md
 *
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication
@EnableConfigurationProperties(SecurityHeadersProperties::class)
class SecurityHeadersConfiguration(
    private val properties: SecurityHeadersProperties,
) {
    /**
     * Filter that adds security headers to all HTTP responses.
     *
     * Headers are configurable via application.yml:
     * ```yaml
     * eaf:
     *   security:
     *     headers:
     *       enabled: true
     *       hsts:
     *         enabled: true
     *         max-age: 31536000  # 1 year
     *         include-subdomains: true
     *       csp:
     *         enabled: true
     *         policy: "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'"
     * ```
     */
    @Bean
    fun securityHeadersFilter(): SecurityHeadersFilter = SecurityHeadersFilter(properties)
}

/**
 * Configuration properties for security headers.
 */
@ConfigurationProperties(prefix = "eaf.security.headers")
data class SecurityHeadersProperties(
    /**
     * Enable/disable security headers globally.
     * Default: true
     */
    val enabled: Boolean = true,
    /**
     * HTTP Strict Transport Security (HSTS) configuration.
     */
    val hsts: HstsProperties = HstsProperties(),
    /**
     * Content Security Policy (CSP) configuration.
     */
    val csp: CspProperties = CspProperties(),
    /**
     * X-Frame-Options configuration.
     */
    val frameOptions: FrameOptionsProperties = FrameOptionsProperties(),
    /**
     * Referrer-Policy configuration.
     */
    val referrerPolicy: ReferrerPolicyProperties = ReferrerPolicyProperties(),
    /**
     * Permissions-Policy configuration.
     */
    val permissionsPolicy: PermissionsPolicyProperties = PermissionsPolicyProperties(),
) {
    data class HstsProperties(
        val enabled: Boolean = true,
        val maxAge: Long = 31536000, // 1 year
        val includeSubdomains: Boolean = true,
        val preload: Boolean = false,
    )

    data class CspProperties(
        val enabled: Boolean = true,
        /**
         * Content Security Policy directive.
         * Default is restrictive - allows only same-origin content.
         *
         * Adjust for your application's needs:
         * - Allow inline scripts: add 'unsafe-inline' to script-src (NOT RECOMMENDED)
         * - Allow specific CDN: add CDN URL to script-src/style-src
         * - Allow data URIs: add 'data:' to img-src
         */
        val policy: String =
            "default-src 'self'; " +
                "script-src 'self'; " +
                "style-src 'self'; " +
                "img-src 'self' data:; " +
                "font-src 'self'; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'",
        val reportOnly: Boolean = false,
    )

    data class FrameOptionsProperties(
        val enabled: Boolean = true,
        /**
         * X-Frame-Options value.
         * Options: DENY, SAMEORIGIN
         */
        val policy: String = "DENY",
    )

    data class ReferrerPolicyProperties(
        val enabled: Boolean = true,
        /**
         * Referrer-Policy value.
         * Options: no-referrer, no-referrer-when-downgrade, origin, origin-when-cross-origin,
         *          same-origin, strict-origin, strict-origin-when-cross-origin, unsafe-url
         */
        val policy: String = "strict-origin-when-cross-origin",
    )

    data class PermissionsPolicyProperties(
        val enabled: Boolean = true,
        /**
         * Permissions-Policy (formerly Feature-Policy).
         * Default: Disable all permission-gated features.
         */
        val policy: String = "geolocation=(), microphone=(), camera=(), payment=(), usb=()",
    )
}

/**
 * Filter that adds security headers to HTTP responses.
 */
class SecurityHeadersFilter(
    private val properties: SecurityHeadersProperties,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (properties.enabled) {
            addSecurityHeaders(response)
        }

        filterChain.doFilter(request, response)
    }

    private fun addSecurityHeaders(response: HttpServletResponse) {
        // Strict-Transport-Security (HSTS)
        if (properties.hsts.enabled) {
            val hstsValue =
                buildString {
                    append("max-age=${properties.hsts.maxAge}")
                    if (properties.hsts.includeSubdomains) {
                        append("; includeSubDomains")
                    }
                    if (properties.hsts.preload) {
                        append("; preload")
                    }
                }
            response.setHeader("Strict-Transport-Security", hstsValue)
        }

        // Content-Security-Policy
        if (properties.csp.enabled) {
            val headerName =
                if (properties.csp.reportOnly) {
                    "Content-Security-Policy-Report-Only"
                } else {
                    "Content-Security-Policy"
                }
            response.setHeader(headerName, properties.csp.policy)
        }

        // X-Frame-Options
        if (properties.frameOptions.enabled) {
            response.setHeader("X-Frame-Options", properties.frameOptions.policy)
        }

        // X-Content-Type-Options (always nosniff)
        response.setHeader("X-Content-Type-Options", "nosniff")

        // X-XSS-Protection (deprecated but still recommended for older browsers)
        response.setHeader("X-XSS-Protection", "1; mode=block")

        // Referrer-Policy
        if (properties.referrerPolicy.enabled) {
            response.setHeader("Referrer-Policy", properties.referrerPolicy.policy)
        }

        // Permissions-Policy
        if (properties.permissionsPolicy.enabled) {
            response.setHeader("Permissions-Policy", properties.permissionsPolicy.policy)
        }

        // Cache-Control for sensitive content (optional, can be overridden per endpoint)
        if (!response.containsHeader("Cache-Control")) {
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            response.setHeader("Pragma", "no-cache")
            response.setHeader("Expires", "0")
        }
    }
}
