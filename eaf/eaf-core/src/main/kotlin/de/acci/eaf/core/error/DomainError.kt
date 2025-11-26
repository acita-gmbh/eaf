package de.acci.eaf.core.error

/**
 * Canonical domain error envelope for EAF modules.
 * Keep contextual fields to support observability and audit requirements.
 */
public sealed class DomainError {
    /** Input failed validation with specific field and message. */
    public data class ValidationFailed(public val field: String, public val message: String) : DomainError()

    /** Requested resource of [type] with [id] was not found. */
    public data class ResourceNotFound(public val type: String, public val id: String) : DomainError()

    /** Illegal transition attempted from [from] to [to] via [action]. */
    public data class InvalidStateTransition(public val from: String, public val to: String, public val action: String) : DomainError()

    /** Operation exceeded allowed quota. */
    public data class QuotaExceeded(public val current: Int, public val max: Int) : DomainError()

    /**
     * Infrastructure or platform issue; use a string to stay dependency-free.
     * Consider embedding correlation IDs or stack snippets in [cause] for audit.
     */
    public data class InfrastructureError(public val cause: String) : DomainError()
}
