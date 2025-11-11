// products/widget-demo - Reference implementation demonstrating EAF framework capabilities
// Implements complete CQRS/ES vertical slice with Axon Framework

plugins {
    id("eaf.testing")
    id("eaf.spring-boot")
    alias(libs.plugins.gatling)
}

group = "com.axians.eaf.products"
description = "Widget Demo - Reference implementation validating EAF framework capabilities"

// ============================================================================
// Gatling Netty 4.2 Isolation (CRITICAL for nightly CI)
// ============================================================================
// Gatling 3.14.0 uses Netty 4.2.1.Final (with IoHandle interface)
// Spring Boot uses Netty 4.1.x (without IoHandle)
// These are fundamentally incompatible major versions.
//
// Solution: Configure Gatling to NOT include main/test output which brings
// Spring Boot's Netty 4.1.x dependencies. Gatling tests must be standalone.
gatling {
    // Disable automatic inclusion of main and test classes
    // This prevents Spring Boot's Netty 4.1.x from polluting Gatling's classpath
    includeMainOutput = false
    includeTestOutput = false
}

// CRITICAL: Explicitly provide Netty 4.2.x to Gatling configuration
// Gatling 3.14.0 requires Netty 4.2.x (EpollIoHandler, IoHandle), incompatible with Spring Boot's 4.1.x
// We exclude Netty from all implementation dependencies and explicitly add comprehensive 4.2.x modules here

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
    // Framework modules - EXCLUDE Netty to prevent version conflicts with Gatling
    // Framework modules have Netty 4.1.125.Final from root enforcement
    // Widget-demo needs Netty flexibility for Gatling 4.2.1.Final
    implementation(project(":framework:core")) {
        exclude(group = "io.netty")
    }
    implementation(project(":framework:cqrs")) {
        exclude(group = "io.netty")
    }
    implementation(project(":framework:persistence")) {
        exclude(group = "io.netty")
    }
    implementation(project(":framework:web")) {
        exclude(group = "io.netty")
    }

    // Spring Boot starters
    // Bean validation for API DTOs (Story 2.10)
    implementation(libs.spring.boot.starter.validation) {
        exclude(group = "io.netty")
    }

    // Axon Framework for CQRS/ES
    implementation(libs.bundles.axon.framework) {
        exclude(group = "io.netty")
    }

    // jOOQ for type-safe SQL (Story 2.6-2.7)
    implementation(libs.bundles.jooq) {
        exclude(group = "io.netty")
    }

    // Metrics for projection monitoring (Story 2.7)
    implementation(libs.micrometer.core) {
        exclude(group = "io.netty")
    }

    // OpenAPI documentation and Swagger UI (Story 2.10)
    implementation(libs.springdoc.openapi.starter.webmvc.ui) {
        exclude(group = "io.netty")
    }

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

    // Integration Testing (Story 2.7)
    integrationTestImplementation(libs.bundles.testcontainers)
    integrationTestImplementation(libs.testcontainers.junit.jupiter)
    integrationTestImplementation(libs.spring.boot.starter.test)
    integrationTestImplementation(libs.bundles.kotest)
    integrationTestImplementation(libs.kotest.extensions.spring)
    integrationTestImplementation(libs.spring.boot.testcontainers)

    // Gatling Performance Testing - Complete Netty 4.2.1.Final stack
    // Gatling 3.14.0 requires Netty 4.2.x (EpollIoHandler, IoHandle classes)
    // These are incompatible with Spring Boot's Netty 4.1.x
    "gatlingImplementation"(libs.netty.buffer)
    "gatlingImplementation"(libs.netty.codec)
    "gatlingImplementation"(libs.netty.codec.http)
    "gatlingImplementation"(libs.netty.codec.http2)
    "gatlingImplementation"(libs.netty.common)
    "gatlingImplementation"(libs.netty.handler)
    "gatlingImplementation"(libs.netty.handler.proxy)
    "gatlingImplementation"(libs.netty.resolver)
    "gatlingImplementation"(libs.netty.resolver.dns)
    "gatlingImplementation"(libs.netty.transport)
    "gatlingImplementation"(libs.netty.transport.classes.epoll)
    "gatlingImplementation"(libs.netty.transport.native.unix.common)
    // Native epoll with Linux x86_64 classifier
    val nettyVersion = libs.versions.netty.gatling
        .get()
    "gatlingImplementation"("io.netty:netty-transport-native-epoll:$nettyVersion:linux-x86_64")
}

// ============================================================================
// Test Performance Optimizations (Story 2.13 - Vector 3)
// ============================================================================

tasks.withType<Test> {
    // Disable jOOQ startup logo and tips (saves fractional-second delay)
    systemProperty("org.jooq.no-tips", "true")
    systemProperty("org.jooq.no-logo", "true")
}
