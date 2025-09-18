plugins {
    id("eaf.kotlin-common")
}

description = "EAF Testing Utilities - Nullable implementations and test helpers"

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.kotest)
    implementation(libs.bundles.testcontainers)
    implementation(libs.mockk)

    // Framework testing support
    implementation(project(":framework:core"))
}
