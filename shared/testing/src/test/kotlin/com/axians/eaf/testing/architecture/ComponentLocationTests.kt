package com.axians.eaf.testing.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withName
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.FunSpec

/**
 * Architecture compliance tests that validate component file locations
 * match the specifications defined in docs/architecture/component-specifications.md
 *
 * These tests prevent inferred file paths and ensure documentation ecosystem integrity.
 */
class ComponentLocationTests :
    FunSpec({

        context("Component Location Validation") {
            test("4.5-UNIT-001: AxonConfiguration should be in correct location when implemented") {
                val axonConfigFiles =
                    Konsist
                        .scopeFromProject()
                        .files
                        .withName("AxonConfiguration")
                        .filter { it.path.contains("/src/main/kotlin/") } // Exclude build artifacts

                // AxonConfiguration can be in TWO valid locations:
                // 1. Framework-level: framework/cqrs/config/ (auto-configuration for cross-cutting concerns)
                // 2. Product-level: products/*/config/ (application-specific configuration with DataSource)
                if (axonConfigFiles.isNotEmpty()) {
                    axonConfigFiles.assertTrue {
                        // Allow framework-level autoconfigure (cross-cutting concerns like tenant propagation)
                        val isFrameworkAutoConfigure =
                            it.path.contains("framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/config/")

                        // Allow product-level config (application-specific setup with DataSource)
                        val isProductConfig = it.path.contains("products/") && it.path.contains("/config/")

                        isFrameworkAutoConfigure || isProductConfig
                    }
                }
            }

            test("4.5-UNIT-002: Product controllers should NOT be in framework modules") {
                // Story 6.3: Framework modules contain ONLY infrastructure (no product-specific code)
                // Product controllers belong in products/* modules
                val controllerFiles =
                    Konsist
                        .scopeFromProject()
                        .files
                        .filter {
                            it.name.endsWith("Controller") &&
                                it.path.contains("/controllers/") &&
                                it.path.contains("/src/main/kotlin/") // Exclude build artifacts (bin/, build/)
                        }

                if (controllerFiles.isNotEmpty()) {
                    controllerFiles.assertTrue {
                        // Controllers in framework must be generic infrastructure only
                        // Product-specific controllers must be in products/* modules
                        val isInFramework = it.path.contains("/framework/") && !it.path.contains("/products/")
                        val isAllowedInfrastructureController =
                            it.name == "HealthController" || // Health check endpoint
                                it.name == "MetricsController" || // Metrics endpoint
                                it.name == "SecureController" // JWT auth demonstration

                        !isInFramework || isAllowedInfrastructureController
                    }
                }
            }

            test("4.5-UNIT-003: GlobalExceptionHandler should be in correct location when implemented") {
                val exceptionHandlerFiles =
                    Konsist
                        .scopeFromProject()
                        .files
                        .withName("GlobalExceptionHandler")
                        .filter { it.path.contains("/src/main/kotlin/") } // Exclude build artifacts

                if (exceptionHandlerFiles.isNotEmpty()) {
                    exceptionHandlerFiles.assertTrue {
                        it.path.contains("framework/web/src/main/kotlin/com/axians/eaf/framework/web/advice/")
                    }
                }
            }

            test("4.5-UNIT-004: Integration tests should follow package structure") {
                val integrationTestFiles =
                    Konsist
                        .scopeFromProject()
                        .files
                        .filter {
                            it.path.contains("/integration-test/") &&
                                it.name.endsWith("Test.kt") &&
                                it.path.contains("/src/integration-test/kotlin/") // Exclude build artifacts
                        }

                if (integrationTestFiles.isNotEmpty()) {
                    integrationTestFiles.assertTrue {
                        it.path.contains("/integration-test/kotlin/com/axians/eaf/framework/")
                    }
                }
            }

            test("4.5-UNIT-005: Framework modules must NOT depend on product-specific types (ARCH-001)") {
                // Story 6.5 ARCH-001: Framework modules MUST be product-agnostic
                // Prevents compile-time coupling to product domains (e.g., Widget)
                val frameworkFiles =
                    Konsist
                        .scopeFromProject()
                        .files
                        .filter {
                            it.path.contains("/framework/") &&
                                it.path.contains("/src/main/kotlin/") &&
                                !it.path.contains("/test/") &&
                                !it.path.contains("/build/") &&
                                !it.path.contains("/bin/")
                        }

                if (frameworkFiles.isNotEmpty()) {
                    frameworkFiles.assertTrue { file ->
                        // Framework files must NOT import from:
                        // - com.axians.eaf.api.widget.* (product-specific Widget types)
                        // - com.axians.eaf.products.* (product implementations)
                        // - Any other product-specific shared-api domains
                        val imports = file.imports.map { it.name }

                        val hasProductImport =
                            imports.any { importName ->
                                importName.startsWith("com.axians.eaf.api.widget") || // Widget domain
                                    importName.startsWith("com.axians.eaf.products") // Product implementations
                            }

                        !hasProductImport
                    }
                }
            }
        }
    })
