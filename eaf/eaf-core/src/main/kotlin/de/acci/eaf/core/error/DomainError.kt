package de.acci.eaf.core.error

/**
 * Canonical domain error envelope for EAF modules.
 * Keep contextual fields to support observability and audit requirements.
 */
public sealed class DomainError {
    /** Input failed validation with specific field and message. */
    public data class ValidationFailed(val field: String, val message: String) : DomainError()

    /** Requested resource of [type] with [id] was not found. */
    public data class ResourceNotFound(val type: String, val id: String) : DomainError()

    /** Illegal transition attempted from [from] to [to] via [action]. */
    public data class InvalidStateTransition(val from: String, val to: String, val action: String) : DomainError()

    /** Operation exceeded allowed quota. */
    public data class QuotaExceeded(val current: Int, val max: Int) : DomainError()

    /**
     * Infrastructure or platform issue; use a string to stay dependency-free.
     * Consider embedding correlation IDs or stack snippets in [cause] for audit.
     */
    public data class InfrastructureError(val cause: String) : DomainError()
}
