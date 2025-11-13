package com.axians.eaf.framework.security.validation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * Unit tests for JwtClaimSchemaValidator (Layer 4: Claim Schema Validation).
 *
 * Tests enforce that core required claims (sub, iss, exp, iat) are present
 * and that critical claims (sub, tenant_id) are non-blank when present.
 *
 * Note: aud, tenant_id, and roles are optional (validated by other layers):
 * - aud: Validated by Spring Security's default audience validator (Epic 3.6)
 * - tenant_id: Optional until Epic 4 (multi-tenancy), must be non-blank if present
 * - roles: Optional until Epic 3.6+ (role validation layer)
 *
 * Story 3.5: JWT Claims Schema and Time-Based Validation (Layers 3-5)
 */
class JwtClaimSchemaValidatorTest :
    FunSpec({
        val validator = JwtClaimSchemaValidator(SimpleMeterRegistry())

        test("valid JWT with all required claims should pass validation") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "sub" to "user-123",
                            "iss" to "https://keycloak.example.com/realms/eaf",
                            "aud" to "eaf-api",
                            "exp" to Instant.now().plusSeconds(3600),
                            "iat" to Instant.now(),
                            "tenant_id" to "tenant-a",
                            "roles" to listOf("WIDGET_ADMIN"),
                        ),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe false
        }

        test("JWT missing 'sub' claim should fail validation") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "iss" to "https://keycloak.example.com/realms/eaf",
                            "aud" to "eaf-api",
                            "exp" to Instant.now().plusSeconds(3600),
                            "iat" to Instant.now(),
                            "tenant_id" to "tenant-a",
                            "roles" to listOf("WIDGET_ADMIN"),
                        ),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe true
            result.errors.first().description shouldBe "JWT missing required claims: sub"
        }

        test("JWT missing optional 'tenant_id' claim should pass validation") {
            // tenant_id is optional until Epic 4 (multi-tenancy implementation)
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "sub" to "user-123",
                            "iss" to "https://keycloak.example.com/realms/eaf",
                            "aud" to "eaf-api",
                            "exp" to Instant.now().plusSeconds(3600),
                            "iat" to Instant.now(),
                            // tenant_id intentionally omitted
                        ),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe false
        }

        test("JWT missing optional 'roles' claim should pass validation") {
            // roles is optional until Epic 3.6+ (role validation layer)
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "sub" to "user-123",
                            "iss" to "https://keycloak.example.com/realms/eaf",
                            "aud" to "eaf-api",
                            "exp" to Instant.now().plusSeconds(3600),
                            "iat" to Instant.now(),
                            // roles intentionally omitted
                        ),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe false
        }

        test("JWT missing required claims should list all missing claims in sorted order") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "sub" to "user-123",
                            "aud" to "eaf-api",
                            "exp" to Instant.now().plusSeconds(3600),
                        ),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe true
            val description = result.errors.first().description
            // Only 'iat' and 'iss' are missing and enforced as required claims in this test.
            // The next test covers the jti requirement.
            // tenant_id and roles are optional until Epic 4 and Epic 3.6+
            description shouldBe "JWT missing required claims: iat, iss"
        }

        test("JWT missing jti claim should fail validation") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "sub" to "user-123",
                            "iss" to "https://keycloak.example.com/realms/eaf",
                            "aud" to "eaf-api",
                            "exp" to Instant.now().plusSeconds(3600),
                            "iat" to Instant.now(),
                        ),
                    includeJti = false,
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe true
            result.errors.first().description shouldBe "JWT missing required claims: jti"
        }

        test("JWT with blank tenant_id should fail validation") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "sub" to "user-123",
                            "iss" to "https://keycloak.example.com/realms/eaf",
                            "aud" to "eaf-api",
                            "exp" to Instant.now().plusSeconds(3600),
                            "iat" to Instant.now(),
                            "tenant_id" to "", // Blank tenant_id (multi-tenancy bypass attempt)
                            "roles" to listOf("WIDGET_ADMIN"),
                        ),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe true
            result.errors.first().description shouldBe "JWT has invalid claim values: tenant_id (blank)"
        }

        test("JWT with blank sub should fail validation") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "sub" to "", // Blank subject (user identity bypass attempt)
                            "iss" to "https://keycloak.example.com/realms/eaf",
                            "aud" to "eaf-api",
                            "exp" to Instant.now().plusSeconds(3600),
                            "iat" to Instant.now(),
                            "tenant_id" to "tenant-a",
                            "roles" to listOf("WIDGET_ADMIN"),
                        ),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe true
            result.errors.first().description shouldBe "JWT has invalid claim values: sub (blank)"
        }

        test("JWT with both blank tenant_id and sub should list both invalid claims") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "sub" to "",
                            "iss" to "https://keycloak.example.com/realms/eaf",
                            "aud" to "eaf-api",
                            "exp" to Instant.now().plusSeconds(3600),
                            "iat" to Instant.now(),
                            "tenant_id" to "",
                            "roles" to listOf("WIDGET_ADMIN"),
                        ),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe true
            result.errors.first().description shouldBe "JWT has invalid claim values: sub (blank), tenant_id (blank)"
        }

        test("JWT with non-critical empty claims should pass validation") {
            val jwt =
                createJwt(
                    claims =
                        mapOf(
                            "sub" to "user-123",
                            "iss" to "", // Non-critical claim (validated separately by issuer validator)
                            "aud" to "", // Non-critical claim (validated separately by audience validator)
                            "exp" to Instant.now().plusSeconds(3600),
                            "iat" to Instant.now(),
                            "tenant_id" to "tenant-a",
                            "roles" to emptyList<String>(), // Empty list is valid (no-roles scenario)
                        ),
                )

            val result = validator.validate(jwt)

            result.hasErrors() shouldBe false
        }
    })

/**
 * Helper function to create a test JWT with specified claims.
 */
private fun createJwt(
    claims: Map<String, Any>,
    includeJti: Boolean = true,
): Jwt {
    val headers = mapOf("alg" to "RS256", "typ" to "JWT")
    val tokenValue = "mock-token-value"

    val exp = claims["exp"] as? Instant
    val iat = claims["iat"] as? Instant

    return Jwt
        .withTokenValue(tokenValue)
        .headers { h -> h.putAll(headers) }
        .claims { c ->
            c.putAll(claims)
            if (includeJti) {
                c.putIfAbsent("jti", "test-jti")
            }
        }.apply {
            exp?.let { expiresAt(it) }
            iat?.let { issuedAt(it) }
        }.build()
}
