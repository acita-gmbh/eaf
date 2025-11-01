plugins {
    id("eaf.kotlin-common")
}

description = "EAF Core Framework - Domain patterns and utilities"

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)

    testImplementation(libs.bundles.kotest)
}
