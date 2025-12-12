// Buildscript dependencies for custom jOOQ generation task (runs in Gradle's context)
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Testcontainers for spinning up PostgreSQL
        classpath("org.testcontainers:postgresql:1.21.3")
        // Flyway for running migrations
        classpath("org.flywaydb:flyway-core:11.16.0")
        classpath("org.flywaydb:flyway-database-postgresql:11.16.0")
        // PostgreSQL driver
        classpath("org.postgresql:postgresql:42.7.8")
        // jOOQ codegen
        classpath("org.jooq:jooq-codegen:3.20.9")
        classpath("org.jooq:jooq-meta:3.20.9")
    }
}

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

    // VCF SDK 9.0 (Official VMware SDK from Maven Central - Apache 2.0 license)
    // Uses BOM for version management, provides vim25 (SOAP) + vsphere-utils (convenience wrappers)
    // JAX-WS (jakarta.xml.ws-api) is provided transitively via CXF
    implementation(platform(libs.vcf.sdk.bom))
    implementation(libs.vcf.sdk.vsphere.utils)  // Includes vim25 transitively

    // Resilience
    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.kotlin)

    // jOOQ code generation dependencies
    // Uses Testcontainers to spin up PostgreSQL + Flyway migrations for real schema generation
    jooqGenerator(libs.jooq)
    jooqGenerator(libs.jooq.codegen)
    jooqGenerator(libs.jooq.meta)
    jooqGenerator(libs.postgresql)
    jooqGenerator(libs.testcontainers.postgresql)
    jooqGenerator(libs.flyway.core)
    jooqGenerator(libs.flyway.database.postgresql)
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

// =============================================================================
// jOOQ CODE GENERATION CONFIGURATION (Option 2: Testcontainers + Flyway)
// =============================================================================
// Uses Testcontainers to spin up a real PostgreSQL database, runs Flyway migrations
// against it, then generates jOOQ code from the actual production-identical schema.
//
// Benefits:
// - Single source of truth: Flyway migrations ARE the schema definition
// - No jooq-init.sql to maintain - eliminates synchronization issues
// - Full PostgreSQL compatibility - RLS, triggers, functions all work
// - Catches migration errors at build time
//
// Requirements:
// - Docker must be running (locally and on CI)
// - Build time increases ~10-15s for container startup
//
// See: https://www.jooq.org/doc/latest/manual/code-generation/codegen-jooq-database/
// =============================================================================

// Resolve paths as plain strings at configuration time (configuration-cache compatible)
// These are resolved immediately to avoid capturing Project references
val eafMigrationsPath: String = rootProject.projectDir.resolve("eaf/eaf-eventsourcing/src/main/resources/db/migration").absolutePath
val dvmmMigrationsPath: String = projectDir.resolve("src/main/resources/db/migration").absolutePath
val jooqOutputDir: String = layout.buildDirectory.dir("generated-sources/jooq").get().asFile.absolutePath

