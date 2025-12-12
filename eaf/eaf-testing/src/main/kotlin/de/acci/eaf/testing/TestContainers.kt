package de.acci.eaf.testing

import dasniko.testcontainers.keycloak.KeycloakContainer
import de.acci.eaf.testing.vcsim.VcsimCertificateGenerator
import de.acci.eaf.testing.vcsim.VcsimContainer
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Central registry for TestContainers used in integration tests.
 *
 * Manages singleton containers for PostgreSQL, Keycloak, and VCSIM to speed up
 * test execution (containers are started once and reused).
 */
public object TestContainers {

    /**
     * Shared PostgreSQL container for integration tests (reused).
     *
     * Note: .withReuse(true) requires 'testcontainers.reuse.enable=true' in ~/.testcontainers.properties.
     * Uses 'eaf_test' database to avoid collisions with other projects.
     */
    public val postgres: PostgreSQLContainer by lazy {
        PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("eaf_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
            .apply { start() }
    }

    /** Shared Keycloak container for integration tests (reused). */
    public val keycloak: KeycloakContainer by lazy {
        KeycloakContainer("quay.io/keycloak/keycloak:26.0")
            .withRealmImportFile("/test-realm.json")
            .withReuse(true)
            .apply { start() }
    }

    /**
     * VMware vCenter Simulator (VCSIM) container for integration testing.
     *
     * Provides a vSphere API-compatible `/sdk` endpoint with default configuration:
     * - 2 clusters, 4 hosts per cluster, 10 VMs per host (80 total VMs)
     * - Default credentials: user/pass
     * - Generated TLS certificates for secure connections (no trust-all required)
     *
     * Usage:
     * ```kotlin
     * val sdkUrl = TestContainers.vcsim.getSdkUrl()
     * val username = TestContainers.vcsim.getUsername()
     * val password = TestContainers.vcsim.getPassword()
     *
     * // For secure HTTP clients:
     * val sslContext = TestContainers.vcsim.getCertificateBundle()?.createSslContext()
     * ```
     */
    public val vcsim: VcsimContainer by lazy {
        val certificateBundle = VcsimCertificateGenerator.generate()
        VcsimContainer.create()
            .withCertificates(certificateBundle)
            .apply { start() }
    }

    private val eventStoreSchemaInitialized = java.util.concurrent.atomic.AtomicBoolean(false)
    private val eventStoreRlsInitialized = java.util.concurrent.atomic.AtomicBoolean(false)
    private val flywayMigrationsInitialized = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Sets up the event store schema if not already initialized.
     * This is thread-safe and idempotent - multiple calls will only initialize once.
     * Drops existing schema to ensure clean state (since containers are reused).
     *
     * Uses synchronized block to prevent race condition where RLS setup could run
     * before schema creation completes in concurrent test execution.
     *
     * @param migrationSqlSupplier Function that returns the migration SQL content
     */
    public fun ensureEventStoreSchema(migrationSqlSupplier: () -> String) {
        synchronized(this) {
            if (!eventStoreSchemaInitialized.compareAndSet(false, true)) {
                return
            }
            postgres.createConnection("").use { conn ->
                // Drop existing schema to ensure clean state (containers are reused)
                conn.createStatement().use { stmt ->
                    stmt.execute("DROP SCHEMA IF EXISTS eaf_events CASCADE")
                    stmt.execute(migrationSqlSupplier())
                }
            }
        }
    }

    /**
     * Sets up the event store schema with RLS enabled if not already initialized.
     * This is thread-safe and idempotent - multiple calls will only initialize once.
     *
     * Uses synchronized block to ensure RLS setup waits for schema creation to complete.
     * This prevents race condition in parallel test execution.
     *
     * @param migrationSqlSupplier Function that returns the migration SQL content
     */
    public fun ensureEventStoreSchemaWithRls(migrationSqlSupplier: () -> String) {
        synchronized(this) {
            ensureEventStoreSchema(migrationSqlSupplier)
            if (!eventStoreRlsInitialized.compareAndSet(false, true)) {
                return
            }
            postgres.createConnection("").use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(
                        """
                        ALTER TABLE eaf_events.events ENABLE ROW LEVEL SECURITY;
                        DROP POLICY IF EXISTS tenant_isolation_events ON eaf_events.events;
                        CREATE POLICY tenant_isolation_events ON eaf_events.events
                            USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::UUID);
                        ALTER TABLE eaf_events.events FORCE ROW LEVEL SECURITY;
    
                        -- Create eaf_app role for RLS tests if it doesn't exist
                        DO $$
                        BEGIN
                            IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'eaf_app') THEN
                                CREATE ROLE eaf_app NOINHERIT;
                            END IF;
                        END
                        $$;
                        GRANT USAGE ON SCHEMA eaf_events TO eaf_app;
                        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA eaf_events TO eaf_app;
                        """.trimIndent()
                    )
                }
            }
        }
    }

    /**
     * Sets up the database schema using Flyway migrations from classpath.
     *
     * This is the preferred method for integration tests as it uses the same
     * migrations that run in production, eliminating schema synchronization issues.
     *
     * Migrations are loaded from `classpath:db/migration` which includes:
     * - EAF framework migrations (eaf-eventsourcing module)
     * - Product-specific migrations (e.g., dcm-infrastructure module)
     *
     * Thread-safe and idempotent - multiple calls will only initialize once.
     * Uses Flyway's clean + migrate pattern to ensure consistent state across
     * reused containers.
     */
    public fun ensureFlywayMigrations() {
        synchronized(this) {
            if (!flywayMigrationsInitialized.compareAndSet(false, true)) {
                return
            }
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .cleanDisabled(false) // Allow clean for test isolation
                .load()
                .apply {
                    clean() // Start fresh for reused containers
                    migrate()
                }
        }
    }
}
