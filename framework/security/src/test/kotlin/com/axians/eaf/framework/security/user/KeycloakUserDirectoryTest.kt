package com.axians.eaf.framework.security.user

import com.axians.eaf.framework.security.config.KeycloakAdminProperties
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class KeycloakUserDirectoryTest :
    FunSpec({
        lateinit var properties: KeycloakAdminProperties
        lateinit var directory: KeycloakUserDirectory
        lateinit var server: MockRestServiceServer

        beforeTest {
            properties =
                KeycloakAdminProperties(
                    baseUrl = "http://localhost:8080",
                    realm = "eaf",
                    adminClientId = "eaf-admin",
                    adminClientSecret = "secret",
                    userCacheTtl = Duration.ofSeconds(60),
                    negativeUserCacheTtl = Duration.ofSeconds(30),
                    tokenExpirySkew = Duration.ofSeconds(0),
                )
            directory =
                KeycloakUserDirectory(
                    properties,
                    RestTemplateBuilder(),
                    Clock.fixed(Instant.parse("2025-11-12T00:00:00Z"), ZoneOffset.UTC),
                )
            server = MockRestServiceServer.createServer(directory.restTemplate)
        }

        afterTest {
            server.verify()
        }

        fun expectTokenRequest(
            token: String = "admin-token",
            expiresIn: Long = 120L,
        ) {
            server
                .expect(
                    ExpectedCount.once(),
                    requestTo("http://localhost:8080/realms/eaf/protocol/openid-connect/token"),
                ).andExpect(method(HttpMethod.POST))
                .andRespond(
                    withSuccess(
                        """{"access_token":"$token","expires_in":$expiresIn}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )
        }

        fun expectUserRequest(
            userId: String,
            enabled: Boolean? = true,
        ) {
            val responder =
                if (enabled == null) {
                    withStatus(HttpStatus.NOT_FOUND)
                } else {
                    withSuccess(
                        """{"id":"$userId","enabled":$enabled}""",
                        MediaType.APPLICATION_JSON,
                    )
                }

            server
                .expect(
                    ExpectedCount.once(),
                    requestTo("http://localhost:8080/admin/realms/eaf/users/$userId"),
                ).andExpect(method(HttpMethod.GET))
                .andRespond(responder)
        }

        test("returns active user and caches result") {
            expectTokenRequest()
            expectUserRequest("user-123", enabled = true)

            val first = directory.findById("user-123")
            first!!.active.shouldBeTrue()

            directory.findById("user-123")!!.active.shouldBeTrue()
        }

        test("returns null for missing user and caches negative result") {
            expectTokenRequest()
            expectUserRequest("ghost", enabled = null)

            directory.findById("ghost").shouldBeNull()
            directory.findById("ghost").shouldBeNull()
        }

        test("reuses admin token across lookups until expiry") {
            // First lookup - should acquire token
            server
                .expect(once(), requestTo("http://localhost:8080/realms/eaf/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""{"access_token":"token123","expires_in":300}""", MediaType.APPLICATION_JSON))

            server
                .expect(once(), requestTo("http://localhost:8080/admin/realms/eaf/users/user123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                    withSuccess("""{"id":"user123","username":"user123","enabled":true}""", MediaType.APPLICATION_JSON),
                )

            val result1 = directory.findById("user123")
            assertEquals("user123", result1?.id)
            assertEquals(true, result1?.active)

            // Second lookup - should reuse token (no new token request)
            server
                .expect(once(), requestTo("http://localhost:8080/admin/realms/eaf/users/user456"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                    withSuccess("""{"id":"user456","username":"user456","enabled":true}""", MediaType.APPLICATION_JSON),
                )

            val result2 = directory.findById("user456")
            assertEquals("user456", result2?.id)
            assertEquals(true, result2?.active)
        }
    })
