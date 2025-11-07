package com.axians.eaf.framework.web.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.mock.web.MockHttpServletRequest
import java.time.Instant

/**
 * Unit tests for RestConfiguration (Story 2.9).
 *
 * Validates Jackson ObjectMapper and CORS configuration.
 *
 * **Test Coverage:**
 * - AC 4: RestConfiguration.kt with CORS, Jackson ObjectMapper, response formatting
 * - Jackson: ISO-8601 dates (NOT timestamps), null exclusion, Kotlin support
 * - CORS: localhost:3000, HTTP methods, credentials
 *
 * **Test Strategy:**
 * - Unit tests (no Spring context) - fast, isolated
 * - Test Jackson serialization behavior
 * - Test CORS configuration values
 *
 * **References:**
 * - Story 2.9: REST API Foundation
 * - Architecture: Section 15 (API Contracts - JSON Serialization)
 */
class RestConfigurationTest :
    FunSpec({

        val config = RestConfiguration()

        context("Jackson ObjectMapper configuration (AC 4)") {
            val objectMapper = config.objectMapper()

            test("should exclude null fields from JSON output") {
                // Given - Data class with null field
                data class TestData(
                    val name: String,
                    val description: String?,
                )
                val data = TestData(name = "Widget", description = null)

                // When
                val json = objectMapper.writeValueAsString(data)

                // Then - "description" field should NOT appear in JSON
                json shouldContain "name"
                json shouldContain "Widget"
                json shouldNotContain "description"
                json shouldNotContain "null"
            }

            test("should serialize Instant as ISO-8601 string (NOT timestamp)") {
                // Given - Data class with Instant field
                data class EventData(
                    val timestamp: Instant,
                )
                val data = EventData(timestamp = Instant.parse("2025-11-07T10:30:00Z"))

                // When
                val json = objectMapper.writeValueAsString(data)

                // Then - Should be ISO-8601 string (not numeric timestamp)
                json shouldContain "2025-11-07T10:30:00Z"
                json shouldNotContain "1730977800" // NOT numeric timestamp
            }

            test("should have JavaTimeModule registered for Java 8 time types") {
                // Given
                val registeredModules = objectMapper.registeredModuleIds

                // Then - JavaTimeModule ID should be present (registered as "jackson-datatype-jsr310")
                registeredModules shouldContain "jackson-datatype-jsr310"
            }

            test("should have KotlinModule registered for Kotlin data classes") {
                // Given
                val registeredModules = objectMapper.registeredModuleIds

                // Then - KotlinModule ID should be present
                registeredModules shouldContain "com.fasterxml.jackson.module.kotlin.KotlinModule"
            }

            test("should disable WRITE_DATES_AS_TIMESTAMPS feature") {
                // Given
                val isEnabled = objectMapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

                // Then - Should be disabled (dates as ISO-8601 strings)
                isEnabled shouldBe false
            }

            test("should use NON_NULL inclusion for null field exclusion") {
                // Given
                val serializationConfig = objectMapper.serializationConfig
                val inclusion = serializationConfig.defaultPropertyInclusion

                // Then - Should exclude null values
                inclusion.valueInclusion shouldBe JsonInclude.Include.NON_NULL
            }

            test("should serialize Kotlin data class with default parameters correctly") {
                // Given - Kotlin data class with default parameter
                data class WidgetData(
                    val id: String,
                    val name: String = "Default Widget",
                )
                val data = WidgetData(id = "widget-123")

                // When
                val json = objectMapper.writeValueAsString(data)

                // Then - Should include default value
                json shouldContain "widget-123"
                json shouldContain "Default Widget"
            }
        }

        context("CORS configuration (AC 4)") {
            val corsSource = config.corsConfigurationSource()

            test("should allow localhost:3000 origin (React dev server)") {
                // Given
                val request =
                    MockHttpServletRequest().apply {
                        requestURI = "/api/widgets"
                    }
                val corsConfig = corsSource.getCorsConfiguration(request)

                // Then
                corsConfig.shouldNotBeNull()
                corsConfig.allowedOrigins.shouldNotBeNull()
                corsConfig.allowedOrigins!! shouldContain "http://localhost:3000"
            }

            test("should allow standard HTTP methods") {
                // Given
                val request =
                    MockHttpServletRequest().apply {
                        requestURI = "/api/widgets"
                    }
                val corsConfig = corsSource.getCorsConfiguration(request)

                // Then
                corsConfig.shouldNotBeNull()
                corsConfig.allowedMethods.shouldNotBeNull()
                corsConfig.allowedMethods!! shouldContainAll listOf("GET", "POST", "PUT", "DELETE", "PATCH")
            }

            test("should allow all headers (development mode)") {
                // Given
                val request =
                    MockHttpServletRequest().apply {
                        requestURI = "/api/widgets"
                    }
                val corsConfig = corsSource.getCorsConfiguration(request)

                // Then
                corsConfig.shouldNotBeNull()
                corsConfig.allowedHeaders.shouldNotBeNull()
                corsConfig.allowedHeaders!! shouldContain "*"
            }

            test("should allow credentials (cookies, auth headers)") {
                // Given
                val request =
                    MockHttpServletRequest().apply {
                        requestURI = "/api/widgets"
                    }
                val corsConfig = corsSource.getCorsConfiguration(request)

                // Then
                corsConfig.shouldNotBeNull()
                corsConfig.allowCredentials shouldBe true
            }

            test("should apply CORS configuration to all endpoints") {
                // Given - Test various endpoint patterns
                val endpoints = listOf("/api/widgets", "/api/widgets/123", "/health", "/actuator/prometheus")

                // When/Then - All endpoints should get same CORS config
                endpoints.forEach { endpoint ->
                    val request =
                        MockHttpServletRequest().apply {
                            requestURI = endpoint
                        }
                    val corsConfig = corsSource.getCorsConfiguration(request)
                    corsConfig.shouldNotBeNull()
                    corsConfig.allowedOrigins.shouldNotBeNull()
                    corsConfig.allowedOrigins!! shouldContain "http://localhost:3000"
                }
            }
        }
    })
