plugins {
    id("eaf.testing")
    id("eaf.kotlin-common")
}

description = "EAF Core Framework - Domain patterns and utilities"

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)

    // Spring Modulith for module boundary enforcement
    implementation(libs.spring.modulith.api)
    testImplementation(libs.spring.modulith.test)

    testImplementation(libs.bundles.kotest)
}
