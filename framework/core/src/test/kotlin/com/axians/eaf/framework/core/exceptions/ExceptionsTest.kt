package com.axians.eaf.framework.core.exceptions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for EAF exception hierarchy - standardized error handling.
 *
 * Validates the custom exception types in EAF's exception hierarchy, ensuring proper
 * exception construction, inheritance relationships, and cause chain propagation.
 *
 * **Test Coverage:**
 * - Exception creation with message and optional cause
 * - Inheritance relationships (DomainException, ValidationException, etc.)
 * - Polymorphic exception handling (catch by base type)
 * - Message and cause propagation through exception chain
 * - Stack trace preservation for debugging
 * - Exception type discrimination
 *
 * **Exception Patterns:**
 * - Domain exceptions for business rule violations
 * - Technical exceptions for infrastructure failures
 * - Validation exceptions for input validation
 * - Proper exception chaining (cause preservation)
 *
 * @see DomainException Base exception for domain errors
 * @see ValidationException Input validation failures
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
 */
class ExceptionsTest {

    @Test
    fun `should create EafException with message`() {
        val exception = EafException("Test error")

        assertThat(exception.message).isEqualTo("Test error")
        assertThat(exception.cause).isNull()
    }

    @Test
    fun `should create EafException with message and cause`() {
        val cause = IllegalStateException("Root cause")
        val exception = EafException("Test error", cause)

        assertThat(exception.message).isEqualTo("Test error")
        assertThat(exception.cause).isEqualTo(cause)
    }

    @Test
    fun `should create ValidationException with message`() {
        val exception = ValidationException("Validation failed")

        assertThat(exception.message).isEqualTo("Validation failed")
    }

    @Test
    fun `should create TenantIsolationException with message`() {
        val exception = TenantIsolationException("Tenant isolation violated")

        assertThat(exception.message).isEqualTo("Tenant isolation violated")
    }

    @Test
    fun `should create AggregateNotFoundException with aggregateId and type`() {
        val exception =
            AggregateNotFoundException(
                aggregateId = "widget-123",
                aggregateType = "Widget",
            )

        assertThat(exception.message).isEqualTo("Aggregate Widget with ID widget-123 not found")
    }

    @Test
    fun `should be catchable as EafException (polymorphism)`() {
        val exception: EafException = ValidationException("Test")

        assertThat(exception).isInstanceOf(ValidationException::class.java)
        assertThat(exception).isInstanceOf(EafException::class.java)
    }

    @Test
    fun `should be catchable as RuntimeException`() {
        val exception: RuntimeException = EafException("Test")

        assertThat(exception).isInstanceOf(EafException::class.java)
        assertThat(exception).isInstanceOf(RuntimeException::class.java)
    }

    @Test
    fun `should preserve stack trace`() {
        val exception =
            assertThrows<ValidationException> {
                throw ValidationException("Test error")
            }

        assertThat(exception.stackTrace).isNotNull()
        assertThat(exception.stackTrace).isNotEmpty()
    }

    @Test
    fun `should support cause chain`() {
        val rootCause = IllegalArgumentException("Root")
        val middleCause = ValidationException("Middle", rootCause)
        val topException = EafException("Top", middleCause)

        assertThat(topException.cause).isEqualTo(middleCause)
        assertThat(topException.cause?.cause).isEqualTo(rootCause)
    }

    @Test
    fun `should allow all exception types in polymorphic collections`() {
        val exceptions: List<EafException> =
            listOf(
                ValidationException("Validation failed"),
                TenantIsolationException("Tenant violation"),
                AggregateNotFoundException("agg-123", "Widget"),
                EafException("Generic error"),
            )

        assertThat(exceptions).hasSize(4)
        assertThat(exceptions[0]).isInstanceOf(ValidationException::class.java)
        assertThat(exceptions[1]).isInstanceOf(TenantIsolationException::class.java)
        assertThat(exceptions[2]).isInstanceOf(AggregateNotFoundException::class.java)
        assertThat(exceptions[3]).isInstanceOf(EafException::class.java)
    }

    @Test
    @Suppress("SwallowedException")
    fun `should support try-catch with specific exception types`() {
        val result =
            try {
                throw ValidationException("Validation error")
            } catch (e: ValidationException) {
                "caught-validation"
            } catch (e: EafException) {
                "caught-eaf"
            }

        assertThat(result).isEqualTo("caught-validation")
    }

    @Test
    @Suppress("SwallowedException")
    fun `should support try-catch with base exception type`() {
        val result =
            try {
                throw TenantIsolationException("Tenant error")
            } catch (e: EafException) {
                "caught-eaf"
            }

        assertThat(result).isEqualTo("caught-eaf")
    }
}
