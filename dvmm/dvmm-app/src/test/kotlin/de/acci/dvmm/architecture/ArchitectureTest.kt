package de.acci.dvmm.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withNameMatching
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test

/**
 * Architecture tests using Konsist to enforce module boundaries and coding standards.
 *
 * Key Constraint (ADR-001): EAF modules MUST NOT depend on DVMM modules.
 * Dependency direction: dvmm-* → eaf-* (never the reverse)
 */
class ArchitectureTest {

    @Test
    fun `eaf modules must not depend on dvmm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-core")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("de.acci.dvmm")
                }
            }
    }

    @Test
    fun `eaf-eventsourcing must not depend on dvmm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-eventsourcing")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("de.acci.dvmm")
                }
            }
    }

    @Test
    fun `eaf-tenant must not depend on dvmm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-tenant")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("de.acci.dvmm")
                }
            }
    }

    @Test
    fun `eaf-auth must not depend on dvmm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-auth")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("de.acci.dvmm")
                }
            }
    }

    @Test
    fun `eaf-testing must not depend on dvmm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-testing")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("de.acci.dvmm")
                }
            }
    }

    @Test
    fun `domain module must not have Spring dependencies`() {
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
    fun `classes with Test suffix should be in test source set`() {
        // Scope from specific DVMM and EAF modules only, excluding build-logic
        val dvmmModules = listOf("dvmm/dvmm-domain", "dvmm/dvmm-application", "dvmm/dvmm-api", "dvmm/dvmm-infrastructure", "dvmm/dvmm-app")
        val eafModules = listOf("eaf/eaf-core", "eaf/eaf-eventsourcing", "eaf/eaf-tenant", "eaf/eaf-auth", "eaf/eaf-testing")
        val allModules = dvmmModules + eafModules

        allModules.flatMap { module ->
            Konsist.scopeFromModule(module).classes().withNameEndingWith("Test")
        }
            // Exclude annotation classes (e.g., @VcsimTest meta-annotation in eaf-testing)
            .filterNot { it.hasAnnotationModifier }
            .forEach { clazz ->
                // Check that path contains test source directory marker
                assert(clazz.path.contains("/src/test/")) {
                    "Class ${clazz.name} with Test suffix should be in test source set, but found at: ${clazz.path}"
                }
            }
    }

    /**
     * Ensures query handlers that fetch single entities by ID include a Forbidden error type
     * for proper authorization handling.
     *
     * Pattern: Get*DetailHandler or Get*ByIdHandler must have corresponding *Error sealed class
     * with a Forbidden subtype.
     *
     * Rationale: Authorization belongs in the application layer. Handlers that retrieve
     * specific resources must verify the requesting user is authorized to access them.
     */
    @Test
    fun `detail query handlers must have Forbidden error type for authorization`() {
        val applicationScope = Konsist.scopeFromModule("dvmm/dvmm-application")

        // Find all Get*Detail*Handler or Get*ById*Handler classes
        val detailHandlers = applicationScope
            .classes()
            .withNameMatching(Regex("Get.*Detail.*Handler|Get.*ById.*Handler"))

        detailHandlers.forEach { handler ->
            // Derive expected error class name (e.g., GetRequestDetailHandler -> GetRequestDetailError)
            val errorClassName = handler.name.replace("Handler", "Error")

            // Find the corresponding error sealed class in the same package
            val errorClass = applicationScope
                .classes()
                .firstOrNull { it.name == errorClassName }

            assert(errorClass != null) {
                "Handler ${handler.name} must have corresponding $errorClassName sealed class"
            }

            // Check that error class has a Forbidden subtype
            val hasForbiddenSubtype = applicationScope
                .classes()
                .any { it.name == "Forbidden" && it.resideInPackage(errorClass!!.packagee!!.name) }
                    || errorClass?.text?.contains("Forbidden") == true

            assert(hasForbiddenSubtype) {
                "$errorClassName must include a Forbidden subtype for authorization errors. " +
                    "Add: data class Forbidden(val message: String = \"Not authorized\") : $errorClassName()"
            }
        }
    }
}
