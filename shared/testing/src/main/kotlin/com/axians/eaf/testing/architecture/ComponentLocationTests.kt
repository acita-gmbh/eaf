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
            test("AxonConfiguration should be in correct location when implemented") {
                val axonConfigFiles =
                    Konsist
                        .scopeFromProject()
                        .files
                        .withName("AxonConfiguration")

                // If file exists, it must be in application-level config (where DataSource is available)
                if (axonConfigFiles.isNotEmpty()) {
                    axonConfigFiles.assertTrue {
                        it.path.contains("products/") && it.path.contains("/config/")
                    }
                }
            }

            test("WidgetController should be in correct location when implemented") {
                val widgetControllerFiles =
                    Konsist
                        .scopeFromProject()
                        .files
                        .withName("WidgetController")

                if (widgetControllerFiles.isNotEmpty()) {
                    widgetControllerFiles.assertTrue {
                        it.path.contains("framework/web/src/main/kotlin/com/axians/eaf/framework/web/controllers/")
                    }
                }
            }

            test("GlobalExceptionHandler should be in correct location when implemented") {
                val exceptionHandlerFiles =
                    Konsist
                        .scopeFromProject()
                        .files
                        .withName("GlobalExceptionHandler")

                if (exceptionHandlerFiles.isNotEmpty()) {
                    exceptionHandlerFiles.assertTrue {
                        it.path.contains("framework/web/src/main/kotlin/com/axians/eaf/framework/web/advice/")
                    }
                }
            }

            test("Integration tests should follow package structure") {
                val integrationTestFiles =
                    Konsist
                        .scopeFromProject()
                        .files
                        .filter { it.path.contains("/integration-test/") && it.name.endsWith("Test.kt") }

                if (integrationTestFiles.isNotEmpty()) {
                    integrationTestFiles.assertTrue {
                        it.path.contains("/integration-test/kotlin/com/axians/eaf/framework/")
                    }
                }
            }
        }
    })
