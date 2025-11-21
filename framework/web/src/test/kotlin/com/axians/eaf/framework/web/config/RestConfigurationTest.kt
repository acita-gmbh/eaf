package com.axians.eaf.framework.web.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
class RestConfigurationTest {
    private val config = RestConfiguration()

    @Nested
    inner class JacksonObjectMapperConfiguration {
        private val objectMapper = config.objectMapper()

        @Test
        fun `should exclude null fields from JSON output`() {
            // Given - Data class with null field
            data class TestData(
                val name: String,
                val description: String?,
            )
            val data = TestData(name = "Widget", description = null)

            // When
            val json = objectMapper.writeValueAsString(data)

            // Then - "description" field should NOT appear in JSON
            assertThat(json).contains("name")
            assertThat(json).contains("Widget")
            assertThat(json).doesNotContain("description")
            assertThat(json).doesNotContain("null")
        }

        @Test
        fun `should serialize Instant as ISO-8601 string (NOT timestamp)`() {
            // Given - Data class with Instant field
            data class EventData(
                val timestamp: Instant,
            )
            val data = EventData(timestamp = Instant.parse("2025-11-07T10:30:00Z"))

            // When
            val json = objectMapper.writeValueAsString(data)

            // Then - Should be ISO-8601 string (not numeric timestamp)
            assertThat(json).contains("2025-11-07T10:30:00Z")
            assertThat(json).doesNotContain("1730977800") // NOT numeric timestamp
        }

        @Test
        fun `should have JavaTimeModule registered for Java 8 time types`() {
            // Given
            val registeredModules = objectMapper.registeredModuleIds

            // Then - JavaTimeModule ID should be present (registered as "jackson-datatype-jsr310")
            assertThat(registeredModules).contains("jackson-datatype-jsr310")
        }

        @Test
        fun `should have KotlinModule registered for Kotlin data classes`() {
            // Given
            val registeredModules = objectMapper.registeredModuleIds

            // Then - KotlinModule ID should be present
            assertThat(registeredModules).contains("com.fasterxml.jackson.module.kotlin.KotlinModule")
        }

        @Test
        fun `should disable WRITE_DATES_AS_TIMESTAMPS feature`() {
            // Given
            val isEnabled = objectMapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

            // Then - Should be disabled (dates as ISO-8601 strings)
            assertThat(isEnabled).isFalse()
        }

        @Test
        fun `should use NON_NULL inclusion for null field exclusion`() {
            // Given
            val serializationConfig = objectMapper.serializationConfig
            val inclusion = serializationConfig.defaultPropertyInclusion

            // Then - Should exclude null values
            assertThat(inclusion.valueInclusion).isEqualTo(JsonInclude.Include.NON_NULL)
        }

        @Test
        fun `should serialize Kotlin data class with default parameters correctly`() {
            // Given - Kotlin data class with default parameter
            data class WidgetData(
                val id: String,
                val name: String = "Default Widget",
            )
            val data = WidgetData(id = "widget-123")

            // When
            val json = objectMapper.writeValueAsString(data)

            // Then - Should include default value
            assertThat(json).contains("widget-123")
            assertThat(json).contains("Default Widget")
        }
    }

    @Nested
    inner class CorsConfiguration {
        private val corsSource = config.corsConfigurationSource()

        @Test
        fun `should allow localhost 3000 origin (React dev server)`() {
            // Given
            val request =
                MockHttpServletRequest().apply {
                    requestURI = "/api/widgets"
                }
            val corsConfig = corsSource.getCorsConfiguration(request)

            // Then
            assertThat(corsConfig).isNotNull
            assertThat(corsConfig.allowedOrigins).isNotNull
            assertThat(corsConfig.allowedOrigins!!).contains("http://localhost:3000")
        }

        @Test
        fun `should allow standard HTTP methods`() {
            // Given
            val request =
                MockHttpServletRequest().apply {
                    requestURI = "/api/widgets"
                }
            val corsConfig = corsSource.getCorsConfiguration(request)

            // Then
            assertThat(corsConfig).isNotNull
            assertThat(corsConfig.allowedMethods).isNotNull
            assertThat(corsConfig.allowedMethods!!).containsAll(listOf("GET", "POST", "PUT", "DELETE", "PATCH"))
        }

        @Test
        fun `should allow all headers (development mode)`() {
            // Given
            val request =
                MockHttpServletRequest().apply {
                    requestURI = "/api/widgets"
                }
            val corsConfig = corsSource.getCorsConfiguration(request)

            // Then
            assertThat(corsConfig).isNotNull
            assertThat(corsConfig.allowedHeaders).isNotNull
            assertThat(corsConfig.allowedHeaders!!).contains("*")
        }

        @Test
        fun `should allow credentials (cookies, auth headers)`() {
            // Given
            val request =
                MockHttpServletRequest().apply {
                    requestURI = "/api/widgets"
                }
            val corsConfig = corsSource.getCorsConfiguration(request)

            // Then
            assertThat(corsConfig).isNotNull
            assertThat(corsConfig.allowCredentials).isTrue()
        }

        @Test
        fun `should apply CORS configuration to all endpoints`() {
            // Given - Test various endpoint patterns
            val endpoints = listOf("/api/widgets", "/api/widgets/123", "/health", "/actuator/prometheus")

            // When/Then - All endpoints should get same CORS config
            endpoints.forEach { endpoint ->
                val request =
                    MockHttpServletRequest().apply {
                        requestURI = endpoint
                    }
                val corsConfig = corsSource.getCorsConfiguration(request)
                assertThat(corsConfig).isNotNull
                assertThat(corsConfig.allowedOrigins).isNotNull
                assertThat(corsConfig.allowedOrigins!!).contains("http://localhost:3000")
            }
        }
    }
}
