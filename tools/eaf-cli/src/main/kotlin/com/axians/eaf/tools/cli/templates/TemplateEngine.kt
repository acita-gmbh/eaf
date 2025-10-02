package com.axians.eaf.tools.cli.templates

import com.github.mustachejava.DefaultMustacheFactory
import java.io.StringWriter

/**
 * Template rendering engine using Mustache for code generation.
 *
 * SECURITY NOTE (SEC-001 Mitigation):
 * All template context values must be validated before rendering to prevent
 * code injection vulnerabilities. Mustache's logic-less design provides
 * protection against arbitrary code execution, but input validation is still
 * required for:
 * - File paths (prevent directory traversal)
 * - Class/package names (validate against naming conventions)
 * - User-provided strings (sanitize special characters if needed)
 *
 * @see <a href="https://github.com/spullara/mustache.java">Mustache.java</a>
 */
class TemplateEngine {
    private val mustacheFactory = DefaultMustacheFactory()

    /**
     * Renders a Mustache template with the provided context.
     *
     * @param templateName Name of the template file (relative to resources/templates/)
     * @param context Map of template variables to replace in the template
     * @return Rendered template as a String
     * @throws TemplateNotFoundException if template file not found
     * @throws TemplateRenderingException if rendering fails
     */
    fun render(
        templateName: String,
        context: Map<String, Any?>,
    ): String =
        try {
            val mustache = mustacheFactory.compile("templates/$templateName")
            val writer = StringWriter()
            mustache.execute(writer, context)
            writer.toString()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Legitimate generic catch: Convert any Mustache exception to our specific exception types
            // for consistent error handling across the CLI. This is pure error translation, not
            // error handling logic. The exception is immediately rethrown in a more specific form.
            when {
                ex::class.simpleName == "MustacheNotFoundException" ||
                    ex.message?.contains("Template", ignoreCase = true) == true ||
                    ex.message?.contains("resource", ignoreCase = true) == true ->
                    throw TemplateNotFoundException("Template not found: $templateName", ex)
                else ->
                    throw TemplateRenderingException("Failed to render template: $templateName", ex)
            }
        }
}

/**
 * Exception thrown when a template file is not found.
 */
class TemplateNotFoundException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Exception thrown when template rendering fails.
 */
class TemplateRenderingException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
