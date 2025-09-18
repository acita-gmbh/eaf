plugins {
    id("eaf.kotlin-common")
}

description = "EAF Observability Framework - Metrics, logging, tracing"

dependencies {
    implementation(project(":framework:core"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.observability)

    testImplementation(libs.bundles.kotest)
}
