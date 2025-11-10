package com.axians.eaf.framework.security.role

import com.axians.eaf.framework.security.config.KeycloakOidcConfiguration
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

class RoleNormalizerTest :
    FunSpec({
        fun normalizer(clientId: String = "eaf-api") =
            RoleNormalizer(
                KeycloakOidcConfiguration(audience = clientId),
            )

        test("should normalize realm and resource roles with ROLE_ prefix") {
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

            authorities.shouldContainExactly(
                SimpleGrantedAuthority("ROLE_WIDGET_ADMIN"),
                SimpleGrantedAuthority("ROLE_EXISTING"),
                SimpleGrantedAuthority("widget:create"),
                SimpleGrantedAuthority("widget:update"),
            )
        }

        test("should ignore blank roles and deduplicate results") {
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

            authorities.size.shouldBe(1)
            authorities.first().authority.shouldBe("ROLE_WIDGET_ADMIN")
        }

        test("should flatten nested structures and arrays") {
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

            authorities.shouldContainExactly("ROLE_NESTED_ROLE", "ROLE_CUSTOM")
        }

        test("should ignore resource roles from other clients") {
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

            authorities.shouldContainExactly("widget:read")
        }
    })
