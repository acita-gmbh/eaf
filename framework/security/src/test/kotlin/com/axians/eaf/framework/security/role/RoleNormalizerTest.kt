package com.axians.eaf.framework.security.role

import com.axians.eaf.framework.security.config.KeycloakOidcConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Unit tests for RoleNormalizer.
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 */
class RoleNormalizerTest {

    private fun normalizer(clientId: String = "eaf-api") = RoleNormalizer(
        KeycloakOidcConfiguration(audience = clientId),
    )

    @Test
    fun `should normalize realm and resource roles with ROLE_ prefix`() {
        val underTest = normalizer()
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim(
                "realm_access",
                mapOf("roles" to listOf("WIDGET_ADMIN", "ROLE_EXISTING")),
            ).claim(
                "resource_access",
                mapOf(
                    "eaf-api" to mapOf("roles" to listOf("widget:create", "widget:update")),
                ),
            ).build()

        val authorities = underTest.normalize(jwt).map { it as SimpleGrantedAuthority }

        assertThat(authorities).containsExactly(
            SimpleGrantedAuthority("ROLE_WIDGET_ADMIN"),
            SimpleGrantedAuthority("ROLE_EXISTING"),
            SimpleGrantedAuthority("widget:create"),
            SimpleGrantedAuthority("widget:update"),
        )
    }

    @Test
    fun `should ignore blank roles and deduplicate results`() {
        val underTest = normalizer()
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim(
                "realm_access",
                mapOf(
                    "roles" to listOf(" ", "widget_admin", "widget_admin"),
                ),
            ).claim(
                "resource_access",
                mapOf(
                    "eaf-api" to mapOf(
                        "roles" to listOf("ROLE_WIDGET_ADMIN", "widget_admin"),
                    ),
                ),
            ).build()

        val authorities = underTest.normalize(jwt)

        assertThat(authorities.size).isEqualTo(1)
        assertThat(authorities.first().authority).isEqualTo("ROLE_widget_admin")
    }

    @Test
    fun `should flatten nested structures and arrays`() {
        val underTest = normalizer()
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim(
                "realm_access",
                mapOf(
                    "roles" to listOf(listOf("nested-role"), arrayOf("ROLE_CUSTOM")),
                ),
            ).build()

        val authorities = underTest.normalize(jwt).map { it.authority }

        assertThat(authorities).containsExactly("ROLE_nested-role", "ROLE_CUSTOM")
    }

    @Test
    fun `should preserve hyphenated realm roles when prefixing`() {
        val underTest = normalizer()
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim(
                "realm_access",
                mapOf("roles" to listOf("eaf-admin")),
            ).build()

        val authorities = underTest.normalize(jwt).map { it.authority }

        assertThat(authorities).containsExactly("ROLE_eaf-admin")
    }

    @Test
    fun `should ignore resource roles from other clients`() {
        val underTest = normalizer()
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim(
                "resource_access",
                mapOf(
                    "eaf-api" to mapOf("roles" to listOf("widget:read")),
                    "external-app" to mapOf("roles" to listOf("WIDGET_ADMIN")),
                ),
            ).build()

        val authorities = underTest.normalize(jwt).map { it.authority }

        assertThat(authorities).containsExactly("widget:read")
    }
}
