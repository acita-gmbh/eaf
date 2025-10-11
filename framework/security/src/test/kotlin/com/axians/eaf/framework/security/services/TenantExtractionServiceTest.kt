package com.axians.eaf.framework.security.services

import com.axians.eaf.framework.security.errors.SecurityError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for TenantExtractionService.
 * Story 8.6: Comprehensive coverage for extracted tenant logic.
 */
class TenantExtractionServiceTest :
    FunSpec({
        val tenantExtractionService = TenantExtractionService()

        // Helper to create JWT with tenant_id claim
        fun createJwtWithTenant(tenantId: String?): Jwt =
            Jwt
                .withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", UUID.randomUUID().toString())
                .claim("tenant_id", tenantId)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()

        context("8.6-UNIT-SVC-100: extractTenantId") {
            test("8.6-UNIT-SVC-101: should return NoAuthentication error for null authentication") {
                val result = tenantExtractionService.extractTenantId(null)
                result.shouldBeLeft(SecurityError.NoAuthentication)
            }

            test("8.6-UNIT-SVC-102: should return NonJwtAuthentication error for non-JWT auth") {
                val nonJwtAuth: Authentication = UsernamePasswordAuthenticationToken("user", "pass")
                val result = tenantExtractionService.extractTenantId(nonJwtAuth)

                val error = result.shouldBeLeft()
                error.shouldBeInstanceOf<SecurityError.NonJwtAuthentication>()
                error.actualType shouldBe "UsernamePasswordAuthenticationToken"
            }

            test("8.6-UNIT-SVC-103: should return MissingTenantClaim when tenant_id is null") {
                val jwt = createJwtWithTenant(null)
                val jwtAuth = JwtAuthenticationToken(jwt)

                val result = tenantExtractionService.extractTenantId(jwtAuth)
                result.shouldBeLeft(SecurityError.MissingTenantClaim)
            }

            test("8.6-UNIT-SVC-104: should return MissingTenantClaim when tenant_id is blank") {
                val jwt = createJwtWithTenant("   ")
                val jwtAuth = JwtAuthenticationToken(jwt)

                val result = tenantExtractionService.extractTenantId(jwtAuth)
                result.shouldBeLeft(SecurityError.MissingTenantClaim)
            }

            test("8.6-UNIT-SVC-105: should return tenant ID when claim is valid") {
                val expectedTenantId = "tenant-abc-123"
                val jwt = createJwtWithTenant(expectedTenantId)
                val jwtAuth = JwtAuthenticationToken(jwt)

                val result = tenantExtractionService.extractTenantId(jwtAuth)
                result.shouldBeRight(expectedTenantId)
            }

            test("8.6-UNIT-SVC-106: should return MissingTenantClaim for empty string tenant_id") {
                val jwt = createJwtWithTenant("")
                val jwtAuth = JwtAuthenticationToken(jwt)

                val result = tenantExtractionService.extractTenantId(jwtAuth)
                result.shouldBeLeft(SecurityError.MissingTenantClaim)
            }
        }

        context("8.6-UNIT-SVC-110: extractTenantIdOrNull") {
            test("8.6-UNIT-SVC-111: should return null for null authentication") {
                val result = tenantExtractionService.extractTenantIdOrNull(null)
                result shouldBe null
            }

            test("8.6-UNIT-SVC-112: should throw IllegalStateException for non-JWT auth") {
                val nonJwtAuth: Authentication = UsernamePasswordAuthenticationToken("user", "pass")

                val exception =
                    shouldThrow<IllegalStateException> {
                        tenantExtractionService.extractTenantIdOrNull(nonJwtAuth)
                    }
                exception.message shouldBe "Authentication is not JWT-based"
            }

            test("8.6-UNIT-SVC-113: should throw IllegalStateException for missing tenant_id") {
                val jwt = createJwtWithTenant(null)
                val jwtAuth = JwtAuthenticationToken(jwt)

                val exception =
                    shouldThrow<IllegalStateException> {
                        tenantExtractionService.extractTenantIdOrNull(jwtAuth)
                    }
                exception.message shouldBe "Missing or invalid tenant_id claim"
            }

            test("8.6-UNIT-SVC-114: should return tenant ID for valid JWT") {
                val expectedTenantId = "tenant-xyz-789"
                val jwt = createJwtWithTenant(expectedTenantId)
                val jwtAuth = JwtAuthenticationToken(jwt)

                val result = tenantExtractionService.extractTenantIdOrNull(jwtAuth)
                result shouldBe expectedTenantId
            }
        }
    })
