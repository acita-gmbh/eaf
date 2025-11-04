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

dependencies {
    implementation(project(":framework:core"))
    implementation(project(":framework:cqrs"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.database)
    implementation(libs.bundles.jooq)

    // Axon Framework JDBC Event Store
    implementation(libs.axon.spring.boot.starter)

    // Flyway Database Migrations
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    // Spring Boot for @Configuration support
    compileOnly(libs.spring.boot.starter.web)

    // Spring Modulith for module boundary enforcement
    implementation(libs.spring.modulith.api)
    testImplementation(libs.spring.modulith.test)

    testImplementation(project(":framework:security"))
    testImplementation(project(":shared:testing"))
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.spring.boot.starter.test)
}
