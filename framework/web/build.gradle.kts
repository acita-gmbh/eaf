plugins {
    id("eaf.kotlin-common")
    id("eaf.testing-v2") // Story 2.2: Migrated to v2
}

description = "EAF Web Framework - REST controllers and global advice"

dependencies {
    implementation(project(":framework:core"))
    implementation(project(":framework:security"))
    implementation(project(":shared:shared-api"))
    implementation(libs.bundles.kotlin)

    // Spring Modulith for module boundary enforcement
    implementation(libs.spring.modulith.api)
    testImplementation(libs.spring.modulith.test)

    implementation(libs.bundles.spring.boot.web)
    implementation(libs.bundles.spring.boot.security)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.bundles.axon.framework)
    implementation(libs.bundles.arrow)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // Jackson Kotlin Module for Kotlin data class support
    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.axon.test)
}
