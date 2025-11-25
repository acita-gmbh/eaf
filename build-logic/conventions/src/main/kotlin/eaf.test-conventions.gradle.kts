plugins {
    id("eaf.kotlin-conventions")
    jacoco
}

// JaCoCo configuration for code coverage
jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal() // 80% minimum coverage
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
    testImplementation(platform("org.junit:junit-bom:6.0.1"))

    // JUnit 6 (unified versioning)
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.platform:junit-platform-engine")

    // MockK for Kotlin mocking
    testImplementation("io.mockk:mockk:1.14.6")

    // Testcontainers 2.x (with new module naming)
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.2"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")

    // Konsist for architecture testing
    testImplementation("com.lemonappdev:konsist:0.17.3")
}
