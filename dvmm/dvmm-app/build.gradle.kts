plugins {
    id("eaf.spring-conventions")
    id("eaf.logging-conventions")
    id("eaf.test-conventions")
    id("eaf.pitest-conventions")
}

// dvmm-app: Main application, assembles all modules
dependencies {
    implementation(project(":dvmm:dvmm-api"))
    implementation(project(":dvmm:dvmm-infrastructure"))
    implementation(project(":eaf:eaf-auth-keycloak"))
    implementation(libs.spring.boot.actuator)

    testImplementation(testFixtures(project(":eaf:eaf-testing")))
    testImplementation(libs.spring.boot.oauth2.resource.server)
    testImplementation(libs.spring.security.test)
}

springBoot {
    mainClass.set("de.acci.dvmm.DvmmApplicationKt")
}

// =============================================================================
// KOVER CONFIGURATION - Exclude Spring Boot Main Function from Coverage
// =============================================================================
// The main() function in DvmmApplicationKt cannot be meaningfully tested
// in JUnit because it starts a new application instance. This is standard
// practice for Spring Boot applications where the main function is just
// a bootstrap wrapper that delegates to Spring's runApplication().
// =============================================================================
kover {
    reports {
        filters {
            excludes {
                // Exclude the main() function (Kotlin generates a *Kt class for top-level functions)
                classes("de.acci.dvmm.DvmmApplicationKt")
            }
        }
    }
}

// =============================================================================
// PITEST CONFIGURATION - Disable Mutation Testing for Bootstrap Module
// =============================================================================
// dvmm-app only contains the Spring Boot main() function which is untestable
// bootstrap code. There's no meaningful code to mutation test.
// Pitest fails with 0% score when all classes are excluded or have no mutations.
// =============================================================================
tasks.named("pitest") {
    enabled = false
}
