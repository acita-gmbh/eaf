package com.axians.eaf.framework.security.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
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
 * - Stateless session management (no HTTP sessions)
 *
 * Note: @Profile("!test") isolates this configuration from test profile where
 * security is disabled for integration testing convenience.
 *
 * Story 3.1: Spring Security OAuth2 Resource Server Foundation
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Profile("!test")
open class SecurityConfiguration {
    @Value("\${eaf.security.jwt.jwks-uri}")
    private lateinit var keycloakJwksUri: String

    /**
     * Configures the application's HTTP security filter chain.
     *
     * Permits unauthenticated access to /actuator/health; requires authentication for all other requests.
     * Enables OAuth2 Resource Server support with JWT authentication, disables CSRF, and configures stateless session management.
     *
     * @return The configured SecurityFilterChain.
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
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }

        return http.build()
    }

    /**
             * Creates a JwtDecoder configured to use the Keycloak JWKS endpoint.
             *
             * The decoder fetches public keys from the configured JWKS URI and validates JWT signatures and standard claims such as expiration, issuance time, issuer, and audience.
             *
             * @return a JwtDecoder that validates JWTs using the configured JWKS endpoint
             */
    @Bean
    open fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder
            .withJwkSetUri(keycloakJwksUri)
            .build()
}