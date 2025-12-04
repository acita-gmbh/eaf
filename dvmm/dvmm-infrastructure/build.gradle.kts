plugins {
    id("eaf.spring-conventions")
    id("eaf.logging-conventions")
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
    implementation(project(":eaf:eaf-notifications"))

    // jOOQ for type-safe SQL
    implementation(libs.jooq)
    implementation(libs.jooq.kotlin)

    // Spring Security Crypto for AES-256 encryption (AC-3.1.4)
    implementation(libs.spring.security.crypto)

    // VMware vSphere SDK (yavijava - maintained fork of vijava)
    implementation(libs.yavijava)

    // jOOQ code generation dependencies
    // jooq-meta-extensions is required for DDLDatabase (generates code from DDL files without running DB)
    jooqGenerator(libs.jooq)
    jooqGenerator(libs.jooq.codegen)
    jooqGenerator(libs.jooq.meta)
    jooqGenerator(libs.jooq.meta.extensions)
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
// Uses DDLDatabase to generate code from SQL DDL files without requiring a running database.
// This enables builds without Docker/PostgreSQL and works in any CI environment.
// See: https://www.jooq.org/doc/latest/manual/code-generation/codegen-ddl/
//
// The jooq-init.sql file uses [jooq ignore start/stop] tokens to skip PostgreSQL-specific
// statements (RLS, grants, triggers) that H2 (used internally by DDLDatabase) doesn't support.
jooq {
    version.set("3.20.8")

    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)

            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN

                // No JDBC connection needed - DDLDatabase parses DDL files directly
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"

                    database.apply {
                        // DDLDatabase generates code from SQL DDL scripts without a live database
                        // Located in jooq-meta-extensions module
                        name = "org.jooq.meta.extensions.ddl.DDLDatabase"

                        // Path to the combined DDL script with all schema definitions
                        properties.add(
                            org.jooq.meta.jaxb.Property()
                                .withKey("scripts")
                                .withValue("src/main/resources/db/jooq-init.sql")
                        )

                        // Parse comments to enable [jooq ignore start/stop] tokens
                        properties.add(
                            org.jooq.meta.jaxb.Property()
                                .withKey("parseIgnoreComments")
                                .withValue("true")
                        )

                        includes = ".*"
                        excludes = "flyway_schema_history"

                        // Include both public and eaf_events schemas for event store tables
                        // H2 uses uppercase schema names, but we need lowercase for Kotlin package names
                        schemata.addAll(
                            listOf(
                                org.jooq.meta.jaxb.SchemaMappingType()
                                    .withInputSchema("PUBLIC")
                                    .withOutputSchema("public"),
                                org.jooq.meta.jaxb.SchemaMappingType()
                                    .withInputSchema("EAF_EVENTS")
                                    .withOutputSchema("eaf_events")
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

// =============================================================================
// KOVER CONFIGURATION - Exclude jOOQ Generated Code from Coverage
// =============================================================================
// jOOQ generates code in de.acci.dvmm.infrastructure.jooq package
// This generated code should not count toward coverage metrics
// =============================================================================
kover {
    reports {
        filters {
            excludes {
                // Exclude all jOOQ generated code from coverage analysis
                packages("de.acci.dvmm.infrastructure.jooq.*")
            }
        }
    }
}
