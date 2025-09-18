plugins {
    id("eaf.spring-boot")
    id("eaf.testing")
    id("eaf.quality-gates")
}

description = "EAF Licensing Server - First product implementation (Epic 8)"

dependencies {
    // Framework dependencies
    implementation(project(":framework:core"))
    implementation(project(":framework:security"))
    implementation(project(":framework:cqrs"))
    implementation(project(":framework:web"))
    implementation(project(":framework:persistence"))

    // Shared APIs
    implementation(project(":shared:shared-api"))

    // Axon Framework
    implementation(libs.bundles.axon.framework)

    // Testing
    testImplementation(project(":shared:testing"))
}
