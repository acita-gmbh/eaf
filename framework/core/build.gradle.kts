plugins {
    id("eaf.testing-v2") // PILOT: Testing v2 plugin (Story 2.1)
    id("eaf.kotlin-common")
}

description = "EAF Core Framework - Domain patterns and utilities"

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)

    // Spring Modulith for module boundary enforcement
    implementation(libs.spring.modulith.api)
    testImplementation(libs.spring.modulith.test)

    // Spring Boot for auto-configuration, dependency injection, and Jackson (ObjectMapper)
    // spring-boot-starter-web brings: spring-boot, spring-context, spring-web, jackson-databind
    implementation(libs.spring.boot.starter.web)

    // Spring Boot auto-configuration support (for @AutoConfiguration, @ConditionalOnClass, etc.)
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Jakarta annotations (for @PostConstruct, @PreDestroy, etc.)
    compileOnly("jakarta.annotation:jakarta.annotation-api")

    // Micrometer for metrics (resilience patterns)
    implementation(libs.micrometer.core)

    // Resilience patterns (OWASP A10:2025 - Exception Handling)
    api(libs.bundles.resilience4j)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.micrometer.core) // For SimpleMeterRegistry in tests
}
