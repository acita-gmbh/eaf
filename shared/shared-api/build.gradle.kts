plugins {
    id("eaf.kotlin-common")
}

description = "EAF Shared API - Axon commands, events, queries"

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.axon.framework)

    testImplementation(libs.bundles.kotest)
}
