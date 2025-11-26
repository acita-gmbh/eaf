plugins {
    id("eaf.kotlin-conventions")
    id("eaf.spring-conventions")
    id("eaf.test-conventions")
}

// eaf-auth-keycloak: Keycloak-specific implementation of IdentityProvider
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
