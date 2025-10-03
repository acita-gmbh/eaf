package com.axians.eaf.tools.cli.generators

import java.nio.file.Path

/**
 * Sealed class representing errors that can occur during code generation.
 *
 * Following EAF coding standards: NO generic exceptions, always use specific types.
 */
sealed class GeneratorError {
    /**
     * Error when attempting to create a module that already exists.
     */
    data class ModuleAlreadyExists(
        val name: String,
        val path: Path,
    ) : GeneratorError()

    /**
     * Error during file system operations (directory creation, file writes).
     */
    data class FileSystemError(
        val message: String,
        val cause: Throwable,
    ) : GeneratorError()

    /**
     * Error during template rendering (missing template, rendering failures).
     */
    data class TemplateError(
        val template: String,
        val cause: Throwable,
    ) : GeneratorError()

    /**
     * Error when target module is not found.
     */
    data class ModuleNotFound(
        val moduleName: String,
    ) : GeneratorError()

    /**
     * Error when aggregate already exists in module.
     */
    data class AggregateAlreadyExists(
        val aggregateName: String,
        val moduleName: String,
    ) : GeneratorError()

    /**
     * Error when field type is not supported.
     */
    data class UnsupportedType(
        val type: String,
        val supportedTypes: Set<String>,
    ) : GeneratorError()

    /**
     * Error when regex pattern is invalid.
     */
    data class InvalidRegexPattern(
        val pattern: String,
        val error: String?,
    ) : GeneratorError()

    /**
     * Error when regex pattern is potentially unsafe (ReDoS).
     */
    data class UnsafeRegexPattern(
        val pattern: String,
    ) : GeneratorError()
}
