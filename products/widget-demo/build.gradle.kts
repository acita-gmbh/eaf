// products/widget-demo - Reference implementation demonstrating EAF framework capabilities
// Implements complete CQRS/ES vertical slice with Axon Framework

plugins {
    id("eaf.testing")
    id("eaf.spring-boot")
}

group = "com.axians.eaf.products"
description = "Widget Demo - Reference implementation validating EAF framework capabilities"

dependencies {
    // Framework modules
    implementation(project(":framework:core"))
    implementation(project(":framework:cqrs"))
    implementation(project(":framework:persistence"))

    // Axon Framework for CQRS/ES
    implementation(libs.bundles.axon.framework)

    // Testing
    testImplementation(libs.axon.test)
}
