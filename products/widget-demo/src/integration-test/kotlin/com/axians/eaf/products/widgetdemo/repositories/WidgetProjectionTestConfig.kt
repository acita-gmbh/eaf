@file:Suppress("DEPRECATION")

package com.axians.eaf.products.widgetdemo.repositories

import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * Minimal test application for jOOQ Widget Projection integration tests.
 *
 * This "hollow application" provides the bootstrap anchor for Spring Boot test contexts
 * while maintaining minimal dependencies. It includes only:
 * - DataSource auto-configuration
 * - jOOQ auto-configuration
 * - WidgetProjectionRepository component
 *
 * Excludes:
 * - Observability framework (avoids OpenTelemetry dependencies)
 * - Security framework (not needed for repository tests)
 * - Web framework (repository-only tests)
 *
 * Pattern Reference: framework/security/test/SecurityFrameworkTestApplication.kt
 *
 * Note: @Suppress("DEPRECATION") required for OpenTelemetryAutoConfiguration exclusion
 * which is deprecated in Spring Boot 3.5.6 but necessary to avoid test classpath issues.
 */
@SpringBootApplication(
    scanBasePackages = ["com.axians.eaf.products.widgetdemo.repositories"],
    exclude = [
        org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryAutoConfiguration::class,
        org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration::class,
        org.springframework.boot.actuate.autoconfigure.metrics.export.otlp.OtlpMetricsExportAutoConfiguration::class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration::class,
    ],
)
open class WidgetProjectionTestConfig
