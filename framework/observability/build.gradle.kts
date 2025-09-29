plugins {
    id("eaf.kotlin-common")
    id("eaf.logging")
    id("eaf.testing")
}

description = "EAF Observability Framework - Metrics, logging, tracing"

dependencies {
    implementation(project(":framework:core"))
    implementation(project(":framework:security"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.observability)
    implementation(libs.bundles.spring.modulith)
    implementation(libs.spring.boot.starter.web)

    testImplementation(libs.bundles.kotest)

    // Integration test dependencies
    integrationTestImplementation(libs.spring.boot.starter.test)
    integrationTestImplementation(project(":shared:testing"))
}
