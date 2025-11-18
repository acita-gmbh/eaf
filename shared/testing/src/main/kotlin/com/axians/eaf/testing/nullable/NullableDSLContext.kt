package com.axians.eaf.testing.nullable

import org.jooq.Configuration
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Result
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Nullable DSLContext implementation for fast unit testing (Story 2.8).
 *
 * Provides an in-memory, zero-latency DSLContext using H2 database (in-memory mode).
 * Follows the Nullable Design Pattern for 100-1000x performance improvement over
 * integration tests while preserving business logic validation.
 *
 * **IMPORTANT:** This is an exception to the "H2 forbidden" rule - H2 is used ONLY
 * for Nullable Pattern unit tests with minimal schema. Integration tests MUST still
 * use Testcontainers PostgreSQL.
 *
 * **Factory Pattern:** Use `createNull()` to instantiate.
 *
 * **Performance:** In-memory database, sub-millisecond query execution.
 *
 * **Use Cases:**
 * - Unit testing query handlers without Testcontainers overhead
 * - Validating query logic, cursor encoding, pagination calculations
 * - Fast TDD red-green-refactor cycles
 *
 * **Limitations:**
 * - H2 SQL dialect differs from PostgreSQL (use integration tests for SQL validation)
 * - No production schema (minimal test schema only)
 * - Transaction behavior simplified
 *
 * **Contract Testing:** Integration tests with Testcontainers validate behavioral parity.
 *
 * @see createNullableDSLContext
 */
object NullableDSLContext {
    /**
     * Factory method following Nullable Design Pattern.
     *
     * Creates fast, zero-latency DSLContext with in-memory H2 database.
     * Initializes minimal widget_projection schema.
     *
     * **Example Usage:**
     * ```kotlin
     * test("query handler returns projection") {
     *     val dsl = createNullableDSLContext()
     *     val handler = WidgetQueryHandler(dsl)
     *
     *     val result = handler.handle(FindWidgetQuery(widgetId))
     *
     *     result.shouldNotBeNull()
     * }
     * ```
     *
     * @return DSLContext with in-memory H2 database
     */
    fun createNull(): DSLContext {
        // Create in-memory H2 database
        val connection: Connection =
            DriverManager.getConnection(
                "jdbc:h2:mem:test_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "sa",
                "",
            )

        val dsl = DSL.using(connection, SQLDialect.H2)

        // Initialize minimal schema for widget_projection
        // Story 4.6: Added tenant_id column for multi-tenancy testing
        dsl.execute(
            """
            CREATE TABLE IF NOT EXISTS widget_projection (
                id UUID PRIMARY KEY,
                tenant_id VARCHAR(64) NOT NULL DEFAULT 'test-tenant',
                name VARCHAR(255) NOT NULL,
                published BOOLEAN NOT NULL DEFAULT false,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
            )
            """.trimIndent(),
        )

        // Insert sample test data using jOOQ DSL
        val now = Instant.now().atOffset(ZoneOffset.UTC)
        dsl
            .insertInto(DSL.table("widget_projection"))
            .columns(
                DSL.field("id"),
                DSL.field("tenant_id"),
                DSL.field("name"),
                DSL.field("published"),
                DSL.field("created_at"),
                DSL.field("updated_at"),
            ).values(
                UUID.randomUUID(),
                "test-tenant",
                "Nullable Test Widget",
                false,
                now,
                now,
            ).execute()

        return dsl
    }
}

/**
 * Global factory function for Nullable DSLContext.
 *
 * Provides convenient access to Nullable DSLContext without importing the class.
 *
 * **Performance:** Sub-millisecond query execution with in-memory H2.
 *
 * **H2 Exception:** This is the ONLY approved use of H2 in EAF - for Nullable Pattern unit tests.
 * Integration tests MUST use Testcontainers PostgreSQL.
 *
 * @return Fast, zero-latency DSLContext for unit testing
 */
fun createNullableDSLContext(): DSLContext = NullableDSLContext.createNull()
