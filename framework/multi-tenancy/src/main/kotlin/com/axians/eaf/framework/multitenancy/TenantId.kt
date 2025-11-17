package com.axians.eaf.framework.multitenancy

/**
 * Value object representing a validated tenant identifier.
 *
 * Ensures type safety and validation for tenant IDs used throughout
 * the multi-tenancy system.
 *
 * **Validation Rules (per Epic 4 Tech-Spec):**
 * - Not blank
 * - Lowercase alphanumeric characters and hyphens only (a-z, 0-9, -)
 * - Length between 1 and 64 characters
 * - Regex: ^[a-z0-9-]{1,64}$
 *
 * Epic 4, Story 4.1: AC2 - TenantId value object with validation
 *
 * @property value The validated tenant ID string
 * @throws IllegalArgumentException if validation fails
 * @since 1.0.0
 */
data class TenantId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Tenant ID cannot be blank" }
        require(value.matches(Regex("^[a-z0-9-]{1,64}$"))) {
            "Tenant ID must be lowercase alphanumeric with hyphens only (1-64 characters)"
        }
    }
}
