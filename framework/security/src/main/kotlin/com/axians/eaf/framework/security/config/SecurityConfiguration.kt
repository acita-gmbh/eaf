package com.axians.eaf.framework.security.config

import com.axians.eaf.framework.security.validation.JwtAlgorithmValidator
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
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
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
     * Creates a JwtDecoder configured to use the Keycloak JWKS endpoint.
     *
     * The decoder fetches public keys from the configured JWKS URI and validates
     * JWT signatures and standard claims.
     *
     * JWT Validation Layers (Story 3.4):
     * - Layer 1: Format validation (3-part structure) - handled by NimbusJwtDecoder
     * - Layer 2: Signature validation (RS256 with JWKS) - handled by NimbusJwtDecoder
     * - Layer 3: Algorithm validation (RS256 only, reject HS256) - JwtAlgorithmValidator
     * - Layer 5: Timestamp validation (exp, iat, nbf) - JwtTimestampValidator
     *
     * JWKS Caching (Story 3.2):
     * - NimbusJwtDecoder includes built-in JWKS caching (default: 5 minutes)
     * - KeycloakJwksProvider provides additional caching layer with configurable
     *   duration (default: 10 minutes) for use cases requiring explicit cache control
     * - Cache refresh triggered automatically on cache miss or expiration
     * - Graceful handling of JWKS rotation via cache invalidation
     *
     * @return a JwtDecoder that validates JWTs using the configured JWKS endpoint
     */
    @Bean
    open fun jwtDecoder(): JwtDecoder {
        val decoder =
            NimbusJwtDecoder
                .withJwkSetUri(keycloakConfig.jwksUri)
                .build()

        // Story 3.4: Add explicit validators for Layers 3 and 5
        val validators =
            DelegatingOAuth2TokenValidator(
                JwtTimestampValidator(), // Layer 5: exp, iat, nbf validation
                JwtAlgorithmValidator(), // Layer 3: RS256 enforcement (reject HS256)
            )

        decoder.setJwtValidator(validators)

        return decoder
    }
}
