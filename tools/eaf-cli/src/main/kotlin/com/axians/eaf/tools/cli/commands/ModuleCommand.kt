package com.axians.eaf.tools.cli.commands

import arrow.core.Either
import com.axians.eaf.tools.cli.generators.GeneratorError
import com.axians.eaf.tools.cli.generators.ModuleGenerator
import com.axians.eaf.tools.cli.templates.TemplateEngine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import kotlin.system.exitProcess

/**
 * CLI command for scaffolding new Spring Modulith-compliant product modules.
 *
 * This command generates a complete Gradle sub-project with:
 * - Convention plugin configuration (eaf.testing, eaf.spring-boot, etc.)
 * - Spring Modulith ModuleMetadata.kt
 * - Application configuration files
 * - Standard directory structure (main, test, integration-test, konsist-test)
 *
 * Security: Implements 3-layer defense against SEC-002 (Template Injection):
 * - Layer 1: Input validation (regex, path traversal check, injection detection)
 * - Layer 2: Mustache HTML-escaping
 * - Layer 3: File system validation
 */
@Command(
    name = "module",
    description = ["Generate a new Spring Modulith-compliant product module"],
    mixinStandardHelpOptions = true,
)
class ModuleCommand : Runnable {
    @Parameters(
        index = "0",
        description = ["Module name (kebab-case, alphanumeric with hyphens)"],
    )
    private lateinit var moduleName: String

    @Option(
        names = ["--directory", "-d"],
        description = ["Parent directory for module (default: products/)"],
        defaultValue = "products",
    )
    private var directory: String = "products"

    override fun run() {
        // SEC-002 Mitigation Layer 1: Input Validation
        validateModuleName(moduleName)

        // Initialize generator
        val templateEngine = TemplateEngine()
        val generator = ModuleGenerator(templateEngine)

        // Generate module
        when (val result = generator.generateModule(moduleName, directory)) {
            is Either.Left -> {
                handleError(result.value)
                exitProcess(1)
            }
            is Either.Right -> {
                printSuccess(result.value.moduleName, result.value.modulePath.toString())
                exitProcess(0)
            }
        }
    }

    /**
     * Handles and displays generator errors.
     */
    private fun handleError(error: GeneratorError) {
        when (error) {
            is GeneratorError.ModuleAlreadyExists -> {
                System.err.println("Error: Module '${error.name}' already exists at ${error.path}")
                System.err.println("Please choose a different module name or remove the existing directory.")
            }
            is GeneratorError.FileSystemError -> {
                System.err.println("Error: ${error.message}")
                System.err.println("Cause: ${error.cause.message}")
            }
            is GeneratorError.TemplateError -> {
                System.err.println("Error: Failed to render template '${error.template}'")
                System.err.println("Cause: ${error.cause.message}")
            }
        }
    }

    /**
     * Prints success message with next steps (Task 8).
     */
    private fun printSuccess(
        moduleName: String,
        modulePath: String,
    ) {
        println("✓ Module '$moduleName' created successfully!")
        println()
        println("Location: $modulePath/")
        println()
        println("Files created:")
        println("  ✓ build.gradle.kts")
        println(
            "  ✓ src/main/kotlin/com/axians/eaf/products/${moduleName.replace(
                "-",
                "",
            )}/${moduleName.replace("-", "").replaceFirstChar { it.uppercaseChar() }}Module.kt",
        )
        println("  ✓ src/main/resources/application.yml")
        println("  ✓ src/test/resources/application-test.yml")
        println()
        println("Module registered in settings.gradle.kts")
        println()
        println("Next steps:")
        println("  1. Review build.gradle.kts and adjust dependencies if needed")
        println("  2. Run: ./gradlew :$directory:$moduleName:build")
        println("  3. Create domain aggregates: eaf scaffold aggregate <Name> --module $moduleName")
    }

    /**
     * Validates module name against security requirements (SEC-002 mitigation).
     *
     * Validation Rules:
     * - Must match regex: ^[a-z][a-z0-9-]*$ (lowercase alphanumeric with hyphens)
     * - No path traversal patterns (..)
     * - No injection characters (< >)
     *
     * @throws IllegalArgumentException if validation fails
     */
    private fun validateModuleName(name: String) {
        // Regex validation: alphanumeric with hyphens, no special chars
        val validPattern = Regex("^[a-z][a-z0-9-]*$")
        require(name.matches(validPattern)) {
            "Invalid module name: '$name'. Must be lowercase alphanumeric with hyphens (kebab-case)."
        }

        // Path traversal protection
        require(!name.contains("..")) {
            "Security violation: path traversal detected in module name"
        }

        // Template injection protection
        require(!name.contains("<") && !name.contains(">")) {
            "Security violation: potential injection detected in module name"
        }
    }
}
