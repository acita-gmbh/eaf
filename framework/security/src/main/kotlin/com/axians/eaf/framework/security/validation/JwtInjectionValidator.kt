package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.InjectionDetectedException
import com.axians.eaf.framework.security.InjectionDetector
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/**
 * JWT Injection Detection Validator - Scans JWT claims for malicious injection patterns.
 *
 * Layer 10 (Architecture): Injection Detection
 * - Validates all string claims against SQL, XSS, JNDI, Expression Injection, and Path Traversal patterns
 * - Uses InjectionDetector for comprehensive pattern matching
 * - Fails fast on first detected injection pattern
 *
 * Security Rationale:
 * - Prevents SQL injection attacks via JWT claims (e.g., malicious sub claim)
 * - Blocks XSS payloads in JWT claims (e.g., script injection in user names)
 * - Detects JNDI injection attempts (e.g., ldap:// URLs in claims)
 * - Identifies Expression Injection (e.g., ${...} SpEL/JNDI patterns)
 * - Prevents Path Traversal attacks (e.g., ../ directory traversal)
 *
 * Performance: Regex patterns compiled once in companion object for optimal performance.
 * All string claims scanned efficiently with fail-fast behavior.
 *
 * Story 3.8: User Validation and Injection Detection (Layers 9-10)
 */
@Component
class JwtInjectionValidator(
    private val injectionDetector: InjectionDetector,
) : OAuth2TokenValidator<Jwt> {
    /**
     * Validates JWT claims for injection patterns using InjectionDetector.
     *
     * Scans all string claims in the JWT for malicious patterns. Returns failure
     * if any injection pattern is detected, success otherwise.
     *
     * @param token The JWT whose claims will be scanned for injection patterns.
     * @return OAuth2TokenValidatorResult.success() if no injection detected,
     *         or failure with detailed error information.
     */
    override fun validate(token: Jwt): OAuth2TokenValidatorResult =
        try {
            // Scan all claims for injection patterns
            // InjectionDetector will throw InjectionDetectedException if malicious content found
            injectionDetector.scan(token.claims)
            OAuth2TokenValidatorResult.success()
        } catch (ex: InjectionDetectedException) {
            // Handle injection detection - malicious content found in JWT claims
            OAuth2TokenValidatorResult.failure(
                OAuth2Error(
                    "invalid_request",
                    "JWT claim contains potential injection pattern: ${ex.message}",
                    null,
                ),
            )
        }
}
