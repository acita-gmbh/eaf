package com.axians.eaf.framework.security.dto

/**
 * Response data classes for secured endpoint API responses.
 * Provides type safety and eliminates need for unchecked casts.
 */
data class SecureEndpointResponse(
    val message: String,
    val user: UserClaimsDto,
    val timestamp: Long,
)

/**
 * User claims extracted from JWT token.
 */
data class UserClaimsDto(
    val id: String?,
    val tenantId: String?,
    val roles: List<String>,
    val issuer: String?,
    val audience: List<String>?,
    val issuedAt: Long?,
    val expiresAt: Long?,
)
