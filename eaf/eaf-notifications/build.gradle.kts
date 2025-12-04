plugins {
    id("eaf.kotlin-conventions")
    id("eaf.spring-conventions")
    id("eaf.test-conventions")
    id("eaf.pitest-conventions")
}

// This is a library module, not a Spring Boot application
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}

// eaf-notifications: Notification service abstraction with SMTP implementation
// Provides interfaces for sending notifications and Thymeleaf-based email templating
dependencies {
    api(project(":eaf:eaf-core"))
    api(project(":eaf:eaf-tenant"))

    // Spring Mail and Thymeleaf for email templating
    implementation(libs.spring.boot.mail)
    implementation(libs.spring.boot.thymeleaf)

    // Coroutines for async email sending
    implementation(libs.kotlin.coroutines.core)

    // Logging
    implementation(libs.kotlin.logging)

    // Test dependencies
    testImplementation(libs.kotlin.coroutines.test)
}

pitest {
    targetClasses.set(listOf("de.acci.eaf.notifications.*"))
}
