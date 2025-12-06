plugins {
    id("eaf.kotlin-conventions")
    id("org.jetbrains.kotlinx.kover")
}

// Access version catalog
val libs = versionCatalogs.named("libs")

// Kover configuration for code coverage
// Per-module verification disabled - global verification in root build.gradle.kts
kover {
    reports {
        total {
            html {
                onCheck = false // Don't run on every check
            }
            xml {
                onCheck = false
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()

    // JUnit 6 native Kotlin suspend support
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }

    // Fail on warnings
    systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
}

dependencies {
    // JUnit 6 BOM for consistent versioning
    testImplementation(platform("org.junit:junit-bom:${libs.findVersion("junit").get()}"))

    // JUnit 6 (unified versioning)
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.platform:junit-platform-engine")

    // MockK for Kotlin mocking
    testImplementation("io.mockk:mockk:${libs.findVersion("mockk").get()}")

    // Testcontainers 2.x
    testImplementation(platform("org.testcontainers:testcontainers-bom:${libs.findVersion("testcontainers").get()}"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")

    // Konsist for architecture testing
    testImplementation("com.lemonappdev:konsist:${libs.findVersion("konsist").get()}")

    // Pitest mutation testing (JUnit 5 plugin)
    testImplementation("org.pitest:pitest-junit5-plugin:${libs.findVersion("pitest-junit5-plugin").get()}")
}
