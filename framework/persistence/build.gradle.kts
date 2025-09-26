plugins {
    id("eaf.kotlin-common")
    id("eaf.testing")
}

description = "EAF Persistence Framework - jOOQ adapters and projections"

sourceSets {
    named("integrationTest") {
        resources.srcDir("src/integration-test/resources")
    }
}

tasks.named<Test>("ciIntegrationTest") {
    filter.setFailOnNoMatchingTests(false)
}

dependencies {
    implementation(project(":framework:core"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.database)
    implementation(libs.bundles.jooq)

    testImplementation(project(":framework:security"))
    testImplementation(project(":shared:testing"))
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.spring.boot.starter.test)
}
