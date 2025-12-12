package de.acci.eaf.testing

import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit 5 extension to isolate the Event Store between tests.
 *
 * Ensures a clean state for each test method by truncating tables or
 * using other isolation strategies. Crucial for reliable integration tests
 * against a real PostgreSQL database.
 */
@Target(AnnotationTarget.CLASS)
@ExtendWith(EventStoreIsolationExtension::class)
public annotation class IsolatedEventStore(
    /** The isolation strategy to use (defaults to TRUNCATE) */
    val strategy: IsolationStrategy = IsolationStrategy.TRUNCATE
)

/**
 * Strategy for isolating database state between tests.
 */
public enum class IsolationStrategy {
    /** Truncates all event tables (fastest for reused containers) */
    TRUNCATE,
    /** Creates a new schema per test (not yet implemented) */
    SCHEMA_PER_TEST
}

/**
 * Extension that implements the isolation logic.
 *
 * Hooks into the test lifecycle (beforeEach) to clean up the database.
 * Note: Database truncation does not guarantee isolation if tests run in parallel
 * against the same database container. Ensure tests using this extension run sequentially
 * or use separate database instances.
 */
public class EventStoreIsolationExtension : BeforeEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        val strategy = context.testMethod
            .map { it.getAnnotation(IsolatedEventStore::class.java)?.strategy }
            .orElse(null)
            ?: context.requiredTestClass.getAnnotation(IsolatedEventStore::class.java)?.strategy
            ?: IsolationStrategy.TRUNCATE

        when (strategy) {
            IsolationStrategy.TRUNCATE -> truncateEventStore()
            IsolationStrategy.SCHEMA_PER_TEST -> {
                throw UnsupportedOperationException("IsolationStrategy.SCHEMA_PER_TEST is not yet implemented. Use IsolationStrategy.TRUNCATE instead.")
            }
        }
    }

    private fun truncateEventStore() {
        TestContainers.postgres.createConnection("").use { conn ->
            conn.autoCommit = true
            // Check which tables exist and truncate only those
            val tables = mutableListOf<String>()
            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                    """
                    SELECT table_name FROM information_schema.tables
                    WHERE table_schema = 'eaf_events'
                    AND table_name IN ('events', 'snapshots')
                    """.trimIndent()
                ).use { rs ->
                    while (rs.next()) {
                        tables.add("eaf_events.${rs.getString("table_name")}")
                    }
                }
            }
            if (tables.isNotEmpty()) {
                conn.createStatement().use { stmt ->
                    stmt.execute("TRUNCATE TABLE ${tables.joinToString(", ")} RESTART IDENTITY CASCADE")
                }
            }
        }
    }
}