// Custom task that:
// 1. Starts a PostgreSQL Testcontainer
// 2. Runs Flyway migrations from both EAF and DVMM
// 3. Generates jOOQ code from the migrated schema
// 4. Stops the container
val generateJooqWithTestcontainers by tasks.registering {
    group = "jooq"
    description = "Generate jOOQ code using Testcontainers PostgreSQL + Flyway migrations"

    // Track migration files as inputs for up-to-date checking
    inputs.files(
        fileTree(eafMigrationsPath) { include("*.sql") },
        fileTree(dvmmMigrationsPath) { include("*.sql") }
    )
    outputs.dir(jooqOutputDir)

    // Use @Input properties to pass configuration-cache-compatible values to doLast
    val eafPath = eafMigrationsPath
    val dvmmPath = dvmmMigrationsPath
    val outputDir = jooqOutputDir

    doLast {
        // Start PostgreSQL container (uses Testcontainers default random credentials)
        val postgres = org.testcontainers.containers.PostgreSQLContainer("postgres:16")
            .withDatabaseName("jooq_codegen")
            .withTmpFs(mapOf("/var/lib/postgresql/data" to "rw"))  // Faster I/O

        postgres.start()
        println("PostgreSQL container started: ${postgres.jdbcUrl}")

        try {
            // Run Flyway migrations from both locations
            val flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations(
                    "filesystem:$eafPath",
                    "filesystem:$dvmmPath"
                )
                .load()

            val result = flyway.migrate()
            println("Flyway applied ${result.migrationsExecuted} migrations")

            // Generate jOOQ code from the migrated database
            val jooqConfig = org.jooq.meta.jaxb.Configuration()
                .withLogging(org.jooq.meta.jaxb.Logging.WARN)
                .withJdbc(
                    org.jooq.meta.jaxb.Jdbc()
                        .withDriver("org.postgresql.Driver")
                        .withUrl(postgres.jdbcUrl)
                        .withUser(postgres.username)
                        .withPassword(postgres.password)
                )
                .withGenerator(
                    org.jooq.meta.jaxb.Generator()
                        .withName("org.jooq.codegen.KotlinGenerator")
                        .withDatabase(
                            org.jooq.meta.jaxb.Database()
                                .withName("org.jooq.meta.postgres.PostgresDatabase")
                                .withIncludes(".*")
                                .withExcludes("flyway_schema_history")
                                .withSchemata(
                                    org.jooq.meta.jaxb.SchemaMappingType()
                                        .withInputSchema("public")
                                        .withOutputSchema("public"),
                                    org.jooq.meta.jaxb.SchemaMappingType()
                                        .withInputSchema("eaf_events")
                                        .withOutputSchema("eaf_events")
                                )
                        )
                        .withGenerate(
                            org.jooq.meta.jaxb.Generate()
                                .withDeprecated(false)
                                .withRecords(true)
                                .withPojos(true)
                                .withImmutablePojos(true)
                                .withFluentSetters(true)
                                .withKotlinNotNullRecordAttributes(true)
                                .withKotlinNotNullPojoAttributes(true)
                                .withKotlinNotNullInterfaceAttributes(true)
                        )
                        .withTarget(
                            org.jooq.meta.jaxb.Target()
                                .withPackageName("de.acci.dvmm.infrastructure.jooq")
                                .withDirectory(outputDir)
                        )
                )

            org.jooq.codegen.GenerationTool.generate(jooqConfig)
            println("jOOQ code generation complete")

        } finally {
            postgres.stop()
            println("PostgreSQL container stopped")
        }
    }
}

// Ensure compileKotlin depends on our custom jOOQ generation
tasks.named("compileKotlin") {
    dependsOn(generateJooqWithTestcontainers)
}

// Add generated sources to the main source set
sourceSets {
    main {
        kotlin {
            srcDir(jooqOutputDir)
        }
    }
}

// =============================================================================
// KOVER CONFIGURATION - Include Only Unit-Testable Classes
// =============================================================================
// Most infrastructure code requires external systems (PostgreSQL, vCenter, SMTP)
// and is tested via integration tests in dvmm-app with Testcontainers.
// Only include classes that can be unit tested in isolation.
// =============================================================================
kover {
    currentProject {
        instrumentation {
            // Include ONLY classes that can be unit tested without external dependencies
            // All adapters/repositories are tested via integration tests in dvmm-app
            includedClasses.addAll(
                "de.acci.dvmm.infrastructure.vmware.SpringSecurityCredentialEncryptor",
                "de.acci.dvmm.infrastructure.vmware.SpringSecurityCredentialEncryptor\$*"
            )
        }
    }
    reports {
        filters {
            includes {
                // Mirror instrumentation includes for consistent reporting
                classes(
                    "de.acci.dvmm.infrastructure.vmware.SpringSecurityCredentialEncryptor",
                    "de.acci.dvmm.infrastructure.vmware.SpringSecurityCredentialEncryptor\$*"
                )
            }
        }
    }
}
