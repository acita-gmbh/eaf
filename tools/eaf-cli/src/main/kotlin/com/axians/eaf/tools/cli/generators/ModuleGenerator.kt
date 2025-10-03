package com.axians.eaf.tools.cli.generators

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.axians.eaf.tools.cli.templates.TemplateEngine
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Generator for Spring Modulith-compliant product modules.
 *
 * Generates complete module structure including:
 * - Directory structure (main, test, integration-test, konsist-test)
 * - build.gradle.kts with convention plugins
 * - ModuleMetadata.kt for Spring Modulith
 * - Application configuration files (application.yml, application-test.yml)
 * - settings.gradle.kts registration
 *
 * Error Handling: Uses Arrow Either for type-safe error propagation (no exceptions in happy path).
 */
class ModuleGenerator(
    private val templateEngine: TemplateEngine,
) {
    /**
     * Generates a complete module structure.
     *
     * @param moduleName Kebab-case module name (e.g., "my-licensing-server")
     * @param directory Parent directory (default: "products")
     * @param description Module description (optional)
     * @param updateSettings Whether to update settings.gradle.kts (default: true)
     * @return Either.Right(ModuleInfo) on success, Either.Left(GeneratorError) on failure
     *
     * Complexity Note: Sequential file generation with early returns for errors (Either pattern).
     * ReturnCount and CyclomaticComplexMethod suppressions justified by error handling requirements.
     */
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    fun generateModule(
        moduleName: String,
        directory: String = "products",
        description: String? = null,
        updateSettings: Boolean = true,
    ): Either<GeneratorError, ModuleInfo> {
        // Check existing directory (fail-fast)
        val modulePath = Paths.get(directory).resolve(moduleName)
        if (modulePath.exists()) {
            return GeneratorError.ModuleAlreadyExists(moduleName, modulePath).left()
        }

        // Create module context
        val context = ModuleContext.fromModuleName(moduleName, description)

        // Create directory structure
        val createDirResult = createDirectoryStructure(modulePath, context)
        when (createDirResult) {
            is Either.Left -> return createDirResult
            is Either.Right -> Unit // Continue
        }

        // Generate files from templates
        val filesCreated = mutableListOf<Path>()

        // Generate build.gradle.kts
        val buildGradleResult = generateBuildGradle(modulePath, context)
        when (buildGradleResult) {
            is Either.Left -> return buildGradleResult
            is Either.Right -> filesCreated.add(buildGradleResult.value)
        }

        // Generate ModuleMetadata.kt
        val moduleMetadataResult = generateModuleMetadata(modulePath, context)
        when (moduleMetadataResult) {
            is Either.Left -> return moduleMetadataResult
            is Either.Right -> filesCreated.add(moduleMetadataResult.value)
        }

        // Generate Application.kt (Spring Boot main class)
        val applicationResult = generateApplicationClass(modulePath, context)
        when (applicationResult) {
            is Either.Left -> return applicationResult
            is Either.Right -> filesCreated.add(applicationResult.value)
        }

        // Generate application.yml
        val appYmlResult = generateApplicationYml(modulePath, context)
        when (appYmlResult) {
            is Either.Left -> return appYmlResult
            is Either.Right -> filesCreated.add(appYmlResult.value)
        }

        // Generate application-test.yml
        val appTestYmlResult = generateApplicationTestYml(modulePath, context)
        when (appTestYmlResult) {
            is Either.Left -> return appTestYmlResult
            is Either.Right -> filesCreated.add(appTestYmlResult.value)
        }

        // Update settings.gradle.kts (optional for unit tests)
        if (updateSettings) {
            val settingsUpdateResult = updateSettingsGradle(moduleName, directory)
            when (settingsUpdateResult) {
                is Either.Left -> return settingsUpdateResult
                is Either.Right -> Unit // Continue
            }
        }

        return ModuleInfo(moduleName, modulePath, filesCreated).right()
    }

    /**
     * Creates standard Spring Boot directory structure.
     */
    private fun createDirectoryStructure(
        modulePath: Path,
        context: ModuleContext,
    ): Either<GeneratorError, Unit> =
        try {
            val packagePath = "src/main/kotlin/com/axians/eaf/products/${context.packageName}"
            val testPackagePath = "src/test/kotlin/com/axians/eaf/products/${context.packageName}"
            val integrationPackagePath =
                "src/integration-test/kotlin/com/axians/eaf/products/${context.packageName}"
            val konsistPackagePath = "src/konsist-test/kotlin/com/axians/eaf/products/${context.packageName}"

            // Main source directories
            Files.createDirectories(modulePath.resolve(packagePath))
            Files.createDirectories(modulePath.resolve("src/main/resources"))

            // Test source directories
            Files.createDirectories(modulePath.resolve(testPackagePath))
            Files.createDirectories(modulePath.resolve("src/test/resources"))

            // Integration test directories
            Files.createDirectories(modulePath.resolve(integrationPackagePath))

            // Konsist test directories
            Files.createDirectories(modulePath.resolve(konsistPackagePath))

            Unit.right()
        } catch (ex: IOException) {
            GeneratorError.FileSystemError("Failed to create module directory structure", ex).left()
        }

    /**
     * Generates build.gradle.kts from template.
     */
    private fun generateBuildGradle(
        modulePath: Path,
        context: ModuleContext,
    ): Either<GeneratorError, Path> =
        try {
            val content = templateEngine.render("module-build.gradle.kts.mustache", context.toMap())
            val targetPath = modulePath.resolve("build.gradle.kts")
            targetPath.writeText(content)
            targetPath.right()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Legitimate use: Translating any template/IO exception to GeneratorError
            GeneratorError.TemplateError("module-build.gradle.kts.mustache", ex).left()
        }

    /**
     * Generates ModuleMetadata.kt from template.
     */
    private fun generateModuleMetadata(
        modulePath: Path,
        context: ModuleContext,
    ): Either<GeneratorError, Path> =
        try {
            val content = templateEngine.render("ModuleMetadata.kt.mustache", context.toMap())
            val packagePath = "com/axians/eaf/products/${context.packageName}"
            val targetPath =
                modulePath.resolve("src/main/kotlin/$packagePath/${context.className}Module.kt")
            targetPath.writeText(content)
            targetPath.right()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Legitimate use: Translating any template/IO exception to GeneratorError
            GeneratorError.TemplateError("ModuleMetadata.kt.mustache", ex).left()
        }

    /**
     * Generates Spring Boot application main class from template.
     */
    private fun generateApplicationClass(
        modulePath: Path,
        context: ModuleContext,
    ): Either<GeneratorError, Path> =
        try {
            val content = templateEngine.render("Application.kt.mustache", context.toMap())
            val packagePath = "com/axians/eaf/products/${context.packageName}"
            val targetPath =
                modulePath.resolve("src/main/kotlin/$packagePath/${context.className}Application.kt")
            targetPath.writeText(content)
            targetPath.right()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Legitimate use: Translating any template/IO exception to GeneratorError
            GeneratorError.TemplateError("Application.kt.mustache", ex).left()
        }

    /**
     * Generates application.yml from template.
     */
    private fun generateApplicationYml(
        modulePath: Path,
        context: ModuleContext,
    ): Either<GeneratorError, Path> =
        try {
            val content = templateEngine.render("application.yml.mustache", context.toMap())
            val targetPath = modulePath.resolve("src/main/resources/application.yml")
            targetPath.writeText(content)
            targetPath.right()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Legitimate use: Translating any template/IO exception to GeneratorError
            GeneratorError.TemplateError("application.yml.mustache", ex).left()
        }

    /**
     * Generates application-test.yml from template.
     */
    private fun generateApplicationTestYml(
        modulePath: Path,
        context: ModuleContext,
    ): Either<GeneratorError, Path> =
        try {
            val content = templateEngine.render("application-test.yml.mustache", context.toMap())
            val targetPath = modulePath.resolve("src/test/resources/application-test.yml")
            targetPath.writeText(content)
            targetPath.right()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Legitimate use: Translating any template/IO exception to GeneratorError
            GeneratorError.TemplateError("application-test.yml.mustache", ex).left()
        }

    /**
     * Updates settings.gradle.kts to register the new module.
     *
     * Strategy (TECH-004 mitigation):
     * 1. Read entire file
     * 2. Locate "// Product modules" section
     * 3. Insert new include() in alphabetical order
     * 4. Preserve all whitespace and comments
     * 5. Write back atomically
     *
     * Complexity Note: File parsing with multiple validation checks and early returns.
     * Suppressions justified by settings.gradle.kts parsing requirements.
     */
    @Suppress("ReturnCount", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
    private fun updateSettingsGradle(
        moduleName: String,
        directory: String,
    ): Either<GeneratorError, Unit> {
        return try {
            val settingsFile = Paths.get("settings.gradle.kts")
            if (!settingsFile.exists()) {
                return GeneratorError
                    .FileSystemError(
                        "settings.gradle.kts not found in project root",
                        IOException("File not found"),
                    ).left()
            }

            val originalContent = Files.readString(settingsFile)
            val newInclude = "include(\":$directory:$moduleName\")"

            // Find product modules section
            val productSectionMarker = "// Product modules"
            if (!originalContent.contains(productSectionMarker)) {
                return GeneratorError
                    .FileSystemError(
                        "Could not find '// Product modules' section in settings.gradle.kts",
                        IOException("Section not found"),
                    ).left()
            }

            // Insert new module in alphabetical order within product section
            val lines = originalContent.lines().toMutableList()
            val productSectionIndex =
                lines.indexOfFirst { it.trim() == productSectionMarker }

            // Find insertion point (after product section marker, before next section or end)
            var insertIndex = productSectionIndex + 1
            while (insertIndex < lines.size) {
                val line = lines[insertIndex].trim()
                if (line.startsWith("//") && !line.startsWith("include")) {
                    // Hit next section marker, insert before it
                    break
                }
                if (line.startsWith("include(\":$directory:")) {
                    // Found existing product module, check alphabetical order
                    if (line > newInclude) {
                        // Insert before this line
                        break
                    }
                }
                insertIndex++
            }

            lines.add(insertIndex, newInclude)
            val updatedContent = lines.joinToString("\n")

            Files.writeString(settingsFile, updatedContent)
            Unit.right()
        } catch (ex: IOException) {
            GeneratorError.FileSystemError("Failed to update settings.gradle.kts", ex).left()
        }
    }
}
