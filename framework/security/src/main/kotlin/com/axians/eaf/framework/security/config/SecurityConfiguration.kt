package com.axians.eaf.framework.security.config

import com.axians.eaf.framework.security.filters.JwtValidationFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration

/**
 * Spring Security configuration for EAF OAuth2 resource server.
 * Configures JWT authentication with Keycloak OIDC discovery.
 */
@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    private val jwtValidationFilter: JwtValidationFilter,
) {
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8180/realms/eaf}")
    private lateinit var issuerUri: String

    @Value("\${eaf.security.cors.allowed-origin-patterns:http://localhost:3000,http://localhost:8080}")
    private lateinit var allowedOriginPatterns: String

    /**
     * Configures JWT decoder with OIDC discovery.
     * Enhanced validation will be implemented in Story 3.3.
     */
    @Bean
    fun jwtDecoder(): JwtDecoder = JwtDecoders.fromOidcIssuerLocation(issuerUri)

    /**
     * Configures HTTP security with OAuth2 resource server and 10-layer JWT validation.
     * Secures endpoints requiring JWT authentication with comprehensive validation.
     */
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/actuator/**")
                    .permitAll()
                    .requestMatchers("/api/secure/**")
                    .authenticated()
                    .requestMatchers("/widgets/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.decoder(jwtDecoder())
                }
            }.addFilterAfter(jwtValidationFilter, BearerTokenAuthenticationFilter::class.java)
            .cors { cors ->
                cors.configurationSource { _ ->
                    val corsConfiguration = CorsConfiguration()
                    corsConfiguration.allowedOriginPatterns = allowedOriginPatterns.split(",").map { it.trim() }
                    corsConfiguration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    corsConfiguration.allowedHeaders = listOf("*")
                    corsConfiguration.allowCredentials = true
                    corsConfiguration
                }
            }.csrf { csrf -> csrf.disable() }
            .build()
}
