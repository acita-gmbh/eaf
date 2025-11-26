package de.acci.eaf.core.error

/**
 * Thrown when an identifier string cannot be parsed into a UUID.
 * Keeps type context to simplify debugging and audit trails.
 */
public class InvalidIdentifierFormatException(
    public val identifierType: String,
    public val raw: String,
    cause: Throwable
) : IllegalArgumentException("Invalid $identifierType format: '$raw'", cause)
