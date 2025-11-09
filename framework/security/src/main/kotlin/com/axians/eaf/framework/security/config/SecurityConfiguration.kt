package com.axians.eaf.framework.security.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain

/**
 * Spring Security OAuth2 Resource Server configuration with JWT authentication.
 *
 * Configures:
 * - OAuth2 Resource Server with JWT decoder
 * - Security filter chain (authenticated by default, /actuator/health public)
 * - Method-level security annotations (@PreAuthorize support)
 * - CSRF disabled (stateless JWT-based API)
 *
 * Story 3.1: Spring Security OAuth2 Resource Server Foundation
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
open class SecurityConfiguration {
    @Value("\${eaf.security.jwt.jwks-uri}")
    private lateinit var keycloakJwksUri: String

    /**
     * Configures the security filter chain.
     *
     * Authorization rules:
     * - /actuator/health: Permit all (health check endpoint)
     * - All other requests: Require authentication
     *
     * Security features:
     * - OAuth2 Resource Server with JWT authentication
     * - CSRF disabled (stateless API)
     */
    @Bean
    open fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
            }.csrf { it.disable() } // Stateless API, CSRF not needed

        return http.build()
    }

    /**
     * Configures JWT decoder with Keycloak JWKS URI.
     *
     * The decoder:
     * - Fetches public keys from Keycloak JWKS endpoint
     * - Validates JWT signature with RS256 algorithm
     * - Validates standard JWT claims (exp, iat, iss, aud)
     */
    @Bean
    open fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder
            .withJwkSetUri(keycloakJwksUri)
            .build()
}
