package com.axians.eaf.framework.web.openapi

import io.swagger.v3.oas.models.security.SecurityScheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
class OpenApiConfigurationTest {
    private val configuration = OpenApiConfiguration()
    private val openAPI = configuration.openAPI()

    @Test
    fun `should configure API metadata`() {
        assertThat(openAPI.info).isNotNull
        assertThat(openAPI.info.title).isEqualTo("EAF v1.0 API")
        assertThat(openAPI.info.version).isEqualTo("1.0.0")
        assertThat(openAPI.info.description).isEqualTo("Enterprise Application Framework REST API")
    }

    @Test
    fun `should configure contact information`() {
        val contact = openAPI.info.contact
        assertThat(contact).isNotNull
        assertThat(contact.name).isEqualTo("Axians EAF Team")
        assertThat(contact.email).isEqualTo("eaf-team@axians.com")
    }

    @Test
    fun `should configure Bearer JWT security scheme`() {
        val components = openAPI.components
        assertThat(components).isNotNull

        val securitySchemes = components.securitySchemes
        assertThat(securitySchemes).isNotNull
        assertThat(securitySchemes).hasSize(1)
        assertThat(securitySchemes).containsKey("bearer-jwt")

        val jwtScheme = securitySchemes["bearer-jwt"]
        assertThat(jwtScheme).isNotNull
        assertThat(jwtScheme!!.type).isEqualTo(SecurityScheme.Type.HTTP)
        assertThat(jwtScheme.scheme).isEqualTo("bearer")
        assertThat(jwtScheme.bearerFormat).isEqualTo("JWT")
        assertThat(jwtScheme.description).isEqualTo("JWT Bearer token from Keycloak")
    }

    @Test
    fun `should apply Bearer JWT security requirement globally`() {
        val security = openAPI.security
        assertThat(security).isNotNull
        assertThat(security).hasSize(1)

        val requirement = security.first()
        assertThat(requirement).isNotNull
        assertThat(requirement).containsKey("bearer-jwt")
    }
}
