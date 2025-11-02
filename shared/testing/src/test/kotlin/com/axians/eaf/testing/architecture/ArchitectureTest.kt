package com.axians.eaf.testing.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withoutAbstractModifier
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.FreeSpec

/**
 * Konsist Architecture Tests - Module Boundary Enforcement
 *
 * Story 1.8: Spring Modulith Module Boundary Enforcement
 * These tests enforce architectural rules across the EAF codebase:
 * - Module boundaries and dependencies
 * - Hexagonal architecture compliance
 * - Coding standards (no wildcard imports)
 * - Package structure compliance
 *
 * Execution: ./gradlew :shared:testing:test
 * Target: <5 seconds execution time
 * Policy: Zero violations enforced (build fails on violations)
 */
class ArchitectureTest :
    FreeSpec({
        val scope = Konsist.scopeFromProject()

        "Module Dependencies" - {
            "framework modules should not reside in products package" {
                scope
                    .classes()
                    .withPackage("..framework..")
                    .assertFalse {
                        it.resideInPackage("..products..")
                    }
            }

            "framework classes should not import from products" {
                scope
                    .files
                    .withPackage("..framework..")
                    .assertFalse {
                        it.hasImport { import -> import.name.contains(".products.") }
                    }
            }
        }

        "Hexagonal Architecture Compliance" - {
            "domain layer should not import from infrastructure" {
                scope
                    .files
                    .withPackage("..domain..")
                    .assertFalse {
                        it.hasImport { import -> import.name.contains(".infrastructure.") }
                    }
            }

            "domain layer should not import from adapters" {
                scope
                    .files
                    .withPackage("..domain..")
                    .assertFalse {
                        it.hasImport { import -> import.name.contains(".adapter.") }
                    }
            }

            "domain aggregates should be in domain package (excluding test helpers)" {
                scope
                    .classes()
                    .withNameEndingWith("Aggregate")
                    .withoutAbstractModifier()
                    .withPackage("!..test..", "!..testing..") // Exclude test packages
                    .assertTrue {
                        it.resideInPackage("..domain..")
                    }
            }
        }

        "Coding Standards" - {
            "no wildcard imports allowed" {
                scope
                    .imports
                    .assertFalse {
                        it.isWildcard
                    }
            }

            "test classes must use Kotest (NOT JUnit)" {
                scope
                    .files
                    .withPackage("..test..")
                    .assertFalse {
                        it.hasImport { import ->
                            import.name == "org.junit.Test" ||
                                import.name == "org.junit.jupiter.api.Test"
                        }
                    }
            }
        }

        "Layer Architecture" - {
            "hexagonal architecture core layer is independent" {
                scope.assertArchitecture {
                    val domain = Layer("Domain", "..domain..")
                    val infrastructure = Layer("Infrastructure", "..infrastructure..")

                    // Domain should not depend on infrastructure
                    domain.dependsOnNothing()

                    // Infrastructure may depend on domain
                    infrastructure.dependsOn(domain)
                }
            }
        }
    })
