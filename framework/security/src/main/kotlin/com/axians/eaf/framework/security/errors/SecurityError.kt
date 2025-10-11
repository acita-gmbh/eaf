package com.axians.eaf.framework.security.errors

import java.time.Instant

/**
 * Comprehensive security error hierarchy for 10-layer JWT validation system.
 * Each error type corresponds to specific validation layer failures.
 */
sealed class SecurityError(
    override val message: String,
) : Exception(message) {
    // Layer 1: Format Validation Errors
    object EmptyToken : SecurityError("JWT token is empty")

    object InvalidTokenFormat : SecurityError("JWT token format is invalid")

    object TokenTooLarge : SecurityError("JWT token exceeds maximum size")

    object InvalidJwtStructure : SecurityError("JWT token does not have 3 parts")

    // Layer 2: Signature Validation Errors
    data class InvalidSignature(
        val details: String,
    ) : SecurityError("Invalid JWT signature: $details")

    object SignatureMismatch : SecurityError("JWT signature verification failed")

    // Layer 3: Algorithm Validation Errors
    data class UnsupportedAlgorithm(
        val algorithm: String?,
    ) : SecurityError("Unsupported algorithm: ${algorithm ?: "null"}")

    // Layer 4: Claim Schema Validation Errors
    data class MissingClaim(
        val claimName: String,
    ) : SecurityError("Required claim missing: $claimName")

    data class InvalidClaimFormat(
        val claimName: String,
        val value: String,
    ) : SecurityError("Invalid format for claim $claimName: $value")

    data class ClaimExtractionError(
        val details: String,
    ) : SecurityError("Failed to extract claims: $details")

    // Layer 5: Time-based Validation Errors
    data class TokenExpired(
        val expiredAt: Instant,
        val now: Instant,
    ) : SecurityError("Token expired at $expiredAt, current time $now")

    data class FutureToken(
        val issuedAt: Instant,
        val now: Instant,
    ) : SecurityError("Token issued in future: $issuedAt, current time $now")

    data class TokenTooOld(
        val issuedAt: Instant,
        val now: Instant,
    ) : SecurityError("Token too old: issued $issuedAt, current time $now")

    // Layer 6: Issuer/Audience Validation Errors
    data class InvalidIssuer(
        val actual: String,
        val expected: String,
    ) : SecurityError("Invalid issuer: expected $expected, got $actual")

    data class InvalidAudience(
        val actual: String,
        val expected: String,
    ) : SecurityError("Invalid audience: expected $expected, got $actual")

    // Tenant Context Errors (Story 8.6)
    object NoAuthentication : SecurityError("No authentication context present")

    data class NonJwtAuthentication(
        val actualType: String,
    ) : SecurityError("Authentication is not JWT-based: $actualType")

    object MissingTenantClaim : SecurityError("Missing or invalid tenant_id claim")

    // Layer 7: Revocation Check Errors
    data class TokenRevoked(
        val jti: String,
    ) : SecurityError("Token has been revoked: $jti")

    data class RevocationCheckFailed(
        val details: String,
    ) : SecurityError("Failed to check token revocation: $details")

    // Layer 8: Role Validation Errors
    object NoRolesAssigned : SecurityError("No roles assigned to user")

    data class InvalidRoles(
        val invalidRoles: Set<String>,
    ) : SecurityError("Invalid roles: ${invalidRoles.joinToString(", ")}")

    data class RoleValidationError(
        val details: String,
    ) : SecurityError("Role validation failed: $details")

    // Layer 9: User Validation Errors
    data class UserNotFound(
        val userId: String,
    ) : SecurityError("User not found: $userId")

    data class UserInactive(
        val userId: String,
    ) : SecurityError("User is inactive: $userId")

    data class UserLocked(
        val userId: String,
    ) : SecurityError("User is locked: $userId")

    data class UserExpired(
        val userId: String,
    ) : SecurityError("User account expired: $userId")

    data class UserValidationError(
        val details: String,
    ) : SecurityError("User validation failed: $details")

    // Layer 10: Injection Detection Errors
    data class InjectionDetected(
        val patterns: List<String>,
    ) : SecurityError("Malicious patterns detected: ${patterns.joinToString(", ")}")

    data class InjectionCheckFailed(
        val details: String,
    ) : SecurityError("Injection detection failed: $details")
}
