plugins {
    id("eaf.workflow")
    id("eaf.testing")
}

description = "EAF Workflow Framework - Flowable BPMN integration"

// Story 6.2: Create dedicated test source set for Axon integration tests
// (avoids Kotest conflict with multiple @SpringBootTest in same module)
val axonIntegrationTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += output + compileClasspath
    java.srcDirs("src/axon-integration-test/kotlin")
    resources.srcDirs("src/axon-integration-test/resources")
}

configurations {
    named("axonIntegrationTestImplementation") {
        extendsFrom(configurations.getByName("testImplementation"))
    }
    named("axonIntegrationTestRuntimeOnly") {
        extendsFrom(configurations.getByName("testRuntimeOnly"))
    }
}

// Create axonIntegrationTest task
val axonIntegrationTestTask =
    tasks.register<Test>("axonIntegrationTest") {
        description = "Runs Axon integration tests (Story 6.2+)"
        group = "verification"
        testClassesDirs = axonIntegrationTest.output.classesDirs
        classpath = axonIntegrationTest.runtimeClasspath
        useJUnitPlatform()

        shouldRunAfter(tasks.named("integrationTest"))
    }

// Add to check task
tasks.named("check") {
    dependsOn(axonIntegrationTestTask)
}

dependencies {
    implementation(project(":framework:core"))
    implementation(libs.bundles.axon.framework) // Story 6.2: Axon CommandGateway
    implementation(libs.arrow.core) // Story 6.2: Arrow Either for error handling
    implementation(project(":framework:security")) // Story 6.2: TenantContext
    // Note: framework:observability NOT added - causes test conflicts with @Configuration classes
    // FlowableMetrics only needs Micrometer (added below)
    implementation(project(":shared:shared-api")) // Story 6.2: Command types

    // Story 6.4: Micrometer for FlowableMetrics
    implementation(libs.micrometer.core)
    implementation(libs.spring.boot.starter.actuator) // Includes Prometheus support

    // Story 6.4 Remediation: Jakarta Validation for @ConfigurationProperties validation
    implementation(libs.spring.boot.starter.validation) // Provides jakarta.validation annotations (@Min, @Validated, etc.)

    // Story 6.4: Ansible execution via SSH (Task 4)
    implementation(libs.jsch) // Java SSH client
    // Note: Jackson provided transitively by spring-boot-starter-web

    testImplementation(libs.bundles.kotest)

    // Integration test dependencies (Story 6.1 - Flowable only)
    integrationTestImplementation(libs.spring.boot.starter.test)
    integrationTestImplementation(project(":shared:testing"))
    integrationTestImplementation(libs.postgresql)
    integrationTestImplementation(libs.testcontainers.postgresql)
    integrationTestImplementation(libs.kotest.runner.junit5.jvm) // Required for custom source sets

    // Story 6.2: Axon integration test dependencies (separate source set)
    // Framework tests use framework-local test types (TestEntity) - NO products dependency
    "axonIntegrationTestImplementation"(libs.spring.boot.starter.test)
    "axonIntegrationTestImplementation"(libs.spring.boot.starter.data.jpa) // Needed for framework dependencies (not for projections)
    "axonIntegrationTestImplementation"(libs.micrometer.core) // For SimpleMeterRegistry in TenantContext
    "axonIntegrationTestImplementation"(project(":shared:testing"))
    "axonIntegrationTestImplementation"(project(":framework:observability")) // Story 6.3: For CustomMetrics (tenant propagation)
    "axonIntegrationTestImplementation"(libs.postgresql)
    "axonIntegrationTestImplementation"(libs.testcontainers.postgresql)
    "axonIntegrationTestImplementation"(libs.kotest.runner.junit5.jvm)
    // Note: framework:observability in test scope only (provides CustomMetrics for AxonIntegrationTestConfig)
}
