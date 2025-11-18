package com.axians.eaf.framework.multitenancy.config

import com.axians.eaf.framework.multitenancy.TenantContextFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration to disable servlet container auto-registration of TenantContextFilter.
 *
 * **"Two Filter Chain" Problem (Root Cause #2):**
 * - Spring Boot auto-registers @Component Filter beans in servlet container chain
 * - Spring Security has its own internal SecurityFilterChain
 * - TenantContextFilter needs to run INSIDE SecurityFilterChain (after JWT auth)
 * - @Order annotation does NOT control ordering relative to Spring Security filters
 *
 * **Solution:**
 * - Disable auto-registration here (prevents filter from running in servlet chain)
 * - SecurityConfiguration manually adds filter to SecurityFilterChain
 * - Filter runs at correct position: AFTER BearerTokenAuthenticationFilter
 *
 * **References:**
 * - External AI Analysis: "Two Filter Chain" architectural flaw
 * - Spring Boot docs: Filter registration behavior
 * - Story 4.2 debugging: 20 commits investigation
 *
 * Epic 4, Story 4.2: Layer 1 - JWT Tenant Extraction
 */
@Configuration
open class TenantSecurityConfiguration(
    private val tenantContextFilter: TenantContextFilter,
) {
    /**
     * Disable default servlet container registration of TenantContextFilter.
     *
     * **Why this is necessary:**
     * - @Component Filter beans are auto-registered by Spring Boot
     * - Auto-registration order is unreliable (often runs BEFORE Spring Security)
     * - This causes filter to see empty SecurityContextHolder
     *
     * **How this works:**
     * - FilterRegistrationBean with enabled=false prevents servlet registration
     * - Filter bean still exists and can be autowired
     * - SecurityConfiguration injects filter into SecurityFilterChain manually
     * - Filter runs only once, at correct position
     *
     * **Without this fix:**
     * - Filter runs TWICE: once before Security, once inside Security chain
     * - First execution sees no JWT, skips tenant extraction
     * - Second execution would work but is wasteful
     */
    @Bean
    open fun disableTenantFilterAutoRegistration(): FilterRegistrationBean<TenantContextFilter> {
        val registration = FilterRegistrationBean(tenantContextFilter)
        registration.isEnabled = false
        return registration
    }
}
