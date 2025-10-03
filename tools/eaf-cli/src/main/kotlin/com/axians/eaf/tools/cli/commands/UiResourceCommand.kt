package com.axians.eaf.tools.cli.commands

import com.axians.eaf.tools.cli.generators.UiResourceGenerator
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.util.concurrent.Callable

/**
 * CLI command to generate React-Admin UI resources for product modules
 *
 * Story 7.4b: Create Product UI Module Generator
 * Follows Story 7.3 AggregateCommand pattern with security-first approach
 *
 * Security: 3-layer defense (input validation, path sanitization, canonical verification)
 * Risk: SEC-001 (Path Traversal and Injection Attacks) - score 6 (HIGH)
 *
 * Usage:
 * ```
 * eaf scaffold ui-resource Product --module licensing-server
 * eaf scaffold ui-resource License --module licensing-server --fields "key:string,expiresAt:date"
 * ```
 */
@Command(
    name = "ui-resource",
    description = ["Generate React-Admin UI resource files for a product module"],
    mixinStandardHelpOptions = true,
)
class UiResourceCommand : Callable<Int> {
    @Parameters(
        index = "0",
        description = ["Resource name in PascalCase (e.g., Product, License)"],
    )
    private lateinit var resourceName: String

    @Option(
        names = ["--module", "-m"],
        description = ["Target product module name (must exist in products/)"],
        required = true,
    )
    private lateinit var moduleName: String

    @Option(
        names = ["--fields", "-f"],
        description = [
            "Comma-separated field definitions (format: name:type)",
            "Types: string, number, boolean, date",
            "Example: name:string,price:number,active:boolean",
        ],
        required = false,
    )
    private var fields: String? = null

    @Option(
        names = ["--api-base-path", "-a"],
        description = ["API endpoint base path (default: /api/v1/{resourceLowerCase})"],
        required = false,
    )
    private var apiBasePath: String? = null

    override fun call(): Int {
        // Layer 1: Input Validation (PascalCase pattern - SEC-001 mitigation)
        val pascalCasePattern = Regex("^[A-Z][A-Za-z0-9]*$")
        if (!resourceName.matches(pascalCasePattern)) {
            System.err.println("❌ ERROR: Resource name must be PascalCase (start with uppercase letter, alphanumeric only)")
            System.err.println("   Invalid: $resourceName")
            System.err.println("   Valid examples: Product, License, Customer")
            return 1
        }

        // Validate product module exists (Task 3.3)
        val productModuleDir = File("products/$moduleName")
        if (!productModuleDir.exists() || !productModuleDir.isDirectory) {
            System.err.println("❌ ERROR: Product module not found: products/$moduleName")
            System.err.println("   Available modules:")
            File("products").listFiles()?.filter { it.isDirectory }?.forEach {
                System.err.println("   - ${it.name}")
            }
            return 1
        }

        // Validate framework/admin-shell exists and is built (Task 3.6 - Story 7.4a dependency)
        val frameworkShellDist = File("framework/admin-shell/dist")
        if (!frameworkShellDist.exists()) {
            System.err.println("❌ ERROR: Framework admin-shell not built")
            System.err.println("   Run: npm run build:admin-shell")
            System.err.println("   Then retry: eaf scaffold ui-resource $resourceName --module $moduleName")
            return 1
        }

        // Generate UI resource using generator service
        val generator = UiResourceGenerator()
        val result =
            generator.generate(
                resourceName = resourceName,
                moduleName = moduleName,
                fields = fields,
                apiBasePath = apiBasePath,
            )

        return when {
            result.isRight() -> {
                val generatedPath = result.getOrNull()
                println("✅ Generated UI resource: $resourceName")
                println("   Location: $generatedPath")
                println("   Files: List.tsx, Create.tsx, Edit.tsx, Show.tsx, types.ts, + 5 more")
                println("\n📝 Next steps:")
                println("   1. Review generated files in $generatedPath")
                println("   2. Import resource in apps/admin/src/main.tsx:")
                println("      import { ${resourceName.lowercase()}Resource } from '@eaf/product-$moduleName-ui';")
                println("   3. Add to AdminShell: <AdminShell resources={[${resourceName.lowercase()}Resource]} />")
                0
            }

            else -> {
                val error = result.leftOrNull()
                System.err.println("❌ ERROR: Failed to generate UI resource")
                System.err.println("   $error")
                1
            }
        }
    }
}
