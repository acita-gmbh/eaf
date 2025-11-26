plugins {
    id("eaf.kotlin-conventions")
    id("eaf.spring-conventions")
    id("eaf.test-conventions")
}

// eaf-auth-keycloak: Keycloak-specific implementation of IdentityProvider
// This is a library module, not an executable application
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}

dependencies {
    api(project(":eaf:eaf-auth"))

    implementation(libs.spring.boot.oauth2.resource.server)
    implementation(libs.spring.boot.webflux)
    implementation(libs.kotlin.coroutines.reactor)
    implementation(libs.reactor.kotlin.extensions)
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.jsr310)

    testImplementation(libs.spring.boot.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.reactor.test)
}
