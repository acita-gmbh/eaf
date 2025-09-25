package com.axians.eaf.framework.security.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders

/**
 * Spring Security configuration for EAF OAuth2 resource server.
 * Configures JWT authentication with Keycloak OIDC discovery.
 */
@Configuration
@EnableWebSecurity
@Import(SecurityFilterChainConfiguration::class)
open class SecurityConfiguration {

    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8180/realms/eaf}")
    private lateinit var issuerUri: String

    /**
     * Configures JWT decoder with OIDC discovery.
     * Enhanced validation will be implemented in Story 3.3.
     */
    @Bean
    open fun jwtDecoder(): JwtDecoder = JwtDecoders.fromOidcIssuerLocation(issuerUri)
}
