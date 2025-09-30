plugins {
    id("eaf.kotlin-common")
    id("eaf.logging")
    id("eaf.observability")
    id("eaf.testing")
}

description = "EAF Observability Framework - Metrics, logging, tracing"

dependencies {
    implementation(project(":framework:core"))
    implementation(project(":framework:security"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.spring.modulith)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.opentelemetry.sdk.testing)

    // Integration test dependencies
    integrationTestImplementation(libs.spring.boot.starter.test)
    integrationTestImplementation(project(":shared:testing"))
    integrationTestImplementation(libs.spring.boot.starter.security)
}
