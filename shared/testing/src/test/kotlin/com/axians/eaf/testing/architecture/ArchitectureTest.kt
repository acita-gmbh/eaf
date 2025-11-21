package com.axians.eaf.testing.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withoutAbstractModifier
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
class ArchitectureTest {
    private val scope = Konsist.scopeFromProject()

    @Nested
    inner class ModuleDependencies {
        @Test
        fun `framework modules should not reside in products package`() {
            scope
                .classes()
                .withPackage("..framework..")
                .assertFalse {
                    it.resideInPackage("..products..")
                }
        }

        @Test
        fun `framework classes should not import from products`() {
            scope
                .files
                .withPackage("..framework..")
                .assertFalse {
                    it.hasImport { import -> import.name.contains(".products.") }
                }
        }
    }

    @Nested
    inner class HexagonalArchitectureCompliance {
        @Test
        fun `domain layer should not import from infrastructure`() {
            scope
                .files
                .withPackage("..domain..")
                .assertFalse {
                    it.hasImport { import -> import.name.contains(".infrastructure.") }
                }
        }

        @Test
        fun `domain layer should not import from adapters`() {
            scope
                .files
                .withPackage("..domain..")
                .assertFalse {
                    it.hasImport { import -> import.name.contains(".adapter.") }
                }
        }

        @Test
        fun `domain aggregates should be in domain package (excluding test helpers)`() {
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

    @Nested
    inner class CodingStandards {
        private val testScope = Konsist.scopeFromTest()

        @Test
        fun `no wildcard imports allowed`() {
            scope
                .imports
                .assertFalse {
                    it.isWildcard
                }
        }

        @Test
        fun `test classes must use JUnit 6 (NOT Kotest)`() {
            testScope
                .files
                .assertFalse {
                    it.hasImport { import ->
                        import.name.startsWith("io.kotest.")
                    }
                }
        }
    }

    @Nested
    inner class LayerArchitecture {
        @Test
        @Disabled("TODO(Epic 2+): Re-enable when infrastructure code exists")
        fun `hexagonal architecture core layer is independent`() {
            // Currently disabled in Epic 1 foundation phase - infrastructure layer will be populated
            // in Epic 2 (CQRS/Event Sourcing) and beyond
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
}
