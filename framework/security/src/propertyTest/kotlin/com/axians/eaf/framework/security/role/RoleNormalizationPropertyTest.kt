package com.axians.eaf.framework.security.role

import com.axians.eaf.framework.security.config.KeycloakOidcConfiguration
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll
import org.junit.jupiter.api.Tag
import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID

@Tag("PBT")
class RoleNormalizationPropertyTest :
    FunSpec({
        val targetClientId = "eaf-api"
        val normalizer = RoleNormalizer(KeycloakOidcConfiguration(audience = targetClientId))

        val roleStringArb = Arb.stringPattern("[A-Za-z:_-]{0,12}")
        val realmRolesArb = Arb.list(roleStringArb, 0..6)
        val targetResourceRolesArb = Arb.list(roleStringArb, 0..5)
        val otherResourceEntryArb =
            Arb.list(
                Arb.pair(Arb.stringPattern("[a-z]{3,8}"), Arb.list(roleStringArb, 0..4)),
                0..3,
            )

        val includeRealmArb = Arb.boolean()
        val includeResourceArb = Arb.boolean()
        val nestedRealmArb = Arb.boolean()
        val nestedResourceArb = Arb.boolean()
        val omitRolesKeyArb = Arb.boolean()

        test("normalized authorities should match spec for random nested inputs") {
            checkAll(
                realmRolesArb,
                targetResourceRolesArb,
                otherResourceEntryArb,
                includeRealmArb,
                includeResourceArb,
                nestedRealmArb,
                nestedResourceArb,
                omitRolesKeyArb,
            ) {
                realmRoles,
                targetRoles,
                otherEntries,
                includeRealm,
                includeResource,
                nestedRealm,
                nestedResource,
                omitRolesKey,
                ->
                val realmClaim = createRealmClaim(realmRoles, includeRealm, nestedRealm)
                val resourceClaim =
                    createResourceClaim(
                        targetClientId,
                        targetRoles,
                        otherEntries,
                        includeResource,
                        nestedResource,
                        omitRolesKey,
                    )

                val jwt = buildJwt(realmClaim.claim, resourceClaim.claim)
                val normalizedAuthorities = normalizer.normalize(jwt).map { it.authority }.toSet()

                val expected = expectedAuthorities(realmClaim.roles, resourceClaim.roles)

                normalizedAuthorities.shouldBe(expected)
            }
        }
    })

data class RealmClaim(
    val claim: Any?,
    val roles: List<String>,
)

data class ResourceClaim(
    val claim: Map<String, Any>?,
    val roles: List<String>,
)

private fun createRealmClaim(
    roles: List<String>,
    include: Boolean,
    nested: Boolean,
): RealmClaim {
    if (!include || roles.isEmpty()) {
        return RealmClaim(null, emptyList())
    }

    val claimValue =
        if (!nested) {
            roles
        } else {
            roles.mapIndexed { index, role ->
                if (index % 2 == 0) {
                    role
                } else {
                    listOf(role)
                }
            }
        }

    return RealmClaim(claimValue, roles)
}

private fun createResourceClaim(
    targetClientId: String,
    targetRoles: List<String>,
    otherEntries: List<Pair<String, List<String>>>,
    include: Boolean,
    nested: Boolean,
    omitRolesKey: Boolean,
): ResourceClaim {
    if (!include) {
        return ResourceClaim(null, emptyList())
    }

    val claimMap = linkedMapOf<String, Any>()
    val accessibleRoles = mutableListOf<String>()

    val targetIncludeRoles = !(omitRolesKey && targetRoles.isNotEmpty())
    val wrappedTargetRoles =
        if (targetRoles.isEmpty()) {
            emptyList()
        } else if (!nested) {
            targetRoles
        } else {
            targetRoles.mapIndexed { idx, role ->
                when (idx % 3) {
                    0 -> role
                    1 -> listOf(role)
                    else -> arrayOf(role)
                }
            }
        }

    if (targetIncludeRoles) {
        claimMap[targetClientId] = mapOf("roles" to wrappedTargetRoles)
        accessibleRoles += targetRoles
    } else {
        claimMap[targetClientId] = emptyMap<String, Any>()
    }

    otherEntries.forEachIndexed { index, (client, roles) ->
        val includeRolesKey = !(omitRolesKey && index % 2 == 1)
        if (!includeRolesKey) {
            claimMap[client] = emptyMap<String, Any>()
        } else {
            val wrappedRoles =
                if (!nested) {
                    roles
                } else {
                    roles.mapIndexed { idx, role ->
                        when (idx % 3) {
                            0 -> role
                            1 -> listOf(role)
                            else -> arrayOf(role)
                        }
                    }
                }
            claimMap[client] = mapOf("roles" to wrappedRoles)
        }
    }

    return ResourceClaim(claimMap.ifEmpty { null }, accessibleRoles)
}

private fun buildJwt(
    realmClaim: Any?,
    resourceClaim: Map<String, Any>?,
): Jwt {
    val builder =
        Jwt
            .withTokenValue(UUID.randomUUID().toString())
            .header("alg", "RS256")

    realmClaim?.let {
        builder.claim("realm_access", mapOf("roles" to it))
    }

    resourceClaim?.let {
        builder.claim("resource_access", it)
    }

    return builder.build()
}

private fun expectedAuthorities(
    realmRoles: List<String>,
    resourceRoles: List<String>,
): Set<String> {
    val rawRoles = linkedSetOf<String>()
    rawRoles.addAll(realmRoles)
    rawRoles.addAll(resourceRoles)

    val deduped = linkedMapOf<String, String>()
    rawRoles.mapNotNull { normalizeRoleString(it) }.forEach { authority ->
        deduped.putIfAbsent(authority.lowercase(), authority)
    }

    return deduped.values.toSet()
}

private fun normalizeRoleString(role: String): String? {
    val trimmed = role.trim()
    if (trimmed.isEmpty()) {
        return null
    }

    return when {
        trimmed.contains(":") -> trimmed
        trimmed.startsWith("ROLE_", ignoreCase = true) ->
            "ROLE_" + trimmed.substring(5)
        else -> "ROLE_" + trimmed
    }
}
