package com.axians.eaf.framework.security.tenant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

class TenantContextTest :
    BehaviorSpec({

        afterTest {
            // Clean up security context after each test
            SecurityContextHolder.clearContext()
        }

        given("an authenticated JWT with tenant_id claim") {
            `when`("getCurrentTenantId is called") {
                then("should return the tenant ID from JWT claims") {
                    // Create a JWT with tenant_id claim
                    val jwt =
                        Jwt
                            .withTokenValue("dummy-token")
                            .header("alg", "RS256")
                            .claim("sub", "user123")
                            .claim("tenant_id", "test-tenant-123")
                            .issuedAt(Instant.now())
                            .expiresAt(Instant.now().plusSeconds(3600))
                            .build()

                    // Set up Spring Security context
                    val authentication = JwtAuthenticationToken(jwt)
                    SecurityContextHolder.getContext().authentication = authentication

                    // Test tenant extraction
                    val tenantId = TenantContext.getCurrentTenantId()
                    tenantId shouldBe "test-tenant-123"
                }
            }
        }

        given("an authenticated JWT without tenant_id claim") {
            `when`("getCurrentTenantId is called") {
                then("should throw IllegalStateException") {
                    // Create a JWT without tenant_id claim
                    val jwt =
                        Jwt
                            .withTokenValue("dummy-token")
                            .header("alg", "RS256")
                            .claim("sub", "user123")
                            .issuedAt(Instant.now())
                            .expiresAt(Instant.now().plusSeconds(3600))
                            .build()

                    // Set up Spring Security context
                    val authentication = JwtAuthenticationToken(jwt)
                    SecurityContextHolder.getContext().authentication = authentication

                    // Test should throw exception
                    shouldThrow<IllegalStateException> {
                        TenantContext.getCurrentTenantId()
                    }.message shouldBe "Missing or invalid tenant_id claim in JWT token"
                }
            }
        }

        given("no authentication context") {
            `when`("getCurrentTenantId is called") {
                then("should throw IllegalStateException") {
                    // Ensure no authentication context
                    SecurityContextHolder.clearContext()

                    // Test should throw exception
                    shouldThrow<IllegalStateException> {
                        TenantContext.getCurrentTenantId()
                    }.message shouldBe "No authentication context found"
                }
            }
        }

        given("non-JWT authentication") {
            `when`("getCurrentTenantId is called") {
                then("should throw IllegalStateException") {
                    // Set up non-JWT authentication
                    val authentication =
                        org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "user",
                            "password",
                        )
                    SecurityContextHolder.getContext().authentication = authentication

                    // Test should throw exception
                    shouldThrow<IllegalStateException> {
                        TenantContext.getCurrentTenantId()
                    }.message shouldBe "Authentication is not JWT-based"
                }
            }
        }
    })
