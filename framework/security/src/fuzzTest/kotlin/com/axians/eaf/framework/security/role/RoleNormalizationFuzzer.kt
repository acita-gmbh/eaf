package com.axians.eaf.framework.security.role

import com.axians.eaf.framework.security.config.KeycloakOidcConfiguration
import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID

class RoleNormalizationFuzzer {
    private val normalizer = RoleNormalizer(KeycloakOidcConfiguration(audience = "eaf-api"))

    @FuzzTest(maxDuration = "30s")
    fun fuzzRoleNormalization(data: FuzzedDataProvider) {
        val jwt = randomJwt(data, allowNullClaims = false, allowMalformedResourceAccess = false, injectionSeed = null)

        assertDoesNotThrow { normalizer.normalize(jwt) }
    }

    @FuzzTest(maxDuration = "30s")
    fun fuzzRoleNormalizationWithNull(data: FuzzedDataProvider) {
        val jwt = randomJwt(data, allowNullClaims = true, allowMalformedResourceAccess = true, injectionSeed = null)

        assertDoesNotThrow { normalizer.normalize(jwt) }
    }

    @FuzzTest(maxDuration = "30s")
    fun fuzzRoleNormalizationInjectionPatterns(data: FuzzedDataProvider) {
        val payloads = listOf("<script>alert('x')</script>", "${'$'}(rm -rf /)", "admin' OR '1'='1", "ROLE_SUPER_ADMIN")
        val seed = payloads[data.consumeInt(0, payloads.lastIndex)]
        val jwt = randomJwt(data, allowNullClaims = true, allowMalformedResourceAccess = true, injectionSeed = seed)

        assertDoesNotThrow { normalizer.normalize(jwt) }
    }

    @FuzzTest(maxDuration = "30s")
    fun fuzzRoleNormalizationUnicodeAttacks(data: FuzzedDataProvider) {
        val unicodeSeed =
            buildString {
                repeat(data.consumeInt(1, 8)) {
                    appendCodePoint(data.consumeInt(0x20, 0x2FFF))
                }
            }
        val jwt =
            randomJwt(data, allowNullClaims = true, allowMalformedResourceAccess = true, injectionSeed = unicodeSeed)

        assertDoesNotThrow { normalizer.normalize(jwt) }
    }

    private fun randomJwt(
        data: FuzzedDataProvider,
        allowNullClaims: Boolean,
        allowMalformedResourceAccess: Boolean,
        injectionSeed: String?,
    ): Jwt {
        val builder =
            Jwt
                .withTokenValue(UUID.randomUUID().toString())
                .header("alg", "RS256")

        val realmRoleCount = data.consumeInt(0, 10)
        val realmRoles = mutableListOf<Any?>()
        repeat(realmRoleCount) {
            realmRoles += sampledRoleStructure(data)
        }

        val includeRealmRoles = !allowNullClaims || data.consumeBoolean()
        if (includeRealmRoles) {
            builder.claim("realm_access", mapOf("roles" to realmRoles))
        } else {
            builder.claim("realm_access", null)
        }

        val resourceEntries = data.consumeInt(0, 5)
        val resourceAccess = linkedMapOf<String, Any?>()
        repeat(resourceEntries) {
            val key = safeString(data.consumeString(16))
            val roleValues = mutableListOf<Any?>()
            repeat(data.consumeInt(0, 5)) {
                roleValues += sampledRoleStructure(data)
            }

            val entryValue =
                if (!allowMalformedResourceAccess || data.consumeBoolean()) {
                    mapOf("roles" to roleValues)
                } else {
                    when (data.consumeInt(0, 2)) {
                        0 -> roleValues
                        1 -> safeString(data.consumeString(32))
                        else -> null
                    }
                }

            resourceAccess[key] = entryValue
        }

        val includeResourceAccess = resourceAccess.isNotEmpty() && (!allowNullClaims || data.consumeBoolean())
        if (includeResourceAccess) {
            builder.claim("resource_access", resourceAccess)
        } else {
            builder.claim(
                "resource_access",
                if (allowMalformedResourceAccess) safeString(data.consumeString(32)) else emptyMap<String, Any>(),
            )
        }

        injectionSeed?.let {
            builder.claim("seed", it)
        }

        return builder.build()
    }

    private fun sampledRoleStructure(data: FuzzedDataProvider): Any? {
        val role = safeString(data.consumeString(24))
        return when (data.consumeInt(0, 4)) {
            0 -> role
            1 -> listOf(role)
            2 -> arrayOf(role)
            3 -> listOf(listOf(role))
            else -> null
        }
    }

    private fun safeString(value: String): String = value.ifBlank { "role-${'$'}{value.hashCode()}" }
}
