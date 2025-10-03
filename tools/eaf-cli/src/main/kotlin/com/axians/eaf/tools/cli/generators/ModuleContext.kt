package com.axians.eaf.tools.cli.generators

import java.time.Instant

/**
 * Context data for module template rendering.
 *
 * Transforms user input (kebab-case module name) into all required template variables:
 * - moduleName: Original kebab-case (e.g., "my-licensing-server")
 * - packageName: Sanitized for Java packages (e.g., "mylicensingserver")
 * - className: PascalCase for class names (e.g., "MylicensingserverModule")
 * - displayName: Human-readable (e.g., "My Licensing Server")
 */
data class ModuleContext(
    val moduleName: String,
    val packageName: String,
    val className: String,
    val displayName: String,
    val description: String,
    val serverPort: Int = 8080,
    val timestamp: String = Instant.now().toString(),
    val author: String = System.getProperty("user.name", "developer"),
) {
    /**
     * Converts context to Map for Mustache template rendering.
     */
    fun toMap(): Map<String, Any> =
        mapOf(
            "moduleName" to moduleName,
            "packageName" to packageName,
            "className" to className,
            "displayName" to displayName,
            "description" to description,
            "serverPort" to serverPort,
            "timestamp" to timestamp,
            "author" to author,
        )

    companion object {
        /**
         * Creates ModuleContext from kebab-case module name.
         *
         * Naming transformations:
         * - Input: "my-licensing-server" (kebab-case)
         * - Package: "mylicensingserver" (remove hyphens, lowercase)
         * - Class: "MylicensingserverModule" (PascalCase)
         * - Display: "My Licensing Server" (human-readable)
         */
        fun fromModuleName(
            moduleName: String,
            description: String? = null,
        ): ModuleContext {
            // Remove hyphens for package name
            val packageName = moduleName.replace("-", "")

            // PascalCase for class name
            val className = packageName.replaceFirstChar { it.uppercaseChar() }

            // Human-readable display name
            val displayName =
                moduleName
                    .split("-")
                    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercaseChar() } }

            return ModuleContext(
                moduleName = moduleName,
                packageName = packageName,
                className = className,
                displayName = displayName,
                description = description ?: "EAF $displayName Module",
            )
        }
    }
}
