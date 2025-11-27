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
        verify {
            rule("Global Coverage") {
                minBound(80)
            }
        }
    }
}
