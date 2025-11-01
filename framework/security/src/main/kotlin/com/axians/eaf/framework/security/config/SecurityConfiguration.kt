package com.axians.eaf.framework.security.config

import com.axians.eaf.framework.security.filters.TenantContextFilter
import com.axians.eaf.framework.security.services.SecurityErrorResponseFormatter
import com.axians.eaf.framework.security.services.TenantExtractionService
import com.axians.eaf.framework.security.tenant.TenantContext
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders

/**
 * Spring Security configuration for EAF OAuth2 resource server.
 * Configures JWT authentication with Keycloak OIDC discovery.
 *
 * Story 6.2: Added @Profile("!test") to prevent loading in test environments.
 * Tests provide TenantContext via AxonIntegrationTestConfig instead.
 * Research: 4 external AI sources recommend this as most idiomatic solution.
 */
@Configuration
@EnableWebSecurity
@EnableAspectJAutoProxy
@Import(SecurityFilterChainConfiguration::class)
open class SecurityConfiguration {
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8180/realms/eaf}")
    private lateinit var issuerUri: String

    /**
     * Configures JWT decoder with OIDC discovery.
     * Enhanced validation will be implemented in Story 3.3.
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder::class)
    @ConditionalOnProperty(name = ["eaf.security.enable-oidc-decoder"], havingValue = "true", matchIfMissing = true)
    open fun jwtDecoder(): JwtDecoder = JwtDecoders.fromOidcIssuerLocation(issuerUri)

    @Bean
    open fun tenantContextFilter(
        tenantContext: TenantContext,
        tenantExtractionService: TenantExtractionService,
        errorFormatter: SecurityErrorResponseFormatter,
        meterRegistry: MeterRegistry?,
    ): TenantContextFilter =
        TenantContextFilter(
            tenantContext,
            tenantExtractionService,
            errorFormatter,
            meterRegistry,
        )

    @Bean
    open fun tenantContextFilterRegistration(filter: TenantContextFilter): FilterRegistrationBean<TenantContextFilter> =
        FilterRegistrationBean(filter).apply {
            isEnabled = false
        }
}
