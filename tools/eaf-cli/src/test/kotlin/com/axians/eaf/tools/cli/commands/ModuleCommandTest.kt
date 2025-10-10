package com.axians.eaf.tools.cli.commands

import arrow.core.Either
import com.axians.eaf.tools.cli.generators.GeneratorError
import com.axians.eaf.tools.cli.generators.ModuleGenerator
import com.axians.eaf.tools.cli.templates.TemplateEngine
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.file.Files
import java.util.Comparator

/**
 * Security and validation tests for ModuleCommand.
 *
 * These tests exercise the input validation logic directly via reflection
 * to avoid side effects from the CLI entrypoint (which terminates the JVM).
 * This keeps coverage on the actual validation methods while remaining
 * faithful to the SEC-002 mitigation requirements.
 */
class ModuleCommandTest :
    FunSpec({
        test("7.2-UNIT-006: SECURITY - Malicious module names should be rejected") {
            val command = ModuleCommand()
            val validateModuleName = command.method("validateModuleName")

            shouldThrow<IllegalArgumentException> { validateModuleName.invokeWith(command, "../../etc/passwd") }

            shouldThrow<IllegalArgumentException> {
                validateModuleName.invokeWith(command, "<script>alert('xss')</script>")
            }

            shouldThrow<IllegalArgumentException> { validateModuleName.invokeWith(command, "module; rm -rf /") }

            shouldThrow<IllegalArgumentException> { validateModuleName.invokeWith(command, "module\u0000.txt") }

            shouldThrow<IllegalArgumentException> { validateModuleName.invokeWith(command, "\u202Emodule") }
        }

        test("7.2-UNIT-002: Invalid module name should produce validation error") {
            val command = ModuleCommand()
            val validateModuleName = command.method("validateModuleName")

            val exception =
                shouldThrow<IllegalArgumentException> {
                    validateModuleName.invokeWith(command, "Test_Module!")
                }
            val expectedMessage =
                "Invalid module name: 'Test_Module!'. Must be lowercase alphanumeric with hyphens (kebab-case)."
            exception.message shouldBe expectedMessage
        }

        test("7.2-UNIT-003: Existing module directory should produce error") {
            val tempDir = Files.createTempDirectory("existing-module-test-")
            val moduleName = "test-existing-module"
            try {
                val existingModulePath = tempDir.resolve(moduleName)
                Files.createDirectories(existingModulePath)

                val generator = ModuleGenerator(TemplateEngine())
                val result = generator.generateModule(moduleName, tempDir.toString(), updateSettings = false)

                val leftResult = result.shouldBeInstanceOf<Either.Left<GeneratorError.ModuleAlreadyExists>>()
                val error = leftResult.value
                error.name shouldBe moduleName
                error.path shouldBe existingModulePath
            } finally {
                Files.walk(tempDir).use { stream ->
                    stream
                        .sorted(Comparator.reverseOrder())
                        .forEach { Files.deleteIfExists(it) }
                }
            }
        }

        test("7.2-UNIT-010: SECURITY - Malicious directory parameters should be rejected") {
            val command = ModuleCommand()
            val validateDirectory = command.method("validateDirectory")

            shouldThrow<IllegalArgumentException> { validateDirectory.invokeWith(command, "/tmp") }

            shouldThrow<IllegalArgumentException> { validateDirectory.invokeWith(command, "../../etc") }

            shouldThrow<IllegalArgumentException> { validateDirectory.invokeWith(command, ".") }

            shouldThrow<IllegalArgumentException> { validateDirectory.invokeWith(command, "malicious") }
        }
    })

private fun ModuleCommand.method(name: String): Method =
    javaClass.getDeclaredMethod(name, String::class.java).apply {
        isAccessible = true
    }

private fun Method.invokeWith(
    target: Any,
    argument: String,
) {
    try {
        invoke(target, argument)
    } catch (ex: InvocationTargetException) {
        throw ex.targetException
    }
}
