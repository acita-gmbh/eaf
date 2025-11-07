package com.axians.eaf.products.widget.test.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Test security configuration that permits all requests.
 *
 * **Story 2.10**: Widget REST API Controller tests need unrestricted access
 * since authentication/authorization is implemented in Epic 3.
 *
 * This configuration is only active for the "test" profile and overrides
 * any security configuration from framework/security module.
 */
@org.springframework.context.annotation.Configuration
@EnableWebSecurity
@Profile("test")
class TestSecurityConfig {
    @Bean
    @Primary
    fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()
            }.csrf { csrf ->
                csrf.disable()
            }.build()
}
