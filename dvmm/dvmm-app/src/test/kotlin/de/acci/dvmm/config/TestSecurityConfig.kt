package de.acci.dvmm.config

import de.acci.eaf.auth.keycloak.KeycloakJwtAuthenticationConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

/**
 * Test security configuration with CSRF disabled.
 *
 * This configuration is only active with @Profile("test") and provides
 * the same security setup as SecurityConfig but without CSRF protection
 * to simplify integration testing.
 *
 * Note: CSRF is disabled ONLY for tests. Production always has CSRF enabled.
 */
@Configuration
@EnableWebFluxSecurity
@Profile("test")
public class TestSecurityConfig(
    @Value("\${eaf.auth.keycloak.client-id:dvmm-web}")
    private val keycloakClientId: String,
    @Value("\${eaf.cors.allowed-origins:http://localhost:3000}")
    private val allowedOrigins: String,
) {

    @Bean
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    public fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        // CSRF disabled for test profile only - simplifies integration testing
        // This is intentional - test code doesn't need CSRF protection
        @Suppress("DEPRECATION") // CodeQL: intentionally disabled for tests
        http.csrf { it.disable() } // codeql[java/spring-disabled-csrf-protection] suppressed - test code

        return http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeExchange { auth ->
                auth
                    .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
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

    @Bean
    public fun keycloakJwtAuthenticationConverter(): KeycloakJwtAuthenticationConverter {
        return KeycloakJwtAuthenticationConverter(clientId = keycloakClientId)
    }

    @Bean
    public fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = this@TestSecurityConfig.allowedOrigins.split(",").map { it.trim() }
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
