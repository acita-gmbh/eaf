package com.axians.eaf.framework.observability.metrics

import com.axians.eaf.framework.security.tenant.TenantContext
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.util.Properties

@Configuration
open class MetricsConfiguration {
    @Bean
    open fun prometheusMeterRegistry(environment: Environment): PrometheusMeterRegistry =
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT).apply {
            val serviceName = environment.getProperty("spring.application.name") ?: "unknown-service"
            config().commonTags("service_name", serviceName)
        }

    @Bean
    open fun customMetrics(
        meterRegistry: MeterRegistry,
        tenantContext: TenantContext,
    ): CustomMetrics = CustomMetrics(meterRegistry, tenantContext)

    @Bean
    open fun prometheusScrapeEndpoint(registry: PrometheusMeterRegistry): PrometheusScrapeEndpoint =
        PrometheusScrapeEndpoint(registry.prometheusRegistry, Properties())
}
