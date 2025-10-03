package com.axians.eaf.tools.cli.commands

import arrow.core.Either
import com.axians.eaf.tools.cli.generators.AggregateGenerator
import com.axians.eaf.tools.cli.generators.GeneratorError
import com.axians.eaf.tools.cli.templates.TemplateEngine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * CLI command for scaffolding new CQRS/ES aggregates.
 *
 * This command generates a complete vertical slice including:
 * - Domain Aggregate with Axon annotations
 * - Commands, Events, Queries (shared API)
 * - JPA Projection Entity and Repository
 * - Event Handler and Query Handler
 * - REST Controller
 * - Liquibase migration (optional)
 * - Test stubs (unit + integration)
 *
 * Security: Implements 3-layer defense against SEC-003 (Template Injection):
 * - Layer 1: Input validation (PascalCase regex, path traversal, injection detection)
 * - Layer 2: Mustache HTML-escaping
 * - Layer 3: File system validation
 *
 * Story 7.3: Create "New Aggregate" Generator
 */
@Command(
    name = "aggregate",
    description = ["Generate a new CQRS/ES aggregate with complete vertical slice"],
    mixinStandardHelpOptions = true,
)
class AggregateCommand : Runnable {
    @Parameters(
        index = "0",
        description = ["Aggregate name (PascalCase, e.g., Product, OrderItem)"],
    )
    private lateinit var aggregateName: String

    @Option(
        names = ["--module", "-m"],
        description = ["Target product module name (e.g., widget-demo, licensing-server)"],
        required = true,
    )
    private lateinit var moduleName: String

    @Option(
        names = ["--fields"],
        description = ["Custom fields (format: name:Type, value:BigDecimal, active:Boolean)"],
    )
    private var fields: String? = null

    @Option(
        names = ["--validation"],
        description = ["Custom validation patterns (format: field:pattern, field:min:max, field:val1|val2)"],
    )
    private var validation: String? = null

    override fun run() {
        // SEC-003 Mitigation Layer 1: Input Validation
        validateAggregateName(aggregateName)
        validateModuleName(moduleName)
        validateModuleExists(moduleName)

        // Initialize generator
        val templateEngine = TemplateEngine()
        val generator = AggregateGenerator(templateEngine)

        // Generate aggregate
        when (val result = generator.generateAggregate(aggregateName, moduleName, fields, validation)) {
            is Either.Left -> {
                handleError(result.value)
                exitProcess(1)
            }
            is Either.Right -> {
                printSuccess(result.value.aggregateName, result.value.moduleName, result.value.filesGenerated)
                exitProcess(0)
            }
        }
    }

    private fun handleError(error: GeneratorError) {
        when (error) {
            is GeneratorError.ModuleNotFound -> {
                System.err.println("Error: Module not found: products/${error.moduleName}")
                System.err.println("Run 'eaf scaffold module ${error.moduleName}' first to create the module.")
            }
            is GeneratorError.AggregateAlreadyExists -> {
                System.err.println("Error: Aggregate '${error.aggregateName}' already exists in '${error.moduleName}'")
                System.err.println("Please choose a different aggregate name.")
            }
            is GeneratorError.UnsupportedType -> {
                System.err.println("Error: Unsupported field type '${error.type}'")
                System.err.println("Supported types: ${error.supportedTypes.joinToString(", ")}")
            }
            is GeneratorError.FileSystemError -> {
                System.err.println("Error: ${error.message}")
                System.err.println("Cause: ${error.cause.message}")
            }
            is GeneratorError.TemplateError -> {
                System.err.println("Error: Failed to render template '${error.template}'")
                System.err.println("Cause: ${error.cause.message}")
            }
            is GeneratorError.InvalidRegexPattern -> {
                System.err.println("Error: Invalid regex pattern '${error.pattern}'")
                error.error?.let { System.err.println("Details: $it") }
            }
            is GeneratorError.UnsafeRegexPattern -> {
                System.err.println("Error: Unsafe regex pattern detected '${error.pattern}'")
                System.err.println("Pattern may cause ReDoS (Regular Expression Denial of Service)")
            }
            is GeneratorError.ModuleAlreadyExists -> {
                System.err.println("Error: Module '${error.name}' already exists")
            }
            // Story 7.4b UI-specific errors (not applicable to aggregate command)
            is GeneratorError.ProductModuleNotFound,
            is GeneratorError.InvalidResourceName,
            is GeneratorError.AdminShellNotBuilt,
            is GeneratorError.TemplateRenderFailed,
            is GeneratorError.PathTraversalDetected,
            -> {
                System.err.println("Error: ${error::class.simpleName}")
                System.err.println("Details: $error")
            }
        }
    }

