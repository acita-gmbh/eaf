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
}
