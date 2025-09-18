plugins {
    id("eaf.kotlin-common")
}

description = "EAF Workflow Framework - Flowable BPMN integration"

dependencies {
    implementation(project(":framework:core"))
    implementation(libs.bundles.kotlin)
    implementation(libs.flowable.spring.boot.starter.process)

    testImplementation(libs.bundles.kotest)
}
