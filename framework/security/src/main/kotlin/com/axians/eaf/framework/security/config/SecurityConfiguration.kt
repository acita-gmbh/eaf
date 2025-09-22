package com.axians.eaf.framework.security.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration

/**
 * Spring Security configuration for EAF OAuth2 resource server.
 * Configures JWT authentication with Keycloak OIDC discovery.
 */
@Configuration
@EnableWebSecurity
class SecurityConfiguration {
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8180/realms/eaf}")
    private lateinit var issuerUri: String

    /**
     * Configures JWT decoder with OIDC discovery.
     * Enhanced validation will be implemented in Story 3.3.
     */
    @Bean
    fun jwtDecoder(): JwtDecoder = JwtDecoders.fromOidcIssuerLocation(issuerUri)

    /**
     * Configures HTTP security with OAuth2 resource server.
     * Secures endpoints requiring JWT authentication.
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
                    .anyRequest()
                    .permitAll()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.decoder(jwtDecoder())
                }
            }.cors { cors ->
                cors.configurationSource { _ ->
                    val corsConfiguration = CorsConfiguration()
                    corsConfiguration.allowedOriginPatterns =
                        listOf(
                            "http://localhost:3000", // React dev server
                            "http://localhost:8080", // Backend dev server
                            "https://*.axians.com", // Production domains
                        )
                    corsConfiguration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    corsConfiguration.allowedHeaders = listOf("*")
                    corsConfiguration.allowCredentials = true
                    corsConfiguration
                }
            }.csrf { csrf -> csrf.disable() }
            .build()
}
