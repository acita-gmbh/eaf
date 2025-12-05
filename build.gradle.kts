plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.kover)
}

allprojects {
    group = "de.acci"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    repositories {
        mavenCentral()
    }
}

// Kover merged reports - aggregate all subprojects
dependencies {
    // EAF modules
    kover(project(":eaf:eaf-core"))
    kover(project(":eaf:eaf-eventsourcing"))
    kover(project(":eaf:eaf-tenant"))
    kover(project(":eaf:eaf-auth"))
    kover(project(":eaf:eaf-auth-keycloak"))
    kover(project(":eaf:eaf-testing"))
    // DVMM modules
    kover(project(":dvmm:dvmm-domain"))
    kover(project(":dvmm:dvmm-application"))
    kover(project(":dvmm:dvmm-api"))
    kover(project(":dvmm:dvmm-infrastructure"))
    kover(project(":dvmm:dvmm-app"))
}

kover {
    reports {
        total {
            html {
                title = "EAF Monorepo Coverage"
                htmlDir = layout.buildDirectory.dir("reports/kover/html")
            }
            xml {
                xmlFile = layout.buildDirectory.file("reports/kover/report.xml")
            }
        }
        // ==========================================================================
        // Global Exclusions for Merged Coverage Report
        // ==========================================================================
        // These exclusions match the per-module exclusions. They are needed here
        // because the merged report aggregates all code, including excluded modules.
        //
        // Temporary exclusions (to be removed in Story 2.1):
        // - eaf-auth-keycloak: Requires Keycloak Testcontainer
        // - dvmm-api (SecurityConfig): Requires Spring Security integration tests
        //
        // Permanent exclusions:
        // - jOOQ generated code
        // - Spring Boot main() function (untestable bootstrap code)
        // ==========================================================================
        filters {
            excludes {
                // Temporary: eaf-auth-keycloak (until Story 2.1)
                packages("de.acci.eaf.auth.keycloak.*")
                // Temporary: dvmm-api SecurityConfig (until Story 2.1)
                packages("de.acci.dvmm.api.security.*")
                // Permanent: jOOQ generated code
                packages("de.acci.dvmm.infrastructure.jooq.*")
                // Permanent: Spring Boot main() function
                classes("de.acci.dvmm.DvmmApplicationKt")
                // Permanent: VcenterAdapter - requires real vCenter (Story 3-9)
                // VCF SDK port 443 limitation prevents VCSIM testing
                classes("de.acci.dvmm.infrastructure.vmware.VcenterAdapter")
            }
        }
        verify {
            rule("Global Coverage") {
                minBound(70) // Aligned with Pitest mutation score threshold
            }
        }
    }
}
