plugins {
    id("eaf.spring-conventions")
    id("eaf.test-conventions")
}

// dvmm-infrastructure: Adapters, Projections, External integrations
// This is a library module, not an executable application
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}

dependencies {
    implementation(project(":dvmm:dvmm-application"))
    implementation(project(":eaf:eaf-tenant"))

    // jOOQ for type-safe SQL
    implementation(libs.jooq)
    implementation(libs.jooq.kotlin)
}
