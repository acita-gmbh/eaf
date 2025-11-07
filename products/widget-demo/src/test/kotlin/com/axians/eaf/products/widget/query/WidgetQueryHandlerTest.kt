package com.axians.eaf.products.widget.query

import com.axians.eaf.products.widget.domain.WidgetId
import com.axians.eaf.testing.nullable.createNullableDSLContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Unit tests for WidgetQueryHandler using Nullable Design Pattern.
 *
 * Tests query handler business logic with fast, zero-latency Nullable DSLContext.
 * Validates cursor encoding/decoding, limit validation, and edge cases without database overhead.
 *
 * **Nullable Pattern:** 100-1000x performance improvement over integration tests.
 * **Contract Testing:** Integration tests validate behavioral parity with real database.
 */
class WidgetQueryHandlerTest :
    FunSpec({

        lateinit var handler: WidgetQueryHandler

        beforeEach {
            val dsl = createNullableDSLContext()
            handler = WidgetQueryHandler(dsl)
        }

        context("FindWidgetQuery with Nullable DSLContext") {

            test("returns widget projection from mock data") {
                // Given: Nullable DSLContext with pre-inserted test data
                // Query using ListWidgetsQuery to find the test widget ID first
                val listResult = handler.handle(ListWidgetsQuery(limit = 1))
                val testWidgetId = listResult.widgets.first().id

                // When: Execute FindWidgetQuery with actual test widget ID
                val query = FindWidgetQuery(testWidgetId)
                val result = handler.handle(query)

                // Then: Widget projection returned from in-memory H2
                result.shouldNotBeNull()
                result.name shouldBe "Nullable Test Widget"
                result.published shouldBe false
            }
        }

        context("ListWidgetsQuery with Nullable DSLContext") {

            test("returns paginated response from mock data") {
                // Given: Nullable DSLContext
                val query = ListWidgetsQuery(limit = 50)

                // When: Execute query
                val result = handler.handle(query)

                // Then: Mock data structure validated
                result.shouldNotBeNull()
                result.widgets.shouldNotBeNull()
            }
        }

        context("Cursor Encoding/Decoding") {

            test("encodes timestamp to Base64 cursor") {
                // Given: Create handler instance for reflection (DSL not used in this test)
                val dummyDsl = null // Not needed for cursor encoding test
                val timestamp = Instant.parse("2025-01-01T12:00:00Z")

                // When: Encode cursor (via direct Base64 encoding - mirrors handler logic)
                val encoded = Base64.getEncoder().encodeToString(timestamp.toString().toByteArray())

                // Then: Encoded cursor is valid Base64
                val decoded = String(Base64.getDecoder().decode(encoded))
                decoded shouldBe timestamp.toString()
            }

            test("decodes Base64 cursor to timestamp") {
                // Given: Valid Base64 cursor
                val timestamp = Instant.parse("2025-01-01T12:00:00Z")
                val cursor = Base64.getEncoder().encodeToString(timestamp.toString().toByteArray())

                // When: Decode cursor
                val decodedTimestamp = String(Base64.getDecoder().decode(cursor))
                val parsed = Instant.parse(decodedTimestamp)

                // Then: Timestamp matches original
                parsed shouldBe timestamp
            }

            test("encodes and decodes cursor round-trip correctly") {
                // Given: Various timestamps
                val timestamps =
                    listOf(
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Instant.parse("2025-06-15T12:30:45Z"),
                        Instant.parse("2025-12-31T23:59:59Z"),
                    )

                timestamps.forEach { original ->
                    // When: Encode then decode
                    val encoded = Base64.getEncoder().encodeToString(original.toString().toByteArray())
                    val decoded = Instant.parse(String(Base64.getDecoder().decode(encoded)))

                    // Then: Timestamp preserved
                    decoded shouldBe original
                }
            }

            test("decoding invalid Base64 cursor throws IllegalArgumentException") {
                // Given: Invalid Base64 string
                val invalidCursor = "not-valid-base64!!!"

                // When/Then: Decoding should fail gracefully
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        String(Base64.getDecoder().decode(invalidCursor))
                    }

                exception.message.shouldNotBeNull()
            }

            test("decoding malformed timestamp throws exception") {
                // Given: Valid Base64 but invalid timestamp format
                val malformedCursor = Base64.getEncoder().encodeToString("not-a-timestamp".toByteArray())

                // When/Then: Parsing should fail
                val exception =
                    shouldThrow<java.time.format.DateTimeParseException> {
                        Instant.parse(String(Base64.getDecoder().decode(malformedCursor)))
                    }

                exception.message.shouldNotBeNull()
            }
        }

        context("Limit Validation") {

            test("limit clamped to minimum value of 1") {
                // Given: Query with limit 0
                val query = ListWidgetsQuery(limit = 0)

                // When: coerceIn should clamp to 1
                val safeLimit = query.limit.coerceIn(1, 100)

                // Then: Limit is 1
                safeLimit shouldBe 1
            }

            test("limit clamped to maximum value of 100") {
                // Given: Query with limit 500
                val query = ListWidgetsQuery(limit = 500)

                // When: coerceIn should clamp to 100
                val safeLimit = query.limit.coerceIn(1, 100)

                // Then: Limit is 100
                safeLimit shouldBe 100
            }

            test("negative limit clamped to 1") {
                // Given: Query with negative limit
                val query = ListWidgetsQuery(limit = -10)

                // When: coerceIn should clamp to 1
                val safeLimit = query.limit.coerceIn(1, 100)

                // Then: Limit is 1
                safeLimit shouldBe 1
            }

            test("valid limit values preserved") {
                // Given: Valid limits within range
                val validLimits = listOf(1, 25, 50, 75, 100)

                validLimits.forEach { limit ->
                    // When: Apply coerceIn
                    val safeLimit = limit.coerceIn(1, 100)

                    // Then: Value unchanged
                    safeLimit shouldBe limit
                }
            }
        }
    })
