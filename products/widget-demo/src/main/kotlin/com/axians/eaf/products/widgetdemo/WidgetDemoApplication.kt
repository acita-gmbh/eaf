package com.axians.eaf.products.widgetdemo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication

/**
 * Widget Demo Application - Reference implementation for EAF framework.
 *
 * ## Purpose
 *
 * This minimal Spring Boot application serves as:
 * 1. **Reference Implementation**: Demonstrates proper framework consumption patterns
 * 2. **E2E Testing**: Provides full application context for integration tests
 * 3. **Framework Validation**: Validates auto-configuration and component wiring
 * 4. **Developer Onboarding**: Example for teams building products on EAF
 *
 * ## Auto-Configured Framework Features
 *
 * By depending on framework modules, this application automatically receives:
 * - **Multi-Tenancy** (Stories 4.1-4.4):
 *   - Layer 1: TenantContext filter extracts tenant from JWT
 *   - Layer 2: Service boundary validation in command handlers
 *   - Layer 3: PostgreSQL RLS with session variables
 *   - Layer 4: Async propagation for event processors (Story 4.4 - THIS!)
 * - **CQRS/ES**: Axon Framework with tenant-aware interceptors
 * - **Security**: 10-layer JWT validation with Keycloak
 * - **Observability**: Micrometer metrics, distributed tracing
 *
 * ## Framework Auto-Configuration Example (Story 4.4)
 *
 * The `AxonConfiguration` from `framework-cqrs` is auto-discovered via:
 * `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
 *
 * This registers:
 * - TenantEventMessageInterceptor (async tenant propagation)
 * - TenantCorrelationDataProvider (event metadata enrichment)
 *
 * **No manual configuration required!**
 *
 * To disable: `eaf.cqrs.tenant-propagation.enabled=false` in application.yml
 *
 * ## Similar Patterns
 *
 * Inspired by:
 * - Spring Petclinic (spring-projects/spring-petclinic)
 * - Spring Boot Samples (spring-boot/samples)
 * - Axon Quick Start (axoniq/axon-quick-start)
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.axians.eaf.products.widgetdemo",
        "com.axians.eaf.framework",
    ],
)
@EntityScan(
    basePackages = [
        "com.axians.eaf.products.widgetdemo.entities",
        "org.axonframework.eventsourcing.eventstore.jpa",
        "org.axonframework.eventhandling.tokenstore.jpa",
        "org.axonframework.modelling.saga.repository.jpa",
    ],
)
class WidgetDemoApplication

fun main(args: Array<String>) {
    runApplication<WidgetDemoApplication>(*args)
}
