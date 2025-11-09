package com.axians.eaf.framework.security.validation

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

/**
 * JWT Algorithm Validator - Enforces RS256 signature algorithm.
 *
 * Layer 3 (Architecture): Algorithm Validation
 * - Only RS256 (asymmetric) allowed
 * - Rejects HS256 (symmetric) to prevent algorithm confusion attacks
 * - Prevents downgrade attacks where attacker uses HMAC with public key
 *
 * Security Rationale:
 * - RS256 requires private key for signing (only Keycloak has it)
 * - HS256 uses shared secret (public key could be misused as secret)
 * - Algorithm confusion: Attacker signs token with HS256 using server's public key
 *
 * Story 3.4: JWT Format and Signature Validation (Layers 1-2)
 */
class JwtAlgorithmValidator : OAuth2TokenValidator<Jwt> {
    companion object {
        private const val REQUIRED_ALGORITHM = "RS256"
        private val FORBIDDEN_ALGORITHMS = setOf("HS256", "HS384", "HS512", "none")
    }

    /**
     * Validates the JWT `alg` header, enforcing that it is present and equals `RS256`.
     *
     * Returns a failure result when the `alg` header is missing, equals a forbidden algorithm
     * (`HS256`, `HS384`, `HS512`, `none`), or is any value other than `RS256`; returns success otherwise.
     *
     * @param token The JWT whose `alg` header will be validated.
     * @return An OAuth2TokenValidatorResult representing validation failure with an `invalid_token` error
     *         for missing/forbidden/wrong algorithms, or success when the algorithm is `RS256`.
     */
    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val algorithm = token.headers["alg"] as? String

        val error =
            when {
                algorithm == null ->
                    OAuth2Error(
                        "invalid_token",
                        "JWT algorithm header missing",
                        null,
                    )

                algorithm in FORBIDDEN_ALGORITHMS ->
                    OAuth2Error(
                        "invalid_token",
                        "JWT algorithm '$algorithm' not allowed (security risk: algorithm confusion attack)",
                        null,
                    )

                algorithm != REQUIRED_ALGORITHM ->
                    OAuth2Error(
                        "invalid_token",
                        "JWT algorithm must be RS256 (actual: $algorithm)",
                        null,
                    )

                else -> null
            }

        return error?.let { OAuth2TokenValidatorResult.failure(it) }
            ?: OAuth2TokenValidatorResult.success()
    }
}