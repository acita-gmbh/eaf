plugins {
    id("eaf.kotlin-common")
}

description = "EAF Web Framework - REST controllers and global advice"

dependencies {
    implementation(project(":framework:core"))
    implementation(project(":framework:security"))
    implementation(project(":shared:shared-api"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.spring.boot.web)
    implementation(libs.bundles.axon.framework)
    implementation(libs.bundles.arrow)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.axon.test)
}
