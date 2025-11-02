package com.axians.eaf.framework.core.exceptions

/**
 * Exception thrown when tenant isolation is violated.
 *
 * This exception indicates a critical security violation where data
 * from one tenant is being accessed by another tenant, or tenant
 * context is missing when required.
 *
 * Example:
 * ```kotlin
 * if (command.tenantId != currentTenantId) {
 *     throw TenantIsolationException("Tenant context mismatch")
 * }
 * ```
 *
 * @param message Description of the tenant isolation violation
 * @param cause The underlying cause (optional)
 */
class TenantIsolationException(
    message: String,
    cause: Throwable? = null,
) : EafException(message, cause)
