package de.acci.eaf.testing

import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext

@Target(AnnotationTarget.CLASS)
@ExtendWith(EventStoreIsolationExtension::class)
public annotation class IsolatedEventStore(
    val strategy: IsolationStrategy = IsolationStrategy.TRUNCATE
)

public enum class IsolationStrategy {
    TRUNCATE,
    SCHEMA_PER_TEST
}

public class EventStoreIsolationExtension : BeforeEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        val annotation = context.requiredTestClass.getAnnotation(IsolatedEventStore::class.java)
        val strategy = annotation?.strategy ?: IsolationStrategy.TRUNCATE

        when (strategy) {
            IsolationStrategy.TRUNCATE -> truncateEventStore()
            IsolationStrategy.SCHEMA_PER_TEST -> {
                // TODO: Implement SCHEMA_PER_TEST
            }
        }
    }

    private fun truncateEventStore() {
        TestContainers.postgres.createConnection("").use { conn ->
            conn.createStatement().execute(
                "TRUNCATE TABLE eaf_events.events, eaf_events.snapshots RESTART IDENTITY CASCADE"
            )
        }
    }
}
