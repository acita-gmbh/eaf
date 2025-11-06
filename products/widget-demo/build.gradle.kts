// products/widget-demo - Reference implementation demonstrating EAF framework capabilities
// Implements complete CQRS/ES vertical slice with Axon Framework

plugins {
    id("eaf.testing")
    id("eaf.spring-boot")
}

group = "com.axians.eaf.products"
description = "Widget Demo - Reference implementation validating EAF framework capabilities"

// Temporary workaround for Detekt Kotlin 2.2.21 compatibility issue
// See: https://github.com/detekt/detekt/issues/6198
// TODO: Remove when Detekt 2.x stable is released with Kotlin 2.2.x support
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    enabled = false
}

dependencies {
    // Framework modules
    implementation(project(":framework:core"))
    implementation(project(":framework:cqrs"))
    implementation(project(":framework:persistence"))

    // Axon Framework for CQRS/ES
    implementation(libs.bundles.axon.framework)

    // jOOQ for type-safe SQL (Story 2.6-2.7)
    implementation(libs.bundles.jooq)

    // Metrics for projection monitoring (Story 2.7)
    implementation(libs.micrometer.core)

    // Testing
    testImplementation(libs.axon.test)
}
