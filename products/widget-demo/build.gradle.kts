// products/widget-demo - Reference implementation demonstrating EAF framework capabilities
// Implements complete CQRS/ES vertical slice with Axon Framework

plugins {
    id("eaf.testing-v2") // Story 2.3: Complete rollout - migrated to v2
    id("eaf.spring-boot")
    alias(libs.plugins.gatling)
}

group = "com.axians.eaf.products"
description = "Widget Demo - Reference implementation validating EAF framework capabilities"

// JUnit 6 Unified Versioning: Force all Platform components to match Jupiter 6.0.1
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.junit.platform") {
            useVersion("6.0.1")
            because("JUnit 6 requires unified versioning - Platform + Jupiter must both be 6.0.1")
        }
    }
}

configurations.configureEach {
    exclude(group = "org.springframework.modulith", module = "spring-modulith-events-jpa")
    exclude(group = "org.springframework.modulith", module = "spring-modulith-starter-jpa")
    exclude(group = "org.springframework.modulith", module = "spring-modulith-starter-core")
    exclude(group = "org.springframework.modulith", module = "spring-modulith-api")
}

// Temporary workaround for Detekt Kotlin 2.2.21 compatibility issue
// See: https://github.com/detekt/detekt/issues/6198
// TODO: Remove when Detekt 2.x stable is released with Kotlin 2.2.x support
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    enabled = false
}

// Story 2.7: Add integration-test resources to classpath (missing from convention plugin)
sourceSets {
    named("integrationTest") {
        resources.srcDir("src/integration-test/resources")
    }
}

dependencies {
    // Framework modules
    implementation(project(":framework:core"))
    implementation(project(":framework:cqrs"))
    implementation(project(":framework:persistence"))
    implementation(project(":framework:web"))
    implementation(project(":framework:security"))
    implementation(project(":framework:multi-tenancy")) // Story 4.6: Multi-tenancy support

    // Spring Boot starters
    // Bean validation for API DTOs (Story 2.10)
    implementation(libs.spring.boot.starter.validation)

    // Axon Framework for CQRS/ES
    implementation(libs.bundles.axon.framework)

    // jOOQ for type-safe SQL (Story 2.6-2.7)
    implementation(libs.bundles.jooq)

    // Metrics for projection monitoring (Story 2.7)
    implementation(libs.micrometer.core)

    // OpenAPI documentation and Swagger UI (Story 2.10)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Exclude OpenTelemetry dependencies only (Story 5.x - Observability Epic)
    // Prevents version conflict: Spring Boot 3.5.7 expects OpenTelemetry 1.49.0, framework has 1.55.0
    // Keeps Actuator + MeterRegistry (needed for Story 2.7 metrics)
    configurations.all {
        exclude(group = "io.opentelemetry")
        exclude(group = "io.opentelemetry.instrumentation")
        exclude(group = "io.opentelemetry.semconv")
    }

    // Testing
    testImplementation(libs.axon.test)
    testImplementation(libs.hamcrest) // Required by Axon Test Framework

    // Integration Testing (Story 2.7)
    integrationTestImplementation(libs.bundles.testcontainers)
    integrationTestImplementation(libs.testcontainers.junit.jupiter)
    integrationTestImplementation(libs.spring.boot.starter.test)
    integrationTestImplementation(libs.spring.boot.testcontainers)
    integrationTestImplementation(project(":shared:testing"))
    integrationTestImplementation("org.springframework:spring-jdbc")
    integrationTestImplementation(libs.kotlin.coroutines.core)

    // Spring Security Test Support (Story 3.10)
    integrationTestImplementation(libs.spring.security.test)
}

// ============================================================================
// Test Performance Optimizations (Story 2.13 - Vector 3)
// ============================================================================

tasks.withType<Test> {
    // Disable jOOQ startup logo and tips (saves fractional-second delay)
    systemProperty("org.jooq.no-tips", "true")
    systemProperty("org.jooq.no-logo", "true")
}
