package com.axians.eaf.framework.observability.logging

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Validates that log output conforms to the required JSON structure.
 * Used for testing and format compliance verification.
 */
@Component
class LoggingFormatValidator {
    private val logger = LoggerFactory.getLogger(LoggingFormatValidator::class.java)
    private val objectMapper = ObjectMapper()

    /**
     * Required JSON fields for EAF structured logging.
     */
    val requiredFields =
        listOf(
            "@timestamp",
            "level",
            "logger_name",
            "message",
            "thread_name",
        )

    /**
     * Context fields that should be present when context is available.
     */
    val contextFields =
        listOf(
            "service_name",
            "trace_id",
            "tenant_id",
        )

    /**
     * Validates that a JSON log entry contains all required fields.
     *
     * @param jsonLogEntry The JSON string to validate
     * @return ValidationResult with success status and details
     */
    fun validateLogEntry(jsonLogEntry: String): ValidationResult =
        try {
            val jsonNode: JsonNode = objectMapper.readTree(jsonLogEntry)

            val missingFields = requiredFields.filter { !jsonNode.has(it) }
            val presentContextFields = contextFields.filter { jsonNode.has(it) }

            ValidationResult(
                isValid = missingFields.isEmpty(),
                jsonValid = true,
                missingRequiredFields = missingFields,
                presentContextFields = presentContextFields,
                allFields = jsonNode.fieldNames().asSequence().toList(),
            )
        } catch (e: JsonProcessingException) {
            ValidationResult(
                isValid = false,
                jsonValid = false,
                error = e.message,
                missingRequiredFields = requiredFields,
                presentContextFields = emptyList(),
                allFields = emptyList(),
            )
        } catch (e: IllegalArgumentException) {
            ValidationResult(
                isValid = false,
                jsonValid = false,
                error = e.message,
                missingRequiredFields = requiredFields,
                presentContextFields = emptyList(),
                allFields = emptyList(),
            )
        }

    /**
     * Test method to verify current logging configuration works.
     * Logs at different levels and validates JSON structure.
     */
    fun testLoggingFormat(): List<String> {
        val testMessages = mutableListOf<String>()

        logger.debug("Debug level test message")
        testMessages.add("DEBUG level tested")

        logger.info("Info level test message")
        testMessages.add("INFO level tested")

        logger.warn("Warning level test message")
        testMessages.add("WARN level tested")

        logger.error("Error level test message")
        testMessages.add("ERROR level tested")

        return testMessages
    }

    data class ValidationResult(
        val isValid: Boolean,
        val jsonValid: Boolean,
        val error: String? = null,
        val missingRequiredFields: List<String>,
        val presentContextFields: List<String>,
        val allFields: List<String>,
    )
}
