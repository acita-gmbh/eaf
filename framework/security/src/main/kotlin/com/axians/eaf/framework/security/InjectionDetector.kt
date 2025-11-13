package com.axians.eaf.framework.security

import com.axians.eaf.framework.core.common.exceptions.EafException
import org.springframework.stereotype.Component

@Component
class InjectionDetector {
    companion object {
        // SQL Injection patterns (refined to reduce false positives)
        private val sqlPatterns =
            listOf(
                "(?i).*((\\\\-\\\\-)|(;)|(\\\\*)|(<)|(>)|(\\|)|(\\\\^)).*",
                "(?i).*(union|select|insert|update|delete|drop|create|alter).*",
            ).map { it.toRegex() }

        // XSS patterns
        private val xssPatterns =
            listOf(
                "(?i).*(<script|javascript:|onerror=|onload=).*",
            ).map { it.toRegex() }

        // JNDI Injection patterns
        private val jndiPatterns =
            listOf(
                "(?i).*(jndi:|ldap:|rmi:).*",
            ).map { it.toRegex() }

        // ✅ CRITICAL: Expression Injection (from architecture.md)
        private val expressionInjectionPatterns =
            listOf(
                "(?i).*(\\\$\\\\{.*\\\\}).*", // ${...} patterns (Log4Shell-style)
            ).map { it.toRegex() }

        // ✅ CRITICAL: Path Traversal (from architecture.md)
        private val pathTraversalPatterns =
            listOf(
                "(?i).*(\\\\.\\\\.[\\\\\\\\/]).*", // ../ or ..\
            ).map { it.toRegex() }

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