    private fun printSuccess(
        aggregateName: String,
        moduleName: String,
        filesGenerated: Int,
    ) {
        val aggregateNameKebab =
            aggregateName
                .replace(Regex("([a-z])([A-Z])"), "$1-$2")
                .lowercase()

        println("✓ Aggregate '$aggregateName' created successfully in module '$moduleName'!")
        println()
        println("Files generated: $filesGenerated")
        println()
        println("Domain Layer:")
        println("  ✓ products/$moduleName/src/main/kotlin/.../domain/$aggregateName.kt")
        println("  ✓ products/$moduleName/src/main/kotlin/.../domain/${aggregateName}Error.kt")
        println()
        println("Shared API:")
        println("  ✓ shared/shared-api/.../api/$aggregateNameKebab/commands/Create${aggregateName}Command.kt")
        println("  ✓ shared/shared-api/.../api/$aggregateNameKebab/commands/Update${aggregateName}Command.kt")
        println("  ✓ shared/shared-api/.../api/$aggregateNameKebab/events/${aggregateName}CreatedEvent.kt")
        println("  ✓ shared/shared-api/.../api/$aggregateNameKebab/events/${aggregateName}UpdatedEvent.kt")
        println("  ✓ shared/shared-api/.../api/$aggregateNameKebab/queries/Find${aggregateName}ByIdQuery.kt")
        println("  ✓ shared/shared-api/.../api/$aggregateNameKebab/queries/Find${aggregateName}sQuery.kt")
        println("  ✓ shared/shared-api/.../api/$aggregateNameKebab/dto/${aggregateName}Response.kt")
        println()
        println("Projection Layer:")
        println("  ✓ products/$moduleName/src/main/kotlin/.../entities/${aggregateName}Projection.kt")
        println("  ✓ products/$moduleName/src/main/kotlin/.../repositories/${aggregateName}ProjectionRepository.kt")
        println("  ✓ products/$moduleName/src/main/kotlin/.../projections/${aggregateName}ProjectionHandler.kt")
        println()
        println("Query Layer:")
        println("  ✓ products/$moduleName/src/main/kotlin/.../query/${aggregateName}QueryHandler.kt")
        println()
        println("Controller Layer:")
        println("  ✓ products/$moduleName/src/main/kotlin/.../controllers/${aggregateName}Controller.kt")
        println("  ✓ products/$moduleName/src/main/kotlin/.../controllers/Create${aggregateName}Request.kt")
        println()
        println("Database Migration:")
        println("  ✓ products/$moduleName/src/main/resources/db/changelog/$aggregateNameKebab-projection-table-*.xml")
        println()
        println("Tests:")
        println("  ✓ products/$moduleName/src/test/.../domain/${aggregateName}Test.kt")
        println("  ✓ products/$moduleName/src/test/.../projections/${aggregateName}ProjectionHandlerTest.kt")
        println("  ✓ products/$moduleName/src/integration-test/.../api/${aggregateName}ControllerIntegrationTest.kt")
        println()
        println("Next steps:")
        println("  1. Review generated code and customize validation logic")
        println("  2. Run: ./gradlew :products:$moduleName:jvmKotest")
        println("  3. Implement test TODOs in generated test files")
        println("  4. Add to version control: git add products/$moduleName shared/shared-api")
    }

    /**
     * Validates aggregate name against security requirements (SEC-003 mitigation).
     *
     * Validation Rules:
     * - Must match regex: ^[A-Z][A-Za-z0-9]*$ (PascalCase alphanumeric)
     * - No path traversal patterns (..)
     * - No injection characters (< >)
     * - No special characters ($, @, ;, etc.)
     *
     * @throws IllegalArgumentException if validation fails
     */
    private fun validateAggregateName(name: String) {
        // PascalCase validation: Must start with uppercase, alphanumeric only
        val validPattern = Regex("^[A-Z][A-Za-z0-9]*$")
        require(name.matches(validPattern)) {
            "Invalid aggregate name: '$name'. Must be PascalCase alphanumeric (e.g., Product, OrderItem, UserProfile)."
        }

        // Path traversal protection
        require(!name.contains("..")) {
            "Security violation: path traversal detected in aggregate name"
        }

        // Template injection protection
        require(!name.contains("<") && !name.contains(">")) {
            "Security violation: potential injection detected in aggregate name"
        }

        // Command injection protection (semicolon, pipe, ampersand)
        require(!name.contains(";") && !name.contains("|") && !name.contains("&")) {
            "Security violation: potential command injection detected in aggregate name"
        }

        // Additional special character protection
        require(!name.contains("$") && !name.contains("@") && !name.contains("_")) {
            "Invalid aggregate name: '$name'. No special characters allowed (_, $, @, etc.)."
        }
    }

    /**
     * Validates module name against path traversal attacks.
     *
     * @throws IllegalArgumentException if validation fails
     */
    private fun validateModuleName(name: String) {
        // Path traversal protection
        require(!name.contains("..")) {
            "Security violation: path traversal detected in module name"
        }

        // Template injection protection
        require(!name.contains("<") && !name.contains(">")) {
            "Security violation: potential injection detected in module name"
        }
    }

    /**
     * Validates that target module exists.
     *
     * @throws IllegalArgumentException if module not found
     */
    private fun validateModuleExists(name: String) {
        val modulePath = Paths.get("products", name)

        require(Files.exists(modulePath) && Files.isDirectory(modulePath)) {
            "Module not found: products/$name\n" +
                "Run 'eaf scaffold module $name' first to create the module."
        }
    }
}
