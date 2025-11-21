package com.axians.eaf.framework.security.role

import com.axians.eaf.framework.security.config.KeycloakOidcConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Unit tests for RoleNormalizer - Keycloak role claim extraction and normalization.
 *
 * Validates extraction of Keycloak roles from realm_access and resource_access JWT claims,
 * normalizing them to Spring Security GrantedAuthority format with ROLE_ prefix for realm
 * roles and preserving resource role format (e.g., widget:create).
 *
 * **Test Coverage:**
 * - Realm role extraction with ROLE_ prefix (realm_access.roles)
 * - Resource role extraction without prefix (resource_access.[clientId].roles)
 * - Existing ROLE_ prefix preservation (idempotent)
 * - Blank role filtering (whitespace roles ignored)
 * - Deduplication (same role from multiple sources = single authority)
 * - Nested structure flattening (lists within lists, arrays)
 * - Hyphenated role preservation (eaf-admin, widget:read)
 * - Other client filtering (only configured clientId roles extracted)
 *
 * **Security Patterns:**
 * - Client isolation (roles from other clients ignored)
 * - Consistent normalization (ROLE_ prefix for realm, no prefix for resource)
 * - Deduplication (prevents duplicate authority checks)
 * - Blank filtering (prevents empty role vulnerabilities)
 * - Spring Security integration (GrantedAuthority format)
 *
 * **Keycloak Role Structure:**
 * - Realm roles: Global roles across all clients (e.g., WIDGET_ADMIN)
 * - Resource roles: Client-specific roles (e.g., widget:create, widget:read)
 * - Normalization: Realm roles get ROLE_ prefix, resource roles preserve format
 *
 * **Testing Strategy:**
 * - Comprehensive role source coverage (realm, resource)
 * - Edge case handling (blank, nested, duplicates, hyphens)
 * - Client isolation verification (other client roles ignored)
 *
 * **Acceptance Criteria:**
 * - Layer 8: Role normalization (realm roles prefixed, resource roles preserved)
 * - Client isolation (only configured clientId roles extracted)
 *
 * @see RoleNormalizer Primary class under test
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
 */
class RoleNormalizerTest {
    private fun normalizer(clientId: String = "eaf-api") =
        RoleNormalizer(
            KeycloakOidcConfiguration(audience = clientId),
        )

    @Test
    fun `should normalize realm and resource roles with ROLE_ prefix`() {
        val underTest = normalizer()
        val jwt =
            Jwt
                .withTokenValue("token")
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
        val jwt =
            Jwt
                .withTokenValue("token")
                .header("alg", "RS256")
                .claim(
                    "realm_access",
                    mapOf(
                        "roles" to listOf(" ", "widget_admin", "widget_admin"),
                    ),
                ).claim(
                    "resource_access",
                    mapOf(
                        "eaf-api" to
                            mapOf(
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
        val jwt =
            Jwt
                .withTokenValue("token")
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
        val jwt =
            Jwt
                .withTokenValue("token")
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
        val jwt =
            Jwt
                .withTokenValue("token")
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
