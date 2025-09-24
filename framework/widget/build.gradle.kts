plugins {
    id("eaf.kotlin-common")
    id("eaf.testing")
}

description = "EAF Widget Framework - Example domain aggregate for CQRS/ES demonstration"

dependencies {
    implementation(project(":framework:core"))
    implementation(project(":framework:persistence"))
    implementation(project(":shared:shared-api"))

    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.axon.framework)
    implementation(libs.bundles.spring.boot.web)
    implementation(libs.bundles.database)
    implementation(libs.spring.modulith.starter.core)
    implementation("com.fasterxml.jackson.core:jackson-databind")

    testImplementation(project(":shared:testing"))
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.axon.test)
    testImplementation(libs.bundles.testcontainers)

    integrationTestImplementation(project(":shared:shared-api"))
    integrationTestImplementation(project(":shared:testing"))
    integrationTestImplementation(libs.bundles.kotest)
    integrationTestImplementation(libs.bundles.testcontainers)
    integrationTestImplementation(libs.spring.boot.starter.test)
    integrationTestImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
