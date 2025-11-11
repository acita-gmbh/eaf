package com.axians.eaf.framework.web.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * REST API configuration for EAF framework (Story 2.9).
 *
 * Configures:
 * - **Jackson ObjectMapper**: JSON serialization with Kotlin support, ISO-8601 dates, null exclusion
 * - **CORS**: Cross-Origin Resource Sharing for React dev server (localhost:3000)
 *
 * **Jackson Configuration:**
 * - Kotlin data classes with default parameter handling
 * - Java 8 time types (Instant, LocalDateTime) as ISO-8601 strings (NOT timestamps)
 * - Null fields excluded from JSON output
 *
 * **CORS Configuration:**
 * - Development: Allows localhost:3000 (React dev server)
 * - Production: Override via application.yml (Spring Boot CORS properties)
 *
 * **References:**
 * - Architecture: Section 15 (API Contracts - JSON Serialization)
 * - Tech Spec: Section 5.3 (REST Configuration)
 *
 * @see ObjectMapper
 * @see CorsConfigurationSource
 */
@Configuration
open class RestConfiguration {
    /**
     * Configures Jackson ObjectMapper with EAF standards (Story 2.9).
     *
     * **Features:**
     * - Kotlin module: Proper handling of data classes, default parameters, null safety
     * - Java Time module: ISO-8601 serialization for Instant, LocalDateTime, etc.
     * - Null exclusion: Fields with null values omitted from JSON output
     * - Timestamp format: Dates as ISO-8601 strings (NOT numeric timestamps)
     *
     * **Example Output:**
     * ```json
     * {
     *   "id": "widget-123",
     *   "name": "Production Widget",
     *   "createdAt": "2025-11-07T10:30:00Z",
     *   "description": null  <-- excluded because NON_NULL
     * }
     * ```
     *
     * **Why ISO-8601 over timestamps?**
     * - Human-readable in logs and API responses
     * - No timezone ambiguity
     * - RFC 3339 compliant (REST API standard)
     * - Frontend-friendly (native Date parsing)
     *
     * @return Configured Jackson ObjectMapper
     */
    @Bean
    open fun objectMapper(): ObjectMapper =
        Jackson2ObjectMapperBuilder()
            // Exclude null fields from JSON output (cleaner responses)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            // Dates as ISO-8601 strings (not numeric timestamps)
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // Register Kotlin + Java Time support
            .modules(
                JavaTimeModule(), // Instant, LocalDateTime, etc.
                kotlinModule(), // Kotlin data classes
            ).build()

    /**
     * Configures CORS (Cross-Origin Resource Sharing) for development (Story 2.9).
     *
     * **Development Settings:**
     * - Allowed Origins: localhost:3000 (React dev server)
     * - Allowed Methods: GET, POST, PUT, DELETE, PATCH
     * - Allowed Headers: * (all headers)
     * - Credentials: true (cookies/auth headers)
     *
     * **Production Override:**
     * Override via application.yml:
     * ```yaml
     * spring:
     *   web:
     *     cors:
     *       allowed-origins: https://production.example.com
     *       allowed-methods: GET,POST,PUT,DELETE
     * ```
     *
     * **Security Note:**
     * - Development only! Production should use specific domain whitelist.
     * - Never use `allowedOrigins = ["*"]` with `allowCredentials = true`
     *
     * @return CORS configuration source
     */
    @Bean
    open fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()

        // Development: React dev server on localhost:3000
        configuration.allowedOrigins = listOf("http://localhost:3000")

        // Standard HTTP methods for REST APIs
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH")

        // Allow all headers (simplifies development)
        // Production: Restrict to specific headers (Authorization, Content-Type, etc.)
        configuration.allowedHeaders = listOf("*")

        // Allow cookies and authentication headers
        configuration.allowCredentials = true

        // Apply CORS to all endpoints
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)

        return source
    }
}
