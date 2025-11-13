package com.axians.eaf.framework.security.config

import com.axians.eaf.framework.security.InjectionDetector
import com.axians.eaf.framework.security.role.RoleNormalizer
import com.axians.eaf.framework.security.user.UserDirectory
import com.axians.eaf.framework.security.validation.JwtAlgorithmValidator
import com.axians.eaf.framework.security.validation.JwtAudienceValidator
import com.axians.eaf.framework.security.validation.JwtClaimSchemaValidator
import com.axians.eaf.framework.security.validation.JwtInjectionValidator
import com.axians.eaf.framework.security.validation.JwtIssuerValidator
import com.axians.eaf.framework.security.validation.JwtRevocationValidator
import com.axians.eaf.framework.security.validation.JwtTimeBasedValidator
import com.axians.eaf.framework.security.validation.JwtUserValidator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
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
    // Suppress VarCouldBeVal: lateinit properties must be declared as var (Kotlin language requirement)
    @Suppress("VarCouldBeVal")
    @Autowired
    private lateinit var keycloakConfig: KeycloakOidcConfiguration

    @Suppress("VarCouldBeVal")
    @Autowired
    private lateinit var roleNormalizer: RoleNormalizer

    // Story 3.9: All validators autowired as @Component beans
    @Suppress("VarCouldBeVal")
    @Autowired
    private lateinit var algorithmValidator: JwtAlgorithmValidator

    @Suppress("VarCouldBeVal")
    @Autowired
    private lateinit var claimSchemaValidator: JwtClaimSchemaValidator

    @Suppress("VarCouldBeVal")
    @Autowired
    private lateinit var timeBasedValidator: JwtTimeBasedValidator

    @Suppress("VarCouldBeVal")
    @Autowired
    private lateinit var issuerValidator: JwtIssuerValidator

    @Suppress("VarCouldBeVal")
    @Autowired
    private lateinit var audienceValidator: JwtAudienceValidator

    @Suppress("VarCouldBeVal")
    @Autowired
    private lateinit var revocationValidator: JwtRevocationValidator

    @Suppress("VarCouldBeVal")
    @Autowired
    private lateinit var userValidator: JwtUserValidator

    @Suppress("VarCouldBeVal")
    @Autowired
    private lateinit var injectionValidator: JwtInjectionValidator

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
        val jwtAuthenticationConverter =
            JwtAuthenticationConverter().apply {
                setJwtGrantedAuthoritiesConverter { jwt -> roleNormalizer.normalize(jwt).toList() }
            }

        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwtConfigurer ->
                    jwtConfigurer.jwtAuthenticationConverter(jwtAuthenticationConverter)
                }
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
     * Story 3.8: Added Layers 9-10 (User Validation and Injection Detection) to validation chain.
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

        // Story 3.5, 3.7, 3.8 & 3.9: All 10 JWT validation layers with per-layer metrics
        // Compose with Spring Security defaults for defense-in-depth
        // Note: createDefault() already includes JwtTimestampValidator (exp, nbf validation)
        // Our JwtTimeBasedValidator extends this with iat validation and configurable clock skew
        val defaults = JwtValidators.createDefault()
        val customValidators =
            DelegatingOAuth2TokenValidator(
                defaults, // Preserve Spring Security baseline protections (Layers 1-2)
                algorithmValidator, // Layer 3: RS256 enforcement (reject HS256)
                claimSchemaValidator, // Layer 4: Required claims validation
                timeBasedValidator, // Layer 5: Enhanced time validation (iat + configurable skew)
                issuerValidator, // Layer 6: Issuer validation
                audienceValidator, // Layer 6: Audience validation
                revocationValidator, // Layer 7: Redis revocation cache enforcement
                userValidator, // Layer 9: Optional user validation (active user enforcement)
                injectionValidator, // Layer 10: Injection detection
            )

        decoder.setJwtValidator(customValidators)

        return decoder
    }
}
