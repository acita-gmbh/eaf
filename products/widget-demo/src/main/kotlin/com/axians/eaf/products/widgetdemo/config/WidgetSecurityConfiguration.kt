package com.axians.eaf.products.widgetdemo.config

import com.axians.eaf.framework.security.filters.JwtValidationFilter
import com.axians.eaf.framework.security.filters.TenantContextFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Product-specific security configuration for Widget API endpoints.
 *
 * Secures /widgets endpoints that were moved from framework to products module.
 * Requires JWT authentication and enables method-level @PreAuthorize annotations.
 *
 * Story 6.3: Product-specific security configuration pattern
 * Story 9.1 Security Fix: Environment-aware CORS configuration via Spring properties
 */
@Configuration
@EnableMethodSecurity // Enable @PreAuthorize annotations
@Order(1) // Run before framework filter chain
open class WidgetSecurityConfiguration(
    private val jwtValidationFilter: JwtValidationFilter,
    private val tenantContextFilter: TenantContextFilter,
    private val jwtDecoder: JwtDecoder,
    private val jwtAuthenticationConverter: org.springframework.core.convert.converter.Converter<
        org.springframework.security.oauth2.jwt.Jwt,
        org.springframework.security.authentication.AbstractAuthenticationToken,
    >,
    @param:org.springframework.beans.factory.annotation.Value("\${eaf.security.cors.allowed-origins:http://localhost:5173}")
    private val allowedOrigins: String,
) {
    @Bean
    open fun widgetSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/widgets/**")
            .cors { cors ->
                // Story 9.1: Enable CORS for React-Admin frontend development
                cors.configurationSource(corsConfigurationSource())
            }.authorizeHttpRequests { authorize ->
                authorize.anyRequest().authenticated()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.decoder(jwtDecoder)
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter) // Story 9.1: Extract Keycloak roles
                }
            }.addFilterAfter(jwtValidationFilter, BearerTokenAuthenticationFilter::class.java)
            .addFilterAfter(tenantContextFilter, JwtValidationFilter::class.java)
            .csrf { csrf ->
                // CSRF protection appropriately disabled for stateless REST API
                //
                // Security Rationale (OWASP ASVS V4.2, CWE-352):
                // 1. This is a REST API consumed by non-browser clients (React-Admin SPA)
                // 2. Authentication uses JWT Bearer tokens (not cookies or session IDs)
                // 3. Session policy is STATELESS (no server-side session state)
                // 4. CSRF attacks require browser-based automatic credential inclusion
                //
                // Per Spring Security documentation:
                // "If you are creating a service that is used only by non-browser clients,
                //  you will likely want to disable CSRF protection"
                // Source: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-when
                //
                // Alternative CSRF protection for SPAs (already implemented):
                // - CORS configuration restricts allowed origins
                // - JWT tokens in Authorization header (not cookies)
                // - Short-lived tokens with revocation support
                //
                // lgtm[java/spring-disabled-csrf-protection]
                csrf.disable()
            }.sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }.build()

    /**
     * CORS configuration for React-Admin frontend.
     * Story 9.1: Environment-aware CORS via configuration properties.
     *
     * Configuration:
     * - Dev: eaf.security.cors.allowed-origins=http://localhost:5173,http://localhost:5174
     * - Prod: EAF_SECURITY_CORS_ALLOWED_ORIGINS=https://admin.eaf.example.com
     */
    @Bean
    open fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = allowedOrigins.split(",").map { it.trim() }
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        configuration.allowedHeaders =
            listOf(
                "Authorization", // JWT bearer tokens
                "Content-Type", // Request body format
                "Accept", // Response format
                "Range", // Pagination support
            )
        configuration.allowCredentials = true
        configuration.exposedHeaders = listOf("Content-Range", "X-Total-Count")

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
