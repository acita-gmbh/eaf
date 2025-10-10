package com.axians.eaf.framework.security.test

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Nullable Design Pattern implementation for JwtDecoder.
 * Provides a fast, stateful, in-memory substitute for JWT decoding in unit tests.
 *
 * This implementation abandons fragile string parsing. Instead, it uses the input
 * token string as a key to return pre-constructed, valid Jwt objects, making tests
 * more robust and readable.
 */
class NullableJwtDecoder : JwtDecoder {
    companion object {
        fun createNull(): NullableJwtDecoder = NullableJwtDecoder()
    }

    override fun decode(token: String): Jwt {
        val now = Instant.now()
        val inOneHour = now.plusSeconds(3600)

        return when (token) {
            JwtTestTokens.validRs256 ->
                buildJwt(now, inOneHour, "RS256", "test-jti")
            JwtTestTokens.hs256 ->
                buildJwt(now, inOneHour, "HS256", "test-jti-hs256")
            JwtTestTokens.noneAlgorithm ->
                buildJwt(now, inOneHour, "none", "test-jti-none")
            JwtTestTokens.expired ->
                buildJwt(now.minusSeconds(7200), now.minusSeconds(3600), "RS256", "test-jti-expired")
            JwtTestTokens.missingJti ->
                buildJwt(now, inOneHour, "RS256", null)
            JwtTestTokens.missingAlgorithm ->
                buildJwt(now, inOneHour, null, "test-jti-missing-alg")
            JwtTestTokens.es256 ->
                buildJwt(now, inOneHour, "ES256", "test-jti-es256")
            JwtTestTokens.INVALID_SIGNATURE -> throw JwtException("Invalid signature")
            else -> throw JwtException("Unhandled token type in NullableJwtDecoder: $token")
        }
    }

    private fun buildJwt(
        issuedAt: Instant,
        expiresAt: Instant,
        alg: String?,
        jti: String?,
    ): Jwt {
        val headers = mutableMapOf("typ" to "JWT")
        alg?.let { headers["alg"] = it }

        val claims =
            mutableMapOf<String, Any>(
                "sub" to UUID.randomUUID().toString(),
                "iss" to "http://localhost:8180/realms/eaf",
                "aud" to listOf("eaf-backend"),
                "iat" to issuedAt,
                "exp" to expiresAt,
                "tenant_id" to UUID.randomUUID().toString(),
                "realm_access" to mapOf("roles" to listOf("user")),
            )
        jti?.let { claims["jti"] = it }

        return Jwt
            .withTokenValue("mock-token-value-for-${jti ?: "no-jti"}")
            .headers { it.putAll(headers) }
            .claims { it.putAll(claims) }
            .build()
    }
}

object JwtTestTokens {
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    private const val ISSUER = "http://localhost:8180/realms/eaf"
    private const val AUDIENCE = "eaf-backend"
    private const val SIGNATURE = "signature"

    private fun encodeSegment(json: String): String = encoder.encodeToString(json.toByteArray(StandardCharsets.UTF_8))

    private fun buildToken(
        headerJson: String,
        payload: Map<String, Any?>,
    ): String =
        listOf(
            encodeSegment(headerJson),
            encodeSegment(payload.toJson()),
            encodeSegment(SIGNATURE),
        ).joinToString(".")

    private fun baseClaims(
        jti: String?,
        issuedAt: Long,
        expiresAt: Long,
    ): MutableMap<String, Any?> =
        linkedMapOf<String, Any?>().apply {
            put("sub", UUID.randomUUID().toString())
            put("iss", ISSUER)
            put("aud", listOf(AUDIENCE))
            put("iat", issuedAt)
            put("exp", expiresAt)
            put("tenant_id", UUID.randomUUID().toString())
            if (jti != null) {
                put("jti", jti)
            }
            put("realm_access", mapOf("roles" to listOf("user")))
        }

    val validRs256: String =
        buildToken(
            headerJson = """{"alg":"RS256","typ":"JWT"}""",
            payload =
                baseClaims(
                    jti = "test-jti",
                    issuedAt = Instant.now().epochSecond,
                    expiresAt = Instant.now().plusSeconds(3600).epochSecond,
                ),
        )

    val hs256: String =
        buildToken(
            headerJson = """{"alg":"HS256","typ":"JWT"}""",
            payload =
                baseClaims(
                    jti = "test-jti-hs256",
                    issuedAt = Instant.now().epochSecond,
                    expiresAt = Instant.now().plusSeconds(3600).epochSecond,
                ),
        )

    val noneAlgorithm: String =
        buildToken(
            headerJson = """{"alg":"none","typ":"JWT"}""",
            payload =
                baseClaims(
                    jti = "test-jti-none",
                    issuedAt = Instant.now().epochSecond,
                    expiresAt = Instant.now().plusSeconds(3600).epochSecond,
                ),
        )

    val expired: String =
        buildToken(
            headerJson = """{"alg":"RS256","typ":"JWT"}""",
            payload =
                baseClaims(
                    jti = "test-jti-expired",
                    issuedAt = Instant.now().minusSeconds(7200).epochSecond,
                    expiresAt = Instant.now().minusSeconds(3600).epochSecond,
                ),
        )

    val missingJti: String =
        buildToken(
            headerJson = """{"alg":"RS256","typ":"JWT"}""",
            payload =
                baseClaims(
                    jti = null,
                    issuedAt = Instant.now().epochSecond,
                    expiresAt = Instant.now().plusSeconds(3600).epochSecond,
                ).apply {
                    remove("jti")
                },
        )

    // Story 8.6: Additional test tokens for comprehensive validator testing
    const val INVALID_SIGNATURE: String = "invalid.signature.token"
    val missingAlgorithm: String =
        buildToken(
            headerJson = """{"typ":"JWT"}""", // No "alg"
            payload =
                baseClaims(
                    "test-jti",
                    Instant.now().epochSecond,
                    Instant.now().plusSeconds(3600).epochSecond,
                ),
        )
    val es256: String =
        buildToken(
            headerJson = """{"alg":"ES256","typ":"JWT"}""",
            payload =
                baseClaims(
                    "test-jti-es256",
                    Instant.now().epochSecond,
                    Instant.now().plusSeconds(3600).epochSecond,
                ),
        )
}

private fun Any?.toJson(): String =
    when (this) {
        null -> "null"
        is String ->
            buildString {
                append('"')
                append(this@toJson.replace("\"", "\\\""))
                append('"')
            }
        is Number, is Boolean -> toString()
        is Map<*, *> -> {
            val filteredEntries = entries.filter { it.key is String }
            filteredEntries.joinToString(
                prefix = "{",
                postfix = "}",
            ) { entry ->
                val key = entry.key as String
                "\"$key\":${entry.value.toJson()}"
            }
        }
        is Iterable<*> ->
            joinToString(prefix = "[", postfix = "]") { it.toJson() }
        else -> "\"${this@toJson}\""
    }
