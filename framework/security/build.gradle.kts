plugins {
    id("eaf.kotlin-common")
}

description = "EAF Security Framework - 10-layer JWT validation and tenant isolation"

dependencies {
    implementation(project(":framework:core"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)
    implementation(libs.jose4j)

    testImplementation(libs.bundles.kotest)
}
