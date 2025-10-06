package com.axians.eaf.products.widgetdemo.test

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.JwtDecoder

/**
 * Test configuration for widget-demo integration tests.
 *
 * Provides test-specific beans to override defaults.
 */
@Configuration
open class WidgetDemoTestApplication {
    @Bean
    @Primary
    open fun testMeterRegistry(): MeterRegistry = SimpleMeterRegistry()

    @Bean
    @Primary
    open fun testJwtDecoder(): JwtDecoder = NullableJwtDecoder.createNull(mapOf("tenant_id" to "550e8400-e29b-41d4-a716-446655440000"))
}
