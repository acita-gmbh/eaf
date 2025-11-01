package com.axians.eaf.framework.security.test

import com.axians.eaf.framework.security.services.SecurityErrorResponseFormatter
import com.axians.eaf.framework.security.services.TenantExtractionService
import com.axians.eaf.framework.security.tenant.TenantContext
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Minimal, test-scoped Spring Boot application for Security Framework integration testing.
 *
 * This "hollow application" provides the bootstrap anchor for Spring Boot test contexts
 * while maintaining strict architectural boundaries. It excludes data access components
 * and scans only framework security packages.
 *
 * Critical: This class exists ONLY in test scope and NEVER references product modules.
 *
 * Rationale for restrictive scanBasePackages:
 * - Prevents accidental product module dependencies
 * - Maintains framework isolation for unit testing
 * - Reduces Spring context initialization time
 * - Will be expanded as security module grows (filters, validators, etc.)
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.axians.eaf.framework.security.tenant",
    ],
    exclude = [
        DataSourceAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration::class,
    ],
)
open class SecurityFrameworkTestApplication {
    @Bean
    @Primary
    open fun testMeterRegistry(): MeterRegistry = SimpleMeterRegistry()

    @Bean
    @Primary
    open fun testTenantContext(meterRegistry: MeterRegistry): TenantContext = TenantContext(meterRegistry)

    @Bean
    @Primary
    open fun testJwtDecoder(): JwtDecoder = NullableJwtDecoder.createNull()

    @Bean
    open fun testController(tenantContext: TenantContext): TestController = TestController(tenantContext)

    @Bean
    @Primary
    open fun testTenantExtractionService(): TenantExtractionService = TenantExtractionService()

    @Bean
    @Primary
    open fun testSecurityErrorResponseFormatter(): SecurityErrorResponseFormatter = SecurityErrorResponseFormatter(ObjectMapper())

    @Bean
    open fun testTenantContextFilter(
        tenantContext: TenantContext,
        tenantExtractionService: TenantExtractionService,
        errorFormatter: SecurityErrorResponseFormatter,
        meterRegistry: MeterRegistry,
    ): com.axians.eaf.framework.security.filters.TenantContextFilter =
        com.axians.eaf.framework.security.filters
            .TenantContextFilter(
                tenantContext,
                tenantExtractionService,
                errorFormatter,
                meterRegistry,
            )

    @Bean
    open fun testSecurityFilterChain(
        http: org.springframework.security.config.annotation.web.builders.HttpSecurity,
        tenantContextFilter: com.axians.eaf.framework.security.filters.TenantContextFilter,
    ): org.springframework.security.web.SecurityFilterChain =
        http
            .csrf { csrf ->
                // CSRF disabled for stateless JWT-based API (test configuration)
                // Security rationale documented in SecurityFilterChainConfiguration
                csrf.disable()
            }.authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/test/**")
                    .permitAll()
                    .anyRequest()
                    .permitAll()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.decoder(testJwtDecoder())
                }
            }.addFilterAfter(
                tenantContextFilter,
                org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter::class.java,
            ).build()
}

/**
 * Minimal test controller for framework integration testing.
 * Provides endpoints to validate filter behavior without product dependencies.
 */
@RestController
@RequestMapping("/test")
open class TestController(
    private val tenantContext: TenantContext,
) {
    @GetMapping("/secure-endpoint")
    open fun secureEndpoint(): ResponseEntity<Map<String, String>> {
        val tenantId =
            try {
                tenantContext.current() ?: "no-tenant"
            } catch (e: Exception) {
                "no-tenant"
            }

        return ResponseEntity.ok(
            mapOf(
                "status" to "ok",
                "tenant" to tenantId,
                "timestamp" to Instant.now().toString(),
            ),
        )
    }

    @GetMapping("/health")
    open fun health(): ResponseEntity<String> = ResponseEntity.ok("Framework Security Module - Integration Test Health Check OK")
}
