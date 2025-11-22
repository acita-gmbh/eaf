package com.axians.eaf.framework.security.user

import com.axians.eaf.framework.security.config.KeycloakAdminProperties
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Keycloak-backed implementation for Layer 9 user validation.
 */
@Component
class KeycloakUserDirectory(
    private val adminProperties: KeycloakAdminProperties,
    restTemplateBuilder: RestTemplateBuilder,
    private val clock: Clock = Clock.systemUTC(),
) : UserDirectory {
    internal val restTemplate: RestTemplate

    private val tokenCache = AtomicReference<CachedToken?>()
    private val userCache = ConcurrentHashMap<String, CachedUser>()

    init {
        restTemplate =
            restTemplateBuilder
                .additionalInterceptors(
                    ClientHttpRequestInterceptor { request, body, execution ->
                        request.headers.add(FORWARDED_PROTO_HEADER, "https")
                        execution.execute(request, body)
                    },
                ).build()
    }

    override fun findById(userId: String): UserRecord? =
        run {
            if (userId.isBlank()) {
                return@run null
            }

            val now = Instant.now(clock)
            val cached = userCache[userId]
            if (cached != null && now.isBefore(cached.expiresAt)) {
                return@run cached.toUserRecord()
            }

            val representation = fetchUser(userId)
            val record = representation?.let { UserRecord(it.id ?: userId, it.enabled == true) }

            val ttl =
                (if (record == null) adminProperties.negativeUserCacheTtl else adminProperties.userCacheTtl)
                    .ensurePositive(DEFAULT_CACHE_TTL)
            val entry =
                CachedUser(
                    exists = record != null,
                    active = record?.active ?: false,
                    cachedId = record?.id,
                    expiresAt = now.plus(ttl),
                )
            userCache[userId] = entry

            record
        }

    private fun fetchUser(userId: String): KeycloakUserRepresentation? {
        val accessToken = adminAccessToken()
        val uri =
            UriComponentsBuilder
                .fromUriString(normalizeBaseUrl())
                .pathSegment("admin", "realms", adminProperties.realm, "users", userId)
                .build()
                .toUri()

        val request =
            RequestEntity
                .get(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${accessToken.token}")
                .build()

        @Suppress("SwallowedException")
        return try {
            restTemplate.exchange(request, KeycloakUserRepresentation::class.java).body
        } catch (ex: HttpClientErrorException.NotFound) {
            null
        } catch (ex: HttpStatusCodeException) {
            log.warn("Keycloak user lookup failed with status {} {}", ex.statusCode.value(), ex.statusText)
            throw UserValidationException("Keycloak user lookup failed with status ${ex.statusCode.value()}", ex)
        } catch (ex: RestClientException) {
            log.warn("Keycloak user lookup failed", ex)
            throw UserValidationException("Keycloak user lookup failed", ex)
        }
    }

    private fun adminAccessToken(): CachedToken {
        val now = Instant.now(clock)
        tokenCache.get()?.let { cached ->
            if (now.isBefore(cached.expiresAt)) {
                return cached
            }
        }

        if (adminProperties.adminClientSecret.isBlank()) {
            throw UserValidationException("Keycloak admin client secret is not configured; cannot validate users.")
        }

        val tokenUrl =
            UriComponentsBuilder
                .fromUriString(normalizeBaseUrl())
                .pathSegment("realms", adminProperties.realm, "protocol", "openid-connect", "token")
                .build()
                .toUriString()

        val headers =
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_FORM_URLENCODED
                add(FORWARDED_PROTO_HEADER, "https")
            }

        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "client_credentials")
                add("client_id", adminProperties.adminClientId)
                add("client_secret", adminProperties.adminClientSecret)
            }

        val result =
            runCatching {
                val response =
                    restTemplate.postForEntity(
                        tokenUrl,
                        HttpEntity(form, headers),
                        AccessTokenResponse::class.java,
                    )
                val body = checkNotNull(response.body) { "Keycloak token endpoint returned empty body" }
                val accessToken = checkNotNull(body.accessToken) { "Keycloak token endpoint missing access_token" }
                val expiresInSeconds = body.expiresIn

                val expiry =
                    now
                        .plusSeconds((expiresInSeconds ?: DEFAULT_TOKEN_TTL_SECONDS).coerceAtLeast(1))
                        .minus(adminProperties.tokenExpirySkew)
                        .let { candidate ->
                            if (candidate.isAfter(now)) candidate else now.plusSeconds(MIN_TOKEN_TTL_SECONDS)
                        }

                CachedToken(accessToken, expiry)
            }.getOrElse { ex ->
                throw UserValidationException("Failed to obtain Keycloak admin token", ex)
            }

        tokenCache.set(result)
        return result
    }

    private fun normalizeBaseUrl(): String = adminProperties.baseUrl.trimEnd('/')

    private data class CachedToken(
        val token: String,
        val expiresAt: Instant,
    )

    private data class CachedUser(
        val exists: Boolean,
        val active: Boolean,
        val cachedId: String?,
        val expiresAt: Instant,
    ) {
        fun toUserRecord(): UserRecord? = if (exists && cachedId != null) UserRecord(cachedId, active) else null
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class KeycloakUserRepresentation(
        val id: String? = null,
        val username: String? = null,
        val enabled: Boolean? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class AccessTokenResponse(
        @JsonProperty("access_token") val accessToken: String? = null,
        @JsonProperty("expires_in") val expiresIn: Long? = null,
    )

    companion object {
        private val DEFAULT_CACHE_TTL: Duration = Duration.ofSeconds(30)
        private const val DEFAULT_TOKEN_TTL_SECONDS = 60L
        private const val MIN_TOKEN_TTL_SECONDS = 5L
        private const val FORWARDED_PROTO_HEADER = "X-Forwarded-Proto"
        private val log = LoggerFactory.getLogger(KeycloakUserDirectory::class.java)
    }
}

private fun Duration.ensurePositive(fallback: Duration): Duration =
    when {
        isZero -> fallback
        isNegative -> fallback
        else -> this
    }
