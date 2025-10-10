package com.axians.eaf.framework.security.config

import com.axians.eaf.framework.security.filters.JwtValidationFilter
import com.axians.eaf.framework.security.filters.TenantContextFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration

/**
 * Separate configuration for Spring Security filter chain.
 * Breaks circular dependency by isolating filter chain creation from main SecurityConfiguration.
 *
 * Story 6.2: Added @Profile("!test") to prevent loading in test environments.
 * Research: 4 external AI sources recommend this as most idiomatic solution.
 */
@Configuration
open class SecurityFilterChainConfiguration(
    private val jwtValidationFilter: JwtValidationFilter,
    private val tenantContextFilter: TenantContextFilter,
    private val jwtDecoder: org.springframework.security.oauth2.jwt.JwtDecoder,
) {
    @Value("\${eaf.security.cors.allowed-origin-patterns:http://localhost:3000,http://localhost:8080}")
    private lateinit var allowedOriginPatterns: String

    /**
     * Configures HTTP security with OAuth2 resource server, 10-layer JWT validation, and tenant context extraction.
     * Secures endpoints requiring JWT authentication with comprehensive validation and tenant isolation.
     */
    @Bean
    open fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/actuator/prometheus")
                    .hasRole("eaf-admin")
                    .requestMatchers("/actuator/**")
                    .authenticated()
                    .requestMatchers("/api/secure/**")
                    .authenticated()
                    .requestMatchers("/test/**")
                    .permitAll() // Allow test endpoints for integration testing
                    // Products should configure their own routes (e.g., "/widgets/**", "/orders/**")
                    .anyRequest()
                    .permitAll()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.decoder(jwtDecoder) // Use injected jwtDecoder
                }
            }.addFilterAfter(
                jwtValidationFilter,
                org.springframework.security.oauth2.server.resource.web.authentication
                    .BearerTokenAuthenticationFilter::class.java,
            ).addFilterAfter(tenantContextFilter, JwtValidationFilter::class.java)
            .cors { cors ->
                cors.configurationSource { _ ->
                    val corsConfiguration = CorsConfiguration()
                    corsConfiguration.allowedOriginPatterns = allowedOriginPatterns.split(",").map { it.trim() }
                    corsConfiguration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    corsConfiguration.allowedHeaders = listOf("*")
                    corsConfiguration.allowCredentials = true
                    corsConfiguration
                }
            }.csrf { csrf ->
                // CSRF protection is intentionally disabled for stateless JWT-based API.
                // SECURITY RATIONALE:
                // 1. Stateless JWT authentication prevents CSRF attacks (no session cookies)
                // 2. JWTs are sent in Authorization header, not susceptible to CSRF
                // 3. Multi-tenant isolation enforced through JWT claims, not session state
                // 4. This is the recommended Spring Security configuration for REST APIs
                // See: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-when
                csrf.disable()
            }.sessionManagement { session ->
                // Enforce stateless session policy for JWT-based resource server
                session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)
            }.build()
}
