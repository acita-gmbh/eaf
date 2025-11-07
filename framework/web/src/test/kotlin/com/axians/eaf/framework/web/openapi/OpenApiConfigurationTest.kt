package com.axians.eaf.framework.web.openapi

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.swagger.v3.oas.models.security.SecurityScheme

/**
 * Unit tests for OpenAPI configuration.
 *
 * Verifies:
 * - API metadata (title, version, description)
 * - Bearer JWT security scheme configuration
 * - Security requirement applied globally
 *
 * Story 2.12: OpenAPI Documentation and Swagger UI
 */
class OpenApiConfigurationTest :
    FunSpec({

        val configuration = OpenApiConfiguration()
        val openAPI = configuration.openAPI()

        test("should configure API metadata") {
            openAPI.info.shouldNotBeNull()
            openAPI.info.title shouldBe "EAF v1.0 API"
            openAPI.info.version shouldBe "1.0.0"
            openAPI.info.description shouldBe "Enterprise Application Framework REST API"
        }

        test("should configure contact information") {
            val contact = openAPI.info.contact
            contact.shouldNotBeNull()
            contact.name shouldBe "Axians EAF Team"
            contact.email shouldBe "eaf-team@axians.com"
        }

        test("should configure Bearer JWT security scheme") {
            val components = openAPI.components
            components.shouldNotBeNull()

            val securitySchemes = components.securitySchemes
            securitySchemes.shouldNotBeNull()
            securitySchemes.size shouldBe 1
            securitySchemes.containsKey("bearer-jwt") shouldBe true

            val jwtScheme = securitySchemes["bearer-jwt"]
            jwtScheme.shouldNotBeNull()
            jwtScheme.type shouldBe SecurityScheme.Type.HTTP
            jwtScheme.scheme shouldBe "bearer"
            jwtScheme.bearerFormat shouldBe "JWT"
            jwtScheme.description shouldBe "JWT Bearer token from Keycloak"
        }

        test("should apply Bearer JWT security requirement globally") {
            val security = openAPI.security
            security.shouldNotBeNull()
            security.size shouldBe 1

            val requirement = security.first()
            requirement.shouldNotBeNull()
            requirement.containsKey("bearer-jwt") shouldBe true
        }
    })
