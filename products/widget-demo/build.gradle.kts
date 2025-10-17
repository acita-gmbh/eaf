plugins {
    id("eaf.testing") // Apply FIRST (Kotest setup)
    id("eaf.spring-boot") // Apply AFTER Kotest
    id("eaf.quality-gates") // Apply LAST
    alias(libs.plugins.jooq.codegen)
}

description = "EAF Widget Demo - Reference implementation and E2E testing application"

dependencies {
    // Framework modules
    implementation(project(":framework:core"))
    implementation(project(":framework:cqrs"))
    implementation(project(":framework:security"))
    implementation(project(":framework:persistence"))
    implementation(project(":framework:web"))
    implementation(project(":shared:shared-api"))

    // Domain dependencies (now that Widget domain is local)
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.axon.framework)
    implementation(libs.spring.modulith.starter.core)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.springframework.boot:spring-boot-starter-jooq")

    // Spring Boot starters (via convention plugin)
    // Auto-configured: web, security, oauth2-resource-server, actuator, validation

    // Database
    implementation(libs.bundles.database)
    implementation(libs.bundles.jooq)
    runtimeOnly(libs.postgresql)

    jooqCodegen(libs.jooq.codegen)
    jooqCodegen(libs.jooq.meta.extensions)

    // Testing
    testImplementation(project(":shared:testing"))
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.axon.test)
    testImplementation("org.hamcrest:hamcrest:3.0")
    testImplementation(libs.bundles.testcontainers)
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // PHASE 2 FIX: Explicit Kotest dependencies for integrationTest to override Spring Boot BOM
    integrationTestImplementation("io.kotest:kotest-runner-junit5:6.0.3")
    integrationTestImplementation("io.kotest:kotest-assertions-core:6.0.3")
    integrationTestImplementation("io.kotest:kotest-property:6.0.3")
    integrationTestImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
    integrationTestImplementation("org.springframework.boot:spring-boot-starter-test")
    integrationTestImplementation("org.springframework.security:spring-security-test")
    integrationTestImplementation(project(":framework:observability"))
    integrationTestImplementation("org.springframework.data:spring-data-redis")
    integrationTestImplementation(libs.jakarta.json.bind.api)
    implementation(libs.jackson.module.kotlin)

    // Story 8.3: OpenTelemetry causes ClassNotFoundException, exclude it for integration tests
    // integrationTestImplementation(libs.bundles.opentelemetry)
}

jooq {
    configuration {
        logging = org.jooq.meta.jaxb.Logging.WARN
        generator {
            name = "org.jooq.codegen.KotlinGenerator"
            database {
                name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                properties.addAll(
                    listOf(
                        org.jooq.meta.jaxb.Property().apply {
                            key = "scripts"
                            value = "${project.projectDir}/src/main/resources/db/jooq/widget_projection.ddl"
                        },
                        org.jooq.meta.jaxb.Property().apply {
                            key = "sort"
                            value = "semantic"
                        },
                        org.jooq.meta.jaxb.Property().apply {
                            key = "defaultNameCase"
                            value = "LOWER"
                        },
                    ),
                )
            }
            generate {
                isFluentSetters = false
                isImmutablePojos = true
                isPojos = true
                isRecords = true
            }
            target {
                packageName = "com.axians.eaf.products.widgetdemo.jooq"
                directory =
                    layout.buildDirectory
                        .dir("generated-src/jooq/main")
                        .get()
                        .asFile.path
            }
        }
    }
}

val jooqOutputDir = layout.buildDirectory.dir("generated-src/jooq/main")

ktlint {
    filter {
        exclude { entry -> entry.file.toPath().startsWith(jooqOutputDir.get().asFile.toPath()) }
    }
}

sourceSets {
    named("main") {
        kotlin.srcDir(jooqOutputDir.get().asFile)
    }
    named("test") {
        kotlin.srcDir(jooqOutputDir.get().asFile)
    }
    named("integrationTest") {
        kotlin.srcDir(jooqOutputDir.get().asFile)
    }
    named("perfTest") {
        kotlin.srcDir(jooqOutputDir.get().asFile)
    }
}

tasks.named("compileKotlin") {
    dependsOn("jooqCodegen")
    (this as org.jetbrains.kotlin.gradle.tasks.KotlinCompile).source(jooqOutputDir.get().asFile)
}

tasks.named("compileTestKotlin") {
    dependsOn("jooqCodegen")
    (this as org.jetbrains.kotlin.gradle.tasks.KotlinCompile).source(jooqOutputDir.get().asFile)
}

tasks.named("runKtlintCheckOverMainSourceSet") {
    dependsOn("jooqCodegen")
}

tasks.named("runKtlintCheckOverTestSourceSet") {
    dependsOn("jooqCodegen")
}

tasks.named("runKtlintCheckOverIntegrationTestSourceSet") {
    dependsOn("jooqCodegen")
}

tasks.named("runKtlintCheckOverPerfTestSourceSet") {
    dependsOn("jooqCodegen")
}

// Story 8.3: Use Spring Boot BOM versions for OpenTelemetry compatibility
// Removed force rules to use Spring Boot managed versions

// Suppress AxonIQ Console marketing message
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("disable-axoniq-console-message", "true")
}

// Suppress JPA open-in-view warning (we use jOOQ for queries, not JPA lazy loading)
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("spring.jpa.open-in-view", "false")
}

// Skip quality gates for minimal reference implementation
// detekt: Version 1.23.8 incompatible with Kotlin 2.2.20 (compiled with 2.0.21)
//         Upgrade to detekt 2.0.0 when it reaches stable release
// pitest: No production code to mutation test (just @SpringBootApplication)
tasks.named("detekt") {
    enabled = false
}
tasks.named("pitest") {
    enabled = false
}
