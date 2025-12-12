package de.acci.dcm.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withNameMatching
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

/**
 * Architecture tests using Konsist to enforce module boundaries and coding standards.
 *
 * Key Constraint (ADR-001): EAF modules MUST NOT depend on DCM modules.
 * Dependency direction: dcm-* → eaf-* (never the reverse)
 */
class ArchitectureTest {

    @Test
    fun `eaf modules must not depend on dcm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-core")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("de.acci.dcm")
                }
            }
    }

    @Test
    fun `eaf-eventsourcing must not depend on dcm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-eventsourcing")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("de.acci.dcm")
                }
            }
    }

    @Test
    fun `eaf-tenant must not depend on dcm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-tenant")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("de.acci.dcm")
                }
            }
    }

    @Test
    fun `eaf-auth must not depend on dcm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-auth")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("de.acci.dcm")
                }
            }
    }

    @Test
    fun `eaf-testing must not depend on dcm modules`() {
        Konsist
            .scopeFromModule("eaf/eaf-testing")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.contains("de.acci.dcm")
                }
            }
    }

    @Test
    fun `domain module must not have Spring dependencies`() {
        Konsist
            .scopeFromModule("dcm/dcm-domain")
            .files
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.startsWith("org.springframework")
                }
            }
    }

    @Test
    fun `classes with Test suffix should be in test source set`() {
        // Scope from specific DCM and EAF modules only, excluding build-logic
        val dcmModules = listOf("dcm/dcm-domain", "dcm/dcm-application", "dcm/dcm-api", "dcm/dcm-infrastructure", "dcm/dcm-app")
        val eafModules = listOf("eaf/eaf-core", "eaf/eaf-eventsourcing", "eaf/eaf-tenant", "eaf/eaf-auth", "eaf/eaf-testing")
        val allModules = dcmModules + eafModules

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
     * Pattern: Matches `Get*Detail*Handler` or `Get*ById*Handler` (regex allows flexible naming).
     * Each must have a corresponding `*Error` sealed class containing a `Forbidden` data class.
     *
     * Rationale: Authorization belongs in the application layer. Handlers that retrieve
     * specific resources must verify the requesting user is authorized to access them.
     */
    @Test
    fun `detail query handlers must have Forbidden error type for authorization`() {
        val applicationScope = Konsist.scopeFromModule("dcm/dcm-application")

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

            // Verify Forbidden is a nested class within the error sealed class.
            // We check the error class source for "data class Forbidden" or "class Forbidden"
            // to ensure it's an actual subtype declaration, not just a comment mention.
            val errorClassSource = errorClass?.text ?: ""
            val hasForbiddenSubtype = errorClassSource.contains(Regex("""(data\s+)?class\s+Forbidden"""))

            assert(hasForbiddenSubtype) {
                "$errorClassName must include a Forbidden subtype for authorization errors. " +
                    "Add: data class Forbidden(val message: String = \"Not authorized\") : $errorClassName()"
            }
        }
    }

    /**
     * Ensures suspend functions that catch generic Exception also handle CancellationException.
     *
     * Pattern: Catches `catch (e: Exception)` or `catch (e: Throwable)` in suspend functions
     * without a preceding CancellationException handler or rethrow.
     *
     * Rationale: Kotlin's structured concurrency uses CancellationException to propagate
     * cancellation signals. Catching it with a broad Exception handler breaks cancellation,
     * causing resource leaks and preventing proper cleanup. See CLAUDE.md for details.
     *
     * Note: This is a heuristic check using text patterns. It may have false positives
     * for intentionally swallowed exceptions, but errs on the side of safety.
     */
    @Test
    fun `suspend functions catching Exception must handle CancellationException`() {
        val applicationScope = Konsist.scopeFromModule("dcm/dcm-application")
        val infrastructureScope = Konsist.scopeFromModule("dcm/dcm-infrastructure")

        val allFunctions = applicationScope.functions() + infrastructureScope.functions()

        // Find suspend functions with catch blocks using text-based detection
        // (Konsist API for modifiers varies between versions, text matching is more stable)
        val violations = allFunctions
            .filter { func ->
                val source = func.text

                // Check if it's a suspend function
                val isSuspend = source.trimStart().startsWith("suspend ") ||
                    source.contains(Regex("""\bsuspend\s+fun\b"""))

                if (!isSuspend) return@filter false

                // Check if function has a broad exception catch
                val hasBroadCatch = source.contains(Regex("""catch\s*\(\s*\w+\s*:\s*(Exception|Throwable)\s*\)"""))

                if (!hasBroadCatch) return@filter false

                // Check if CancellationException is properly handled:
                // Must have a catch block for CancellationException that rethrows it.
                // Pattern: catch (e: CancellationException) { throw e } or similar
                val cancellationCatchWithRethrow = Regex(
                    """catch\s*\(\s*(\w+)\s*:\s*CancellationException\s*\)\s*\{[^}]*throw\s+\1"""
                )
                val hasCancellationRethrow = source.contains(cancellationCatchWithRethrow)

                // If the rethrow pattern is missing, it's a violation
                !hasCancellationRethrow
            }
            .map { "${it.containingFile.name}:${it.name}" }

        if (violations.isNotEmpty()) {
            fail<Unit>(
                "Suspend functions with catch(Exception) must also handle CancellationException.\n" +
                    "Add: catch (e: CancellationException) { throw e }\n" +
                    "Violations found in:\n${violations.joinToString("\n") { "  - $it" }}"
            )
        }
    }
}
