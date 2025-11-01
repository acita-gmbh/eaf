plugins {
    id("eaf.kotlin-common")
}

description = "EAF Multi-Tenancy Framework - Tenant isolation and context management"

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)

    testImplementation(libs.bundles.kotest)
}
