package de.acci.eaf.testing

import dasniko.testcontainers.keycloak.KeycloakContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

public object TestContainers {

    public val postgres: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("dvmm_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
            .apply { start() }
    }

    public val keycloak: KeycloakContainer by lazy {
        KeycloakContainer("quay.io/keycloak/keycloak:26.0")
            .withRealmImportFile("/test-realm.json")
            .withReuse(true)
            .apply { start() }
    }

    private val eventStoreSchemaInitialized = java.util.concurrent.atomic.AtomicBoolean(false)
    private val eventStoreRlsInitialized = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Sets up the event store schema if not already initialized.
     * This is thread-safe and idempotent - multiple calls will only initialize once.
     * Drops existing schema to ensure clean state (since containers are reused).
     *
     * @param migrationSqlSupplier Function that returns the migration SQL content
     */
    public fun ensureEventStoreSchema(migrationSqlSupplier: () -> String) {
        if (eventStoreSchemaInitialized.compareAndSet(false, true)) {
            postgres.createConnection("").use { conn ->
                // Drop existing schema to ensure clean state (containers are reused)
                conn.createStatement().execute("DROP SCHEMA IF EXISTS eaf_events CASCADE")
                conn.createStatement().execute(migrationSqlSupplier())
            }
        }
    }

    /**
     * Sets up the event store schema with RLS enabled if not already initialized.
     * This is thread-safe and idempotent - multiple calls will only initialize once.
     *
     * @param migrationSqlSupplier Function that returns the migration SQL content
     */
    public fun ensureEventStoreSchemaWithRls(migrationSqlSupplier: () -> String) {
        ensureEventStoreSchema(migrationSqlSupplier)
        if (eventStoreRlsInitialized.compareAndSet(false, true)) {
            postgres.createConnection("").use { conn ->
                conn.createStatement().execute(
                    """
                    ALTER TABLE eaf_events.events ENABLE ROW LEVEL SECURITY;
                    DROP POLICY IF EXISTS tenant_isolation_events ON eaf_events.events;
                    CREATE POLICY tenant_isolation_events ON eaf_events.events
                        USING (tenant_id = current_setting('app.tenant_id', true)::UUID);
                    ALTER TABLE eaf_events.events FORCE ROW LEVEL SECURITY;
                    """.trimIndent()
                )
            }
        }
    }
}
