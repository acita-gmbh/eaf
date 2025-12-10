plugins {
    id("eaf.kotlin-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("plugin.spring")
}

// Access version catalog
val libs = versionCatalogs.named("libs")

dependencies {
    // Spring WebFlux reactive stack
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Reactor Kotlin extensions
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:${libs.findVersion("reactor-kotlin").get()}")

    // Kotlin reflection for Spring
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Jackson Kotlin module
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("io.projectreactor:reactor-test")

    // Flyway 10+ split database logic (needed for Postgres 16+)
    // We force this for all Spring Boot modules to ensure compatibility if they use Flyway
    // It's safe to add as implementation or runtimeOnly, but implementation ensures visibility
    implementation("org.flywaydb:flyway-database-postgresql")
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.flywaydb" && requested.name == "flyway-core") {
            // Force version from catalog to override Spring Boot managed dependency
            // which downgrades Flyway to 11.7.2 (incompatible with Postgres 16.11)
            useVersion(libs.findVersion("flyway").get().toString())
            because("PostgreSQL 16.11 support requires Flyway 11.10+")
        }
    }
}
