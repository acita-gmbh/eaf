package com.axians.eaf.products.widgetdemo.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
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
open class WidgetSecurityConfiguration {
    @Bean
    open fun widgetSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .securityMatcher("/widgets/**")
            .authorizeHttpRequests { authorize ->
                authorize.requestMatchers("/widgets/**").authenticated()
            }.build()
}
