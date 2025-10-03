package com.axians.eaf.tools.cli.generators

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.axians.eaf.tools.cli.templates.TemplateEngine
import java.io.File

/**
 * Generator for React-Admin UI resources in product modules
 *
 * Story 7.4b: Create Product UI Module Generator
 * Security: 3-layer defense against path traversal (SEC-001 mitigation from Story 7.3 ADVISORY-001)
 *
 * Generated files go to: products/{module}/ui-module/src/resources/{resourceLowerCase}/
 * Imports from: @axians/eaf-admin-shell (Story 7.4a)
 */
class UiResourceGenerator {
    private val templateEngine = TemplateEngine()

    fun generate(
        resourceName: String,
        moduleName: String,
        fields: String?,
        apiBasePath: String?,
    ): Either<GeneratorError, String> {
        // Layer 1: Input Validation (PascalCase pattern)
        val pascalCasePattern = Regex("^[A-Z][A-Za-z0-9]*$")
        if (!resourceName.matches(pascalCasePattern)) {
            return GeneratorError
                .InvalidResourceName(
                    resourceName,
                    "Resource name must be PascalCase: start with uppercase letter, alphanumeric only",
                ).left()
        }

        // Validate product module exists
        val productModuleDir = File("products/$moduleName")
        if (!productModuleDir.exists()) {
            return GeneratorError.ProductModuleNotFound(moduleName).left()
        }

        // Layer 2: Path Sanitization (remove dangerous characters)
        val sanitizedResourceName = resourceName.replace(Regex("[./\\\\~]"), "")
        val resourceLowerCase = sanitizedResourceName.lowercase()

        // Layer 3: Canonical Path Verification
        val targetDir = File("products/$moduleName/ui-module/src/resources/$resourceLowerCase")
        val canonicalPath = targetDir.canonicalFile
        val projectRoot = File(".").canonicalFile

        if (!canonicalPath.absolutePath.startsWith(projectRoot.absolutePath)) {
            return GeneratorError
                .PathTraversalDetected(
                    targetDir.absolutePath,
                    "Path escapes project boundary: $canonicalPath",
                ).left()
        }

        // Create ui-module structure if doesn't exist (Task 1)
        val uiModuleDir = File("products/$moduleName/ui-module")
        if (!uiModuleDir.exists()) {
            val initResult = initializeUiModule(moduleName)
            if (initResult.isLeft()) {
                return initResult
            }
        }

        // Parse fields or use defaults
        val fieldList = parseFields(fields ?: "name:string")

        // Build context for Mustache templates
        val context =
            mapOf(
                "resourceName" to resourceName,
                "resourceNameLowerCase" to resourceLowerCase,
                "apiBasePath" to (apiBasePath ?: "/api/v1/$resourceLowerCase"),
                "fields" to fieldList,
                "hasDateFields" to fieldList.any { it["type"] == "Date" },
                "hasNumberFields" to fieldList.any { it["type"] == "number" },
            )

        // Generate files from templates
        return try {
            targetDir.mkdirs()

            val templates =
                listOf(
                    "List.tsx",
                    "Create.tsx",
                    "Edit.tsx",
                    "Show.tsx",
                    "types.ts",
                    "EmptyState.tsx",
                    "LoadingSkeleton.tsx",
                    "ResourceExport.ts",
                    "index.ts",
                    "README.md",
                )

            templates.forEach { templateName ->
                val rendered = templateEngine.render("ui-resource/$templateName", context)
                val outputFile = File(targetDir, templateName.replace(".mustache", ""))
                outputFile.writeText(rendered)
            }

            // Update ui-module index.ts to export resource
            updateUiModuleIndex(moduleName, resourceLowerCase)

            targetDir.absolutePath.right()
        } catch (e: Exception) {
            GeneratorError.TemplateRenderFailed(resourceName, e.message ?: "Unknown error").left()
        }
    }

    private fun initializeUiModule(moduleName: String): Either<GeneratorError, String> {
        val uiModuleDir = File("products/$moduleName/ui-module")
        uiModuleDir.mkdirs()

        // Create package.json
        val packageJson =
            """
            {
              "name": "@eaf/product-$moduleName-ui",
              "version": "0.1.0",
              "type": "module",
              "main": "./dist/index.js",
              "types": "./dist/index.d.ts",
              "scripts": {
                "build": "vite build",
                "typecheck": "tsc --noEmit"
              },
              "peerDependencies": {
                "react": "^18.0.0",
                "react-admin": "5.4.0",
                "@axians/eaf-admin-shell": "workspace:*"
              },
              "devDependencies": {
                "typescript": "^5.0.0",
                "vite": "^5.0.0"
              }
            }
            """.trimIndent()

        File(uiModuleDir, "package.json").writeText(packageJson)

        // Create tsconfig.json
        val tsConfig =
            """
            {
              "extends": "../../../framework/admin-shell/tsconfig.json",
              "compilerOptions": {
                "outDir": "./dist"
              },
              "include": ["src"]
            }
            """.trimIndent()

        File(uiModuleDir, "tsconfig.json").writeText(tsConfig)

        // Create src/index.ts
        val srcDir = File(uiModuleDir, "src")
        srcDir.mkdirs()
        File(srcDir, "index.ts").writeText("// Product UI module exports\n")

        // Create .gitignore
        File(uiModuleDir, ".gitignore").writeText("node_modules/\ndist/\n")

        return uiModuleDir.absolutePath.right()
    }

    private fun updateUiModuleIndex(
        moduleName: String,
        resourceLowerCase: String,
    ) {
        val indexFile = File("products/$moduleName/ui-module/src/index.ts")
        val currentContent = if (indexFile.exists()) indexFile.readText() else ""

        val exportStatement = "export { ${resourceLowerCase}Resource } from './resources/$resourceLowerCase/ResourceExport';\n"

        if (!currentContent.contains(exportStatement)) {
            indexFile.appendText(exportStatement)
        }
    }

    private fun parseFields(fieldsString: String): List<Map<String, String>> {
        return fieldsString.split(",").map { fieldDef ->
            val parts = fieldDef.trim().split(":")
            val fieldName = parts[0].trim()
            val fieldType = if (parts.size > 1) parts[1].trim() else "string"

            // Map TypeScript type to React-Admin components
            val (tsType, inputComponent, fieldComponent) =
                when (fieldType.lowercase()) {
                    "string" -> Triple("string", "TextInput", "TextField")
                    "number" -> Triple("number", "NumberInput", "NumberField")
                    "boolean" -> Triple("boolean", "BooleanInput", "BooleanField")
                    "date" -> Triple("Date", "DateTimeInput", "DateField")
                    else -> Triple("string", "TextInput", "TextField")
                }

            mapOf(
                "fieldName" to fieldName,
                "fieldLabel" to fieldName.replaceFirstChar { it.uppercase() },
                "fieldType" to tsType,
                "inputComponent" to inputComponent,
                "fieldComponent" to fieldComponent,
            )
        }
    }
}
