package com.axians.eaf.products.widget.test.config

import com.axians.eaf.framework.multitenancy.TenantContext
import com.axians.eaf.framework.security.revocation.TokenRevocationStore
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

/**
 * Test security configuration that permits all requests.
 *
 * **Story 2.10**: Widget REST API Controller tests need unrestricted access
 * since authentication/authorization is implemented in Epic 3.
 *
 * **Story 4.6**: Adds TestTenantContextFilter to set test tenant ID for multi-tenant tests.
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
            .addFilterBefore(TestTenantContextFilter(), SecurityContextHolderFilter::class.java)
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

/**
 * Test filter that sets a default tenant ID for all requests.
 *
 * Simulates the TenantContextFilter (Layer 1) behavior without requiring JWTs.
 * Sets tenant ID to "test-tenant" for all HTTP requests in integration tests.
 */
class TestTenantContextFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            TenantContext.setCurrentTenantId("test-tenant")
            filterChain.doFilter(request, response)
        } finally {
            TenantContext.clearCurrentTenant()
        }
    }
}
