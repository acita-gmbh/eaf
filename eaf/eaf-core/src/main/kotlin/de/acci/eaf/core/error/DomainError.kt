package de.acci.eaf.core.error

/**
 * Canonical domain error envelope for EAF modules.
 * Keep contextual fields to support observability and audit requirements.
 */
public sealed class DomainError {
    public data class ValidationFailed(val field: String, val message: String) : DomainError()
    public data class ResourceNotFound(val type: String, val id: String) : DomainError()
    public data class InvalidStateTransition(val from: String, val action: String) : DomainError()
    public data class QuotaExceeded(val current: Int, val max: Int) : DomainError()
    public data class InfrastructureError(val cause: String) : DomainError()
}
