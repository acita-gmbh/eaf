package com.acita.dvmm.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test

/**
 * Architecture tests using Konsist to enforce module boundaries and coding standards.
 *
 * Key Constraint (ADR-001): EAF modules MUST NOT depend on DVMM modules.
 * Dependency direction: dvmm-* → eaf-* (never the reverse)
 */
public class ArchitectureTest {

    @Test
    public fun `eaf modules must not depend on dvmm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-core")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("com.acita.dvmm")
                }
            }
    }

    @Test
    public fun `eaf-eventsourcing must not depend on dvmm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-eventsourcing")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("com.acita.dvmm")
                }
            }
    }

    @Test
    public fun `eaf-tenant must not depend on dvmm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-tenant")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("com.acita.dvmm")
                }
            }
    }

    @Test
    public fun `eaf-auth must not depend on dvmm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-auth")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("com.acita.dvmm")
                }
            }
    }

    @Test
    public fun `eaf-testing must not depend on dvmm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-testing")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("com.acita.dvmm")
                }
            }
    }

    @Test
    public fun `domain module must not have Spring dependencies`() {
        Konsist
            .scopeFromModule("dvmm/dvmm-domain")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.startsWith("org.springframework")
                }
            }
    }

    @Test
    public fun `classes with Test suffix should be in test source set`() {
        // Scope from specific DVMM and EAF modules only, excluding build-logic
        val dvmmModules = listOf("dvmm/dvmm-domain", "dvmm/dvmm-application", "dvmm/dvmm-api", "dvmm/dvmm-infrastructure", "dvmm/dvmm-app")
        val eafModules = listOf("eaf/eaf-core", "eaf/eaf-eventsourcing", "eaf/eaf-tenant", "eaf/eaf-auth", "eaf/eaf-testing")
        val allModules = dvmmModules + eafModules

        allModules.flatMap { module ->
            Konsist.scopeFromModule(module).classes().withNameEndingWith("Test")
        }.forEach { clazz ->
            // Check that path contains test source directory marker
            assert(clazz.path.contains("/src/test/")) {
                "Class ${clazz.name} with Test suffix should be in test source set, but found at: ${clazz.path}"
            }
        }
    }
}
