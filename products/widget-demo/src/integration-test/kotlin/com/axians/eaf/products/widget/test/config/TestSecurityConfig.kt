package com.axians.eaf.products.widget.test.config

import com.axians.eaf.framework.security.revocation.TokenRevocationStore
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import java.time.Instant

/**
 * Test security configuration that permits all requests.
 *
 * **Story 2.10**: Widget REST API Controller tests need unrestricted access
 * since authentication/authorization is implemented in Epic 3.
 *
 * This configuration is only active for the "test" profile and overrides
 * any security configuration from framework/security module.
 *
 * NOTE: Does NOT enable method security (@PreAuthorize) to avoid breaking existing tests.
 * For RBAC tests, see RbacTestSecurityConfig with "rbac-test" profile.
 */
@org.springframework.context.annotation.Configuration
@EnableWebSecurity
@Profile("test")
@Import(TestDslConfiguration::class, TestJpaBypassConfiguration::class)
open class TestSecurityConfig {
    @Bean
    @Primary
    fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()
            }.csrf { csrf ->
                csrf.disable()
            }.build()

    @Bean
    open fun tokenRevocationStore(): TokenRevocationStore =
        object : TokenRevocationStore {
            override fun isRevoked(jti: String): Boolean = false

            override fun revoke(
                jti: String,
                expiresAt: Instant?,
            ) {
                // no-op for tests
            }
        }
}
