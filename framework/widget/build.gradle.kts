plugins {
    id("eaf.kotlin-common")
    id("eaf.testing")
}

description = "EAF Widget Framework - Example domain aggregate for CQRS/ES demonstration"

dependencies {
    implementation(project(":framework:core"))
    implementation(project(":shared:shared-api"))

    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.axon.framework)
    implementation(libs.bundles.spring.boot.web)
    implementation(libs.spring.modulith.starter.core)

    testImplementation(project(":shared:testing"))
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.axon.test)
    testImplementation(libs.bundles.testcontainers)
}
