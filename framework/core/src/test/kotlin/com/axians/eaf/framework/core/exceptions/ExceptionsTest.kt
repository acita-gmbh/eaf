package com.axians.eaf.framework.core.exceptions

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for EAF exception hierarchy.
 *
 * Validates:
 * - Exception creation with message and cause
 * - Inheritance relationships
 * - Polymorphic exception handling
 * - Message and cause propagation
 * - Stack trace preservation
 */
class ExceptionsTest :
    FunSpec({

        test("should create EafException with message") {
            val exception = EafException("Test error")

            exception.message shouldBe "Test error"
            exception.cause shouldBe null
        }

        test("should create EafException with message and cause") {
            val cause = IllegalStateException("Root cause")
            val exception = EafException("Test error", cause)

            exception.message shouldBe "Test error"
            exception.cause shouldBe cause
        }

        test("should create ValidationException with message") {
            val exception = ValidationException("Validation failed")

            exception.message shouldBe "Validation failed"
        }

        test("should create TenantIsolationException with message") {
            val exception = TenantIsolationException("Tenant isolation violated")

            exception.message shouldBe "Tenant isolation violated"
        }

        test("should create AggregateNotFoundException with aggregateId and type") {
            val exception =
                AggregateNotFoundException(
                    aggregateId = "widget-123",
                    aggregateType = "Widget",
                )

            exception.message shouldBe "Aggregate Widget with ID widget-123 not found"
        }

        test("should be catchable as EafException (polymorphism)") {
            val exception: EafException = ValidationException("Test")

            exception.shouldBeInstanceOf<ValidationException>()
            exception.shouldBeInstanceOf<EafException>()
        }

        test("should be catchable as RuntimeException") {
            val exception: RuntimeException = EafException("Test")

            exception.shouldBeInstanceOf<EafException>()
            exception.shouldBeInstanceOf<RuntimeException>()
        }

        test("should preserve stack trace") {
            val exception =
                shouldThrow<ValidationException> {
                    throw ValidationException("Test error")
                }

            exception.stackTrace shouldNotBe null
            exception.stackTrace.isNotEmpty() shouldBe true
        }

        test("should support cause chain") {
            val rootCause = IllegalArgumentException("Root")
            val middleCause = ValidationException("Middle", rootCause)
            val topException = EafException("Top", middleCause)

            topException.cause shouldBe middleCause
            topException.cause?.cause shouldBe rootCause
        }

        test("should allow all exception types in polymorphic collections") {
            val exceptions: List<EafException> =
                listOf(
                    ValidationException("Validation failed"),
                    TenantIsolationException("Tenant violation"),
                    AggregateNotFoundException("agg-123", "Widget"),
                    EafException("Generic error"),
                )

            exceptions.size shouldBe 4
            exceptions[0].shouldBeInstanceOf<ValidationException>()
            exceptions[1].shouldBeInstanceOf<TenantIsolationException>()
            exceptions[2].shouldBeInstanceOf<AggregateNotFoundException>()
            exceptions[3].shouldBeInstanceOf<EafException>()
        }

        @Suppress("SwallowedException")
        test("should support try-catch with specific exception types") {
            val result =
                try {
                    throw ValidationException("Validation error")
                } catch (e: ValidationException) {
                    "caught-validation"
                } catch (e: EafException) {
                    "caught-eaf"
                }

            result shouldBe "caught-validation"
        }

        @Suppress("SwallowedException")
        test("should support try-catch with base exception type") {
            val result =
                try {
                    throw TenantIsolationException("Tenant error")
                } catch (e: EafException) {
                    "caught-eaf"
                }

            result shouldBe "caught-eaf"
        }
    })
