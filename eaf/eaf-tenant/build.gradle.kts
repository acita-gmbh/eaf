plugins {
    id("eaf.kotlin-conventions")
    id("eaf.test-conventions")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))

    api(project(":eaf:eaf-core"))

    implementation(libs.spring.boot.webflux)
    implementation(libs.kotlin.coroutines.reactor)
    implementation(libs.reactor.kotlin.extensions)
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.jsr310)

    testImplementation(libs.spring.boot.test)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.reactor.test)
    testImplementation(kotlin("test"))
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.postgresql)
}
