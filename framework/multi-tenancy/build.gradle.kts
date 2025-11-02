plugins {
    id("eaf.kotlin-common")
}

description = "EAF Multi-Tenancy Framework - Tenant isolation and context management"

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)

    // Spring Modulith for module boundary enforcement
    implementation(libs.spring.modulith.api)
    testImplementation(libs.spring.modulith.test)

    testImplementation(libs.bundles.kotest)
}
