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
class ComponentLocationTests : FunSpec({

    context("Axon Framework Configuration Components") {
        test("AxonConfiguration should exist in correct location when implemented") {
            val axonConfigFiles = Konsist.scopeFromProject()
                .files
                .withName("AxonConfiguration")

            // If file exists, it must be in the correct location
            if (axonConfigFiles.isNotEmpty()) {
                axonConfigFiles.assertTrue {
                    it.path.contains("framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/config/")
                }
            }
        }

        test("EventStoreConfiguration should exist in correct location when implemented") {
            val eventStoreConfigFiles = Konsist.scopeFromProject()
                .files
                .withName("EventStoreConfiguration")

            if (eventStoreConfigFiles.isNotEmpty()) {
                eventStoreConfigFiles.assertTrue {
                    it.path.contains("framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/config/")
                }
            }
        }
    }

    context("REST API Components") {
        test("Controllers should be in framework/web/controllers package") {
            val controllerFiles = Konsist.scopeFromProject()
                .classes()
                .withNameEndingWith("Controller")
                .withPackage("..framework.web..")

            if (controllerFiles.isNotEmpty()) {
                controllerFiles.assertTrue {
                    it.packagee?.fullyQualifiedName?.contains("com.axians.eaf.framework.web.controllers") == true
                }
            }
        }

        test("WidgetController should be in correct location when implemented") {
            val widgetControllerFiles = Konsist.scopeFromProject()
                .files
                .withName("WidgetController")

            if (widgetControllerFiles.isNotEmpty()) {
                widgetControllerFiles.assertTrue {
                    it.path.contains("framework/web/src/main/kotlin/com/axians/eaf/framework/web/controllers/")
                }
            }
        }

        test("GlobalExceptionHandler should be in correct location when implemented") {
            val exceptionHandlerFiles = Konsist.scopeFromProject()
                .files
                .withName("GlobalExceptionHandler")

            if (exceptionHandlerFiles.isNotEmpty()) {
                exceptionHandlerFiles.assertTrue {
                    it.path.contains("framework/web/src/main/kotlin/com/axians/eaf/framework/web/advice/")
                }
            }
        }
    }

    context("Domain Components") {
        test("Domain aggregates should follow package structure") {
            val aggregateClasses = Konsist.scopeFromProject()
                .classes()
                .withAnnotationOf("org.axonframework.spring.stereotype.Aggregate")

            if (aggregateClasses.isNotEmpty()) {
                aggregateClasses.assertTrue {
                    it.packagee?.fullyQualifiedName?.contains(".framework.") == true &&
                    it.packagee?.fullyQualifiedName?.contains(".domain") == true
                }
            }
        }

        test("Widget aggregate should be in correct location") {
            val widgetFiles = Konsist.scopeFromProject()
                .files
                .withName("Widget")
                .filter { it.path.contains("domain") }

            if (widgetFiles.isNotEmpty()) {
                widgetFiles.assertTrue {
                    it.path.contains("framework/widget/src/main/kotlin/com/axians/eaf/framework/widget/domain/")
                }
            }
        }
    }

    context("CQRS Shared API Components") {
        test("Commands should be in shared-api commands package") {
            val commandFiles = Konsist.scopeFromProject()
                .classes()
                .withNameEndingWith("Command")
                .withPackage("..api..")

            if (commandFiles.isNotEmpty()) {
                commandFiles.assertTrue {
                    it.packagee?.fullyQualifiedName?.contains("com.axians.eaf.api") == true &&
                    it.packagee?.fullyQualifiedName?.contains(".commands") == true
                }
            }
        }

        test("Events should be in shared-api events package") {
            val eventFiles = Konsist.scopeFromProject()
                .classes()
                .withNameEndingWith("Event")
                .withPackage("..api..")

            if (eventFiles.isNotEmpty()) {
                eventFiles.assertTrue {
                    it.packagee?.fullyQualifiedName?.contains("com.axians.eaf.api") == true &&
                    it.packagee?.fullyQualifiedName?.contains(".events") == true
                }
            }
        }
    }

    context("Testing Components") {
        test("Domain tests should follow package structure") {
            val domainTestFiles = Konsist.scopeFromProject()
                .files
                .withPath("**/src/test/**")
                .filter { it.name.endsWith("Test.kt") }
                .filter { it.path.contains("domain") }

            if (domainTestFiles.isNotEmpty()) {
                domainTestFiles.assertTrue {
                    it.path.contains("/test/kotlin/com/axians/eaf/framework/") &&
                    it.path.contains("/domain/")
                }
            }
        }

        test("Integration tests should follow package structure") {
            val integrationTestFiles = Konsist.scopeFromProject()
                .files
                .withPath("**/src/integration-test/**")
                .filter { it.name.endsWith("Test.kt") }

            if (integrationTestFiles.isNotEmpty()) {
                integrationTestFiles.assertTrue {
                    it.path.contains("/integration-test/kotlin/com/axians/eaf/framework/")
                }
            }
        }
    }

    context("Module Structure Validation") {
        test("All framework modules should have proper Spring Modulith config") {
            val moduleConfigFiles = Konsist.scopeFromProject()
                .classes()
                .withNameEndingWith("Module")
                .withAnnotationOf("org.springframework.modulith.ApplicationModule")

            if (moduleConfigFiles.isNotEmpty()) {
                moduleConfigFiles.assertTrue {
                    it.packagee?.fullyQualifiedName?.startsWith("com.axians.eaf.framework.") == true &&
                    !it.packagee?.fullyQualifiedName?.contains(".config") == true &&
                    !it.packagee?.fullyQualifiedName?.contains(".domain") == true
                }
            }
        }

        test("Configuration classes should be in config packages") {
            val configClasses = Konsist.scopeFromProject()
                .classes()
                .withNameEndingWith("Configuration")
                .withPackage("..framework..")

            if (configClasses.isNotEmpty()) {
                configClasses.assertTrue {
                    it.packagee?.fullyQualifiedName?.contains(".config") == true
                }
            }
        }
    }
})