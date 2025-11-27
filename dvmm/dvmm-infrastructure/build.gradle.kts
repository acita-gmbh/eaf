plugins {
    id("eaf.spring-conventions")
    id("eaf.test-conventions")
    alias(libs.plugins.jooq.codegen)
}

// dvmm-infrastructure: Adapters, Projections, External integrations
// This is a library module, not an executable application
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}

dependencies {
    implementation(project(":dvmm:dvmm-application"))
    implementation(project(":eaf:eaf-tenant"))
    implementation(project(":eaf:eaf-eventsourcing"))

    // jOOQ for type-safe SQL
    implementation(libs.jooq)
    implementation(libs.jooq.kotlin)

    // jOOQ code generation dependencies
    jooqGenerator(libs.jooq)
    jooqGenerator(libs.jooq.codegen)
    jooqGenerator(libs.jooq.meta)
    jooqGenerator(libs.postgresql)
    jooqGenerator(libs.testcontainers.core)
    jooqGenerator(libs.testcontainers.postgresql)
    jooqGenerator(libs.slf4j.simple)

    // Test dependencies
    testImplementation(project(":eaf:eaf-testing"))
    testImplementation(libs.postgresql)
    testImplementation(libs.kotlin.coroutines.test)
    testRuntimeOnly(libs.postgresql)
}

// Disable explicit API mode for this module due to jOOQ generated code
// Generated code doesn't have visibility modifiers required by explicitApi()
kotlin {
    explicitApi = org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode.Warning
}

// jOOQ code generation configuration
// NOTE: Requires PostgreSQL container running with schema initialized
// Run: docker run -d --name dvmm-jooq-db -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test -e POSTGRES_DB=dvmm_test -p 5432:5432 postgres:16-alpine
// Then: psql -h localhost -U test -d dvmm_test -f src/main/resources/db/jooq-init.sql
jooq {
    version.set("3.20.8")

    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)

            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN

                jdbc.apply {
                    // Direct PostgreSQL connection - container must be running with schema initialized
                    // Alternative: Use Testcontainers JDBC URL when Docker/Testcontainers compatibility is resolved
                    // jdbc:tc:postgresql:15:///dvmm?TC_INITSCRIPT=file:src/main/resources/db/jooq-init.sql
                    driver = "org.postgresql.Driver"
                    url = System.getenv("JOOQ_DB_URL") ?: "jdbc:postgresql://localhost:5432/dvmm_test"
                    user = System.getenv("JOOQ_DB_USER") ?: "test"
                    password = System.getenv("JOOQ_DB_PASSWORD") ?: "test"
                }

                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"

                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        includes = ".*"
                        excludes = "flyway_schema_history"

                        // Include both public and eaf_events schemas for event store tables
                        // Note: schemata overrides inputSchema, so we define all schemas here
                        schemata.addAll(
                            listOf(
                                org.jooq.meta.jaxb.SchemaMappingType().withInputSchema("public"),
                                org.jooq.meta.jaxb.SchemaMappingType().withInputSchema("eaf_events")
                            )
                        )
                    }

                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isPojos = true
                        isImmutablePojos = true
                        isFluentSetters = true
                        isKotlinNotNullRecordAttributes = true
                        isKotlinNotNullPojoAttributes = true
                        isKotlinNotNullInterfaceAttributes = true
                    }

                    target.apply {
                        packageName = "de.acci.dvmm.infrastructure.jooq"
                        directory = "${layout.buildDirectory.get()}/generated-sources/jooq"
                    }
                }
            }
        }
    }
}
