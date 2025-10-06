package com.axians.eaf.products.widgetdemo.repositories

import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Ultra-minimal test configuration for jOOQ Widget Projection integration tests.
 *
 * Explicitly imports ONLY the required auto-configurations:
 * - DataSource auto-configuration (for Testcontainers PostgreSQL)
 * - jOOQ auto-configuration (for DSLContext bean)
 * - WidgetProjectionRepository component scan
 *
 * This approach avoids all framework auto-configuration scanning which causes
 * OpenTelemetry ComponentLoader version conflicts (issue 8.3-BUILD-001).
 *
 * Pattern: Explicit import over classpath scanning for focused integration tests.
 */
@Configuration
@Import(
    DataSourceAutoConfiguration::class,
    JooqAutoConfiguration::class,
)
@ComponentScan(basePackageClasses = [JooqWidgetProjectionRepository::class])
open class WidgetProjectionTestConfig
