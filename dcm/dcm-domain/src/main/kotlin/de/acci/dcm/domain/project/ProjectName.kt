package de.acci.dcm.domain.project

/**
 * Value object representing a validated project name.
 *
 * Validation rules:
 * - 3-100 characters total
 * - Must start with a letter or number
 * - Can contain letters, numbers, spaces, hyphens, and underscores
 *
 * Provides a [normalized] property for case-insensitive uniqueness comparison.
 */
@JvmInline
public value class ProjectName private constructor(public val value: String) {

    /**
     * Lowercase version of the name for case-insensitive comparison.
     * Used for enforcing uniqueness: "Alpha" == "ALPHA" == "alpha"
     */
    public val normalized: String
        get() = value.lowercase()

    public companion object {
        /**
         * Regex pattern for project name validation.
         * - Must start with alphanumeric
         * - Can contain alphanumeric, spaces, hyphens, underscores
         */
        private val PROJECT_NAME_REGEX = Regex("^[a-zA-Z0-9][a-zA-Z0-9\\s\\-_]*$")

        private const val MIN_LENGTH = 3
        private const val MAX_LENGTH = 100

        /**
         * Creates a validated ProjectName instance.
         *
         * @param name The project name to validate
         * @return Validated ProjectName
         * @throws ProjectNameValidationException if validation fails
         */
        public fun of(name: String): ProjectName {
            val trimmed = name.trim()

            if (trimmed.isBlank()) {
                throw ProjectNameValidationException("Project name cannot be blank")
            }

            if (trimmed.length !in MIN_LENGTH..MAX_LENGTH) {
                throw ProjectNameValidationException(
                    "Project name must be 3-100 characters, got ${trimmed.length}"
                )
            }

            if (!trimmed.matches(PROJECT_NAME_REGEX)) {
                throw ProjectNameValidationException(
                    "Project name must start with alphanumeric and contain only " +
                        "alphanumeric, spaces, hyphens, underscores"
                )
            }

            return ProjectName(trimmed)
        }
    }
}

/**
 * Exception thrown when project name validation fails.
 */
public class ProjectNameValidationException(message: String) : IllegalArgumentException(message)
