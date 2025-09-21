plugins {
    id("eaf.kotlin-common")
    id("eaf.testing")
}

description = "EAF Persistence Framework - jOOQ adapters and projections"

dependencies {
    implementation(project(":framework:core"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.database)
    implementation(libs.bundles.jooq)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.bundles.testcontainers)
}
