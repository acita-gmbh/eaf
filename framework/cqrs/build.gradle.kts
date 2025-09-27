plugins {
    id("eaf.kotlin-common")
    id("eaf.testing")
    alias(libs.plugins.kotlin.spring)
}

description = "EAF CQRS Framework - Axon Framework integration"

dependencies {
    implementation(project(":framework:core"))
    implementation(project(":framework:security"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.axon.framework)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.micrometer.core)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.axon.test)
    testImplementation(libs.bundles.testcontainers)

    integrationTestImplementation(project(":framework:security"))
    integrationTestImplementation(libs.bundles.kotest)
    integrationTestImplementation(libs.bundles.testcontainers)
    integrationTestImplementation(libs.spring.boot.starter.data.redis)
}
