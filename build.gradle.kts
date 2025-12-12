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

    // CVE-2024-47554: Force safe commons-io version across all modules
    // Vulnerable version 2.11.0 comes transitively from testcontainers-keycloak
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "commons-io" && requested.name == "commons-io") {
                useVersion("2.18.0")
                because("CVE-2024-47554: commons-io < 2.14.0 vulnerable to DoS via XmlStreamReader")
            }
        }
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
    // DCM modules
    kover(project(":dcm:dcm-domain"))
    kover(project(":dcm:dcm-application"))
    kover(project(":dcm:dcm-api"))
    kover(project(":dcm:dcm-infrastructure"))
    kover(project(":dcm:dcm-app"))
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
                // Temporary: dcm-api SecurityConfig (until Story 2.1)
                packages("de.acci.dcm.api.security.*")
                // Permanent: jOOQ generated code
                packages("de.acci.dcm.infrastructure.jooq.*")
                // Permanent: Spring Boot main() function
                classes("de.acci.dcm.DcmApplicationKt")
                // Permanent: VcenterAdapter - requires real vCenter (Story 3-9)
                // VCF SDK port 443 limitation prevents VCSIM testing
                classes("de.acci.dcm.infrastructure.vmware.VcenterAdapter")
            }
        }
        verify {
            rule("Global Coverage") {
                minBound(70) // Aligned with Pitest mutation score threshold
            }
        }
    }
}
