plugins {
    id("eaf.kotlin-common")
    id("eaf.observability")
    id("eaf.testing")
}

description = "EAF Security Framework - 10-layer JWT validation and tenant isolation"

dependencies {
    implementation(project(":framework:core"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.spring.boot.security)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.jose4j)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(project(":shared:testing"))

    // Integration test specific dependencies for security framework
    integrationTestImplementation("org.springframework.security:spring-security-test:6.5.5")
    integrationTestImplementation(libs.spring.boot.starter.test)
}
