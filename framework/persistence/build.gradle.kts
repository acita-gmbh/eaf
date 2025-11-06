plugins {
    id("eaf.kotlin-common")
    id("eaf.testing")
    alias(libs.plugins.jooq.codegen)
}

description = "EAF Persistence Framework - jOOQ adapters and projections"

// Exclude generated jOOQ sources from ktlint checks
configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    filter {
        exclude("**/kotlin-generated/**")
    }
}

sourceSets {
    named("integrationTest") {
        resources.srcDir("src/integration-test/resources")
    }
    named("main") {
        // Committed generated jOOQ sources (available in CI without DB)
        // Path contains 'generated' to match ktlint exclude pattern
        java.srcDir("src/main/generated-kotlin/jooq")
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

    // Jackson for Kotlin serialization support
    implementation(libs.jackson.module.kotlin)

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

    // jOOQ code generation dependencies
    jooqCodegen(libs.postgresql)
    jooqCodegen(libs.jooq.codegen)
}

// jOOQ Code Generation Configuration
jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = System.getenv("JOOQ_DB_URL") ?: "jdbc:postgresql://localhost:5432/eaf"
            user = System.getenv("JOOQ_DB_USER") ?: "eaf_user"
            password = System.getenv("JOOQ_DB_PASSWORD") ?: "eaf_pass"
        }

        generator {
            name = "org.jooq.codegen.KotlinGenerator"

            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "eaf"
                includes = ".*_view" // Only projection tables
            }

            target {
                packageName = "com.axians.eaf.framework.persistence.jooq"
                directory = "build/generated-src/jooq/main"
            }
        }
    }
}
