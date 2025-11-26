package de.acci.eaf.auth

/**
 * Exception thrown when token validation fails.
 *
 * This is thrown by [IdentityProvider.validateToken] when the provided
 * token is invalid, expired, malformed, or cannot be verified.
 *
 * @property reason The specific reason for token rejection.
 * @property message Detailed error message.
 * @property cause The underlying cause (if any).
 */
public class InvalidTokenException(
    public val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    /**
     * Enumeration of token validation failure reasons.
     */
    public enum class Reason {
        /** Token signature verification failed. */
        INVALID_SIGNATURE,

        /** Token has expired (exp claim in the past). */
        EXPIRED,

        /** Token is not yet valid (nbf claim in the future). */
        NOT_YET_VALID,

        /** Token issuer does not match expected issuer. */
        INVALID_ISSUER,

        /** Token audience does not match expected audience. */
        INVALID_AUDIENCE,

        /** Required claim is missing from token. */
        MISSING_CLAIM,

        /** Token format is invalid (not a valid JWT structure). */
        MALFORMED,

        /** Token type is not supported. */
        UNSUPPORTED_TOKEN_TYPE,

        /** Generic validation failure. */
        VALIDATION_FAILED,
    }

    public companion object {
        public fun invalidSignature(message: String = "Token signature verification failed"): InvalidTokenException =
            InvalidTokenException(reason = Reason.INVALID_SIGNATURE, message = message)

        public fun expired(message: String = "Token has expired"): InvalidTokenException =
            InvalidTokenException(reason = Reason.EXPIRED, message = message)

        public fun notYetValid(message: String = "Token is not yet valid"): InvalidTokenException =
            InvalidTokenException(reason = Reason.NOT_YET_VALID, message = message)

        public fun invalidIssuer(expected: String, actual: String): InvalidTokenException =
            InvalidTokenException(
                reason = Reason.INVALID_ISSUER,
                message = "Token issuer mismatch. Expected: $expected, Actual: $actual",
            )

        public fun invalidAudience(expected: String, actual: String?): InvalidTokenException =
            InvalidTokenException(
                reason = Reason.INVALID_AUDIENCE,
                message = "Token audience mismatch. Expected: $expected, Actual: $actual",
            )

        public fun missingClaim(claimName: String): InvalidTokenException =
            InvalidTokenException(
                reason = Reason.MISSING_CLAIM,
                message = "Required claim '$claimName' is missing from token",
            )

        public fun malformed(message: String, cause: Throwable? = null): InvalidTokenException =
            InvalidTokenException(reason = Reason.MALFORMED, message = message, cause = cause)

        public fun validationFailed(message: String, cause: Throwable? = null): InvalidTokenException =
            InvalidTokenException(reason = Reason.VALIDATION_FAILED, message = message, cause = cause)
    }
}
