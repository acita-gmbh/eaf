plugins {
    id("eaf.spring-conventions")
    id("eaf.logging-conventions")
    id("eaf.test-conventions")
    id("eaf.pitest-conventions")
}

// dcm-app: Main application, assembles all modules
dependencies {
    implementation(project(":dcm:dcm-api"))
    implementation(project(":dcm:dcm-application"))
    implementation(project(":dcm:dcm-infrastructure"))
    implementation(project(":eaf:eaf-auth-keycloak"))
    implementation(project(":eaf:eaf-eventsourcing"))
    implementation(project(":eaf:eaf-tenant"))
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.jdbc)

    // jOOQ for type-safe SQL
    implementation(libs.jooq)
    implementation(libs.jooq.kotlin)

    // PostgreSQL JDBC driver for DataSource
    runtimeOnly(libs.postgresql)

    testImplementation(testFixtures(project(":eaf:eaf-testing")))
    testImplementation(libs.spring.boot.oauth2.resource.server)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.kotlin.coroutines.test)
}

springBoot {
    mainClass.set("de.acci.dcm.DcmApplicationKt")
}

// Disable plain JAR - only bootJar is needed for deployment
// This prevents confusion when copying JARs in Docker builds
tasks.named<Jar>("jar") {
    enabled = false
}

// =============================================================================
// KOVER CONFIGURATION - Exclude Spring Boot Main Function from Coverage
// =============================================================================
// The main() function in DcmApplicationKt cannot be meaningfully tested
// in JUnit because it starts a new application instance. This is standard
// practice for Spring Boot applications where the main function is just
// a bootstrap wrapper that delegates to Spring's runApplication().
// =============================================================================
kover {
    reports {
        filters {
            excludes {
                // Exclude the main() function (Kotlin generates a *Kt class for top-level functions)
                classes("de.acci.dcm.DcmApplicationKt")
            }
        }
    }
}

// =============================================================================
// PITEST CONFIGURATION - Disable Mutation Testing for Bootstrap Module
// =============================================================================
// dcm-app's main() function is untestable bootstrap code.
// The DcmApplication class itself is tested via DcmApplicationTest.kt.
// Pitest fails with 0% score when the only remaining code has no testable mutations.
// =============================================================================
tasks.named("pitest") {
    enabled = false
}
