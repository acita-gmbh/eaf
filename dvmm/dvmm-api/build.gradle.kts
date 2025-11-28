plugins {
    id("eaf.spring-conventions")
    id("eaf.logging-conventions")
    id("eaf.test-conventions")
}

// dvmm-api: REST controllers, DTOs, API documentation
// This is a library module, not an executable application
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}

dependencies {
    implementation(project(":dvmm:dvmm-application"))
    implementation(project(":eaf:eaf-auth"))
    implementation(project(":eaf:eaf-auth-keycloak"))
    implementation(project(":eaf:eaf-tenant"))

    implementation(libs.spring.boot.oauth2.resource.server)

    testImplementation(libs.spring.security.test)
    testImplementation(libs.spring.boot.actuator)
    testImplementation(project(":eaf:eaf-testing"))
}

// Coverage and mutation testing quality gates restored in Story 2.1
