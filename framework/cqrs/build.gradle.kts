plugins {
    id("eaf.kotlin-common")
}

description = "EAF CQRS Framework - Axon Framework integration"

dependencies {
    implementation(project(":framework:core"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.axon.framework)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.axon.test)
}
