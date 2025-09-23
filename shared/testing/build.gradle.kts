plugins {
    id("eaf.kotlin-common")
}

description = "EAF Testing Utilities - Nullable implementations and test helpers"

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.kotest)
    implementation(libs.bundles.testcontainers)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.konsist)

    // Framework testing support
    implementation(project(":framework:core"))
    implementation(project(":framework:persistence"))
}
