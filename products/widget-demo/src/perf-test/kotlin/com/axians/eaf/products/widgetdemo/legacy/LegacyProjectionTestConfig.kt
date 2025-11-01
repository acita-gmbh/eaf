@file:Suppress("DEPRECATION")

package com.axians.eaf.products.widgetdemo.legacy

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Performance test configuration providing both JPA and jOOQ repositories.
 *
 * Uses explicit @Import to include only required auto-configurations:
 * - DataSource (for Testcontainers PostgreSQL)
 * - jOOQ (for jOOQ repository)
 * - Hibernate JPA (for legacy JPA repository)
 *
 * This enables side-by-side comparison to validate AC 15 (≥20% improvement).
 *
 * Pattern: Minimal config approach (no OpenTelemetry, Security, Axon).
 */
@Configuration
@Import(
    DataSourceAutoConfiguration::class,
    JooqAutoConfiguration::class,
    HibernateJpaAutoConfiguration::class,
)
@ComponentScan(
    basePackages = [
        "com.axians.eaf.products.widgetdemo.legacy",
        "com.axians.eaf.products.widgetdemo.repositories", // Include jOOQ repository
    ],
)
@EnableJpaRepositories(basePackageClasses = [LegacyWidgetProjectionJpaRepository::class])
@EntityScan(basePackageClasses = [LegacyWidgetProjectionEntity::class])
open class LegacyProjectionTestConfig
