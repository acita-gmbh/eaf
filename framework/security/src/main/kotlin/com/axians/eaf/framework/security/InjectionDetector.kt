package com.axians.eaf.framework.security

import com.axians.eaf.framework.core.exceptions.EafException
import org.springframework.stereotype.Component

@Component
class InjectionDetector {
    companion object {
        // SQL Injection patterns (refined to reduce false positives)
        private val sqlPatterns =
            listOf(
                Regex("(?i).*(--).*"), // SQL comment
                Regex("(?i).*(;\\s*(DROP|DELETE|UPDATE|INSERT|ALTER|CREATE|TRUNCATE)).*"), // Dangerous SQL
                Regex("(?i).*(UNION\\s+SELECT).*"), // UNION injection
                Regex("(?i).*(OR\\s+['\"]?1['\"]?\\s*=\\s*['\"]?1).*"), // Tautology injection
            )

        // XSS patterns
        private val xssPatterns =
            listOf(
                Regex("(?i).*(<script|javascript:|onerror=|onload=).*"),
            )

        // JNDI Injection patterns
        private val jndiPatterns =
            listOf(
                Regex("(?i).*(jndi:|ldap:|rmi:).*"),
            )

        // ✅ CRITICAL: Expression Injection (from architecture.md)
        private val expressionInjectionPatterns =
            listOf(
                Regex(".*\\$\\{.*}"), // ${...} patterns (Log4Shell-style)
            )

        // ✅ CRITICAL: Path Traversal (from architecture.md)
        private val pathTraversalPatterns =
            listOf(
                Regex(".*(\\.\\.[\\\\/]).*"), // ../ or ..\
            )

        // All patterns combined (compiled once for performance)
        private val allPatterns =
            sqlPatterns + xssPatterns + jndiPatterns +
                expressionInjectionPatterns + pathTraversalPatterns
    }

    fun scan(claims: Map<String, Any>) {
        claims.forEach { (key, value) ->
            if (value is String) {
                detectInjection(value, key)
            }
        }
    }

    private fun detectInjection(
        value: String,
        claimName: String,
    ) {
        allPatterns.forEach { pattern ->
            if (pattern.matches(value)) {
                throw InjectionDetectedException(
                    claim = claimName,
                    detectedPattern = pattern.pattern,
                    value = value,
                )
            }
        }
    }
}

/**
 * Thrown when potential injection attack is detected in JWT claim.
 * Extends EafException per Zero-Tolerance Policy #2.
 */
class InjectionDetectedException(
    val claim: String,
    val detectedPattern: String,
    val value: String,
) : EafException("Potential injection detected in claim '$claim': pattern=$detectedPattern")
