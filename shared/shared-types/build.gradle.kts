plugins {
    id("eaf.kotlin-common")
}

description = "EAF Shared Types - TypeScript interfaces and common types"

dependencies {
    implementation(libs.bundles.kotlin)

    testImplementation(libs.bundles.kotest)
}
