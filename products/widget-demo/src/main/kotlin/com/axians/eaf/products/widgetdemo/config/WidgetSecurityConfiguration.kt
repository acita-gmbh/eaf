package com.axians.eaf.products.widgetdemo.config

import com.axians.eaf.framework.security.filters.JwtValidationFilter
import com.axians.eaf.framework.security.filters.TenantContextFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain

/**
 * Product-specific security configuration for Widget API endpoints.
 *
 * Secures /widgets endpoints that were moved from framework to products module.
 * Requires JWT authentication and enables method-level @PreAuthorize annotations.
 *
 * Story 6.3: Product-specific security configuration pattern
 */
@Configuration
@EnableMethodSecurity // Enable @PreAuthorize annotations
@Order(1) // Run before framework filter chain
open class WidgetSecurityConfiguration(
    private val jwtValidationFilter: JwtValidationFilter,
    private val tenantContextFilter: TenantContextFilter,
    private val jwtDecoder: JwtDecoder,
) {
    @Bean
    open fun widgetSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/widgets/**")
            .authorizeHttpRequests { authorize ->
                authorize.anyRequest().authenticated()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.decoder(jwtDecoder)
                }
            }.addFilterAfter(jwtValidationFilter, BearerTokenAuthenticationFilter::class.java)
            .addFilterAfter(tenantContextFilter, JwtValidationFilter::class.java)
            .csrf { csrf -> csrf.disable() }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }.build()
}
