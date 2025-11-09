package com.axians.eaf.framework.security.config

import com.axians.eaf.framework.security.validation.JwtAlgorithmValidator
import com.axians.eaf.framework.security.validation.JwtClaimSchemaValidator
import com.axians.eaf.framework.security.validation.JwtTimeBasedValidator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
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
 * - JWKS caching with configurable duration (Story 3.2)
 *
 * Note: @Profile("!test") isolates this configuration from test profile where
 * security is disabled for integration testing convenience.
 *
 * Story 3.1: Spring Security OAuth2 Resource Server Foundation
 * Story 3.2: Keycloak OIDC Discovery and JWKS Integration
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Profile("!test")
open class SecurityConfiguration {
    @Autowired
    private lateinit var keycloakConfig: KeycloakOidcConfiguration

    /**
     * Configures the application's HTTP security filter chain.
     *
     * Permits unauthenticated access to /actuator/health; requires authentication
     * for all other requests. Enables OAuth2 Resource Server support with JWT
     * authentication, disables CSRF, and configures stateless session management.
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
     * Create a JwtDecoder that validates tokens against the configured Keycloak JWKS endpoint.
     *
     * Validates signatures using keys obtained from keycloakConfig.jwksUri, enforces the RS256
     * signing algorithm, and validates standard timestamp claims (`exp`, `iat`, `nbf`).
     *
     * @return a JwtDecoder which verifies signatures with the Keycloak JWKS endpoint, enforces
     * RS256, and validates token timestamps
     */
    @Bean
    open fun jwtDecoder(): JwtDecoder {
        val decoder =
            NimbusJwtDecoder
                .withJwkSetUri(keycloakConfig.jwksUri)
                .build()

        // Story 3.5: Add explicit validators for Layers 3, 4, and 5
        // Compose with Spring Security defaults for defense-in-depth
        // Note: createDefault() already includes JwtTimestampValidator (exp, nbf validation)
        // Our JwtTimeBasedValidator extends this with iat validation and configurable clock skew
        val defaults = JwtValidators.createDefault()
        val customValidators =
            DelegatingOAuth2TokenValidator(
                defaults, // Preserve Spring Security baseline protections
                JwtAlgorithmValidator(), // Layer 3: RS256 enforcement (reject HS256)
                JwtClaimSchemaValidator(), // Layer 4: Required claims validation
                JwtTimeBasedValidator(), // Layer 5: Enhanced time validation (iat + configurable skew)
            )

        decoder.setJwtValidator(customValidators)

        return decoder
    }
}
