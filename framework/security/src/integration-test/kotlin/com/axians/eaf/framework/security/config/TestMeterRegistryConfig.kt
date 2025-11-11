package com.axians.eaf.framework.security.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class TestMeterRegistryConfig {
    @Bean
    open fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()
}
