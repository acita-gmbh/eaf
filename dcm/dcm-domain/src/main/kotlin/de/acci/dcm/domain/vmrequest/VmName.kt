package de.acci.dcm.domain.vmrequest

/**
 * Value object representing a valid VM name.
 *
 * Validation rules:
 * - 3-63 characters total
 * - Lowercase letters, numbers, and hyphens only
 * - Must start with a letter or number
 * - Must end with a letter or number
 * - Cannot have consecutive hyphens
 *
 * These rules align with DNS hostname standards and cloud provider naming conventions.
 */
@JvmInline
public value class VmName private constructor(public val value: String) {
    public companion object {
        /**
         * Regex pattern for VM name validation.
         * Matches: lowercase alphanumeric, hyphens in middle, 3-63 chars total.
         */
        private val VM_NAME_REGEX = Regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$")

        /**
         * Creates a validated VmName instance.
         *
         * @param name The VM name to validate
         * @return Result containing the VmName or a validation error
         */
        public fun create(name: String): Result<VmName> {
            val trimmed = name.trim()
            return when {
                trimmed.length < 3 -> Result.failure(
                    VmNameValidationException("VM name must be at least 3 characters long")
                )
                trimmed.length > 63 -> Result.failure(
                    VmNameValidationException("VM name must not exceed 63 characters")
                )
                trimmed.contains("--") -> Result.failure(
                    VmNameValidationException("VM name cannot contain consecutive hyphens")
                )
                !trimmed.matches(VM_NAME_REGEX) -> Result.failure(
                    VmNameValidationException(
                        "VM name must contain only lowercase letters, numbers, and hyphens, " +
                            "and must start and end with a letter or number"
                    )
                )
                else -> Result.success(VmName(trimmed))
            }
        }

        /**
         * Creates a VmName, throwing an exception if validation fails.
         *
         * @param name The VM name to validate
         * @return The validated VmName
         * @throws VmNameValidationException if validation fails
         */
        public fun of(name: String): VmName = create(name).getOrThrow()
    }
}

/**
 * Exception thrown when VM name validation fails.
 */
public class VmNameValidationException(message: String) : IllegalArgumentException(message)
