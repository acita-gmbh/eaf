plugins {
    id("eaf.kotlin-common")
    id("eaf.testing")
}

description = "EAF Testing Utilities - Nullable implementations and test helpers"

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.testcontainers)
    implementation(libs.konsist)

    // Framework testing support
    implementation(project(":framework:core"))
}
