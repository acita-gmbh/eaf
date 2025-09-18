plugins {
    id("eaf.kotlin-common")
}

description = "EAF Web Framework - REST controllers and global advice"

dependencies {
    implementation(project(":framework:core"))
    implementation(project(":framework:security"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.spring.boot.web)

    testImplementation(libs.bundles.kotest)
}
