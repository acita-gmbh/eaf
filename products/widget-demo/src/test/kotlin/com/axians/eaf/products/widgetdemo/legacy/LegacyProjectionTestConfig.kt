package com.axians.eaf.products.widgetdemo.legacy

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Test configuration for performance benchmarks and validation tests.
 *
 * Provides both:
 * - Legacy JPA repository (for baseline comparison)
 * - New jOOQ repository (for performance validation)
 *
 * This allows side-by-side comparison to validate AC 15 (≥20% improvement).
 *
 * Note: Performance tests in test source set don't need full Spring Boot config.
 * They just need JPA + jOOQ repositories with minimal Spring context.
 */
@Configuration
@ComponentScan(
    basePackages = [
        "com.axians.eaf.products.widgetdemo.legacy",
        "com.axians.eaf.products.widgetdemo.repositories", // Include jOOQ repository
    ],
)
@EnableJpaRepositories(basePackageClasses = [LegacyWidgetProjectionJpaRepository::class])
@EntityScan(basePackageClasses = [LegacyWidgetProjectionEntity::class])
open class LegacyProjectionTestConfig
