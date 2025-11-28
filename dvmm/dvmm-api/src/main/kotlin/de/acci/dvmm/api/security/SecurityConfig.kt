package de.acci.dvmm.api.security

import de.acci.eaf.auth.keycloak.KeycloakJwtAuthenticationConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler

/**
 * Spring Security configuration for DVMM API.
 *
 * Configures OAuth2 Resource Server with Keycloak JWT validation,
 * CORS for frontend access, and authorization rules.
 *
 * Key features:
 * - JWT validation against Keycloak JWKS endpoint
 * - Role extraction from realm_access and resource_access claims
 * - CORS configuration for frontend origin
 * - Actuator health endpoint publicly accessible
 * - All /api/ endpoints require authentication
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig(
    @Value("\${eaf.auth.keycloak.client-id:dvmm-web}")
    private val keycloakClientId: String,
    @Value("\${eaf.cors.allowed-origins:http://localhost:3000}")
    private val allowedOrigins: String,
) {

    /**
     * Configures the security filter chain for WebFlux.
     *
     * Filter chain order:
     * 1. SecurityWebFilter (Spring Security) - runs first (highest precedence)
     * 2. TenantContextWebFilter - runs after (HIGHEST_PRECEDENCE + 10)
     */
    @Bean
    public fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { csrf ->
                csrf
                    .csrfTokenRepository(csrfTokenRepository())
                    .csrfTokenRequestHandler(csrfTokenRequestHandler())
            }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeExchange { auth ->
                auth
                    .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .pathMatchers("/api/csrf").authenticated()
                    .pathMatchers("/api/**").authenticated()
                    .anyExchange().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter())
                }
            }
            .build()
    }

    /**
     * CSRF token repository using cookies.
     *
     * Uses HttpOnly=false so JavaScript can read the XSRF-TOKEN cookie
     * and send it back as X-XSRF-TOKEN header.
     */
    @Bean
    public fun csrfTokenRepository(): CookieServerCsrfTokenRepository {
        return CookieServerCsrfTokenRepository.withHttpOnlyFalse()
    }

    /**
     * CSRF token request handler.
     *
     * Uses the standard handler that compares the cookie value
     * with the X-XSRF-TOKEN header value.
     */
    @Bean
    public fun csrfTokenRequestHandler(): ServerCsrfTokenRequestAttributeHandler {
        return ServerCsrfTokenRequestAttributeHandler()
    }

    /**
     * Custom JWT authentication converter for Keycloak.
     * Extracts roles from realm_access and resource_access claims.
     */
    @Bean
    public fun keycloakJwtAuthenticationConverter(): KeycloakJwtAuthenticationConverter {
        return KeycloakJwtAuthenticationConverter(clientId = keycloakClientId)
    }

    /**
     * CORS configuration for frontend access.
     *
     * Allows:
     * - Origins from configuration (default: http://localhost:3000)
     * - Methods: GET, POST, PUT, DELETE, OPTIONS
     * - All headers
     * - Credentials (cookies, Authorization header)
     */
    @Bean
    public fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = this@SecurityConfig.allowedOrigins.split(",").map { it.trim() }
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}
