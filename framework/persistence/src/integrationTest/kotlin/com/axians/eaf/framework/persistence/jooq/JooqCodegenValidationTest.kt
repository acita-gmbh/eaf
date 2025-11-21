package com.axians.eaf.framework.persistence.jooq

import com.axians.eaf.framework.persistence.jooq.tables.WidgetView.Companion.WIDGET_VIEW
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * Standalone Integration test for jOOQ code generation validation.
 *
 * Validates acceptance criteria without Spring Boot overhead:
 * - AC5: Generated WIDGET_VIEW table class has column accessors
 * - AC7: Type-safe jOOQ queries execute successfully
 * - AC8: jOOQ code generation task produces usable Kotlin classes
 *
 * Uses Testcontainers PostgreSQL directly (no Spring context).
 */
class JooqCodegenValidationTest {
    @BeforeEach
    fun beforeEach() {
        // Clean table before each test to avoid data pollution
        dslContext.deleteFrom(WIDGET_VIEW).execute()
    }

    @Test
    fun `AC5 - Generated WIDGET_VIEW table class should have all column accessors`() {
        // Verify generated jOOQ class structure
        assertThat(WIDGET_VIEW).isNotNull()
        assertThat(WIDGET_VIEW.ID).isNotNull()
        assertThat(WIDGET_VIEW.NAME).isNotNull()
        assertThat(WIDGET_VIEW.PUBLISHED).isNotNull()
        assertThat(WIDGET_VIEW.CREATED_AT).isNotNull()
        assertThat(WIDGET_VIEW.UPDATED_AT).isNotNull()
    }

    @Test
    fun `AC7 - Type-safe jOOQ INSERT should persist data successfully`() {
        // Given: A widget projection record
        val widgetId = UUID.randomUUID()
        val widgetName = "Test Widget"
        val now = OffsetDateTime.now()

        // When: Insert using type-safe jOOQ DSL
        val insertCount =
            dslContext
                .insertInto(WIDGET_VIEW)
                .columns(
                    WIDGET_VIEW.ID,
                    WIDGET_VIEW.NAME,
                    WIDGET_VIEW.PUBLISHED,
                    WIDGET_VIEW.CREATED_AT,
                    WIDGET_VIEW.UPDATED_AT,
                ).values(widgetId, widgetName, false, now, now)
                .execute()

        // Then: Insert should succeed
        assertThat(insertCount).isEqualTo(1)
    }

    @Test
    fun `AC7 - Type-safe jOOQ SELECT should retrieve data successfully`() {
        // Given: An existing widget record
        val widgetId = UUID.randomUUID()
        val widgetName = "Query Test Widget"
        val now = OffsetDateTime.now()

        dslContext
            .insertInto(WIDGET_VIEW)
            .columns(
                WIDGET_VIEW.ID,
                WIDGET_VIEW.NAME,
                WIDGET_VIEW.PUBLISHED,
                WIDGET_VIEW.CREATED_AT,
                WIDGET_VIEW.UPDATED_AT,
            ).values(widgetId, widgetName, true, now, now)
            .execute()

        // When: Query using type-safe jOOQ DSL
        val result =
            dslContext
                .selectFrom(WIDGET_VIEW)
                .where(WIDGET_VIEW.ID.eq(widgetId))
                .fetchOne()

        // Then: Query should return the inserted record
        assertThat(result).isNotNull()
        assertThat(result!!.id).isEqualTo(widgetId)
        assertThat(result.name).isEqualTo(widgetName)
        assertThat(result.published).isEqualTo(true)
    }

    @Test
    fun `AC7 - Type-safe jOOQ WHERE clause should filter correctly`() {
        // Given: Multiple widget records with different published status
        val publishedId = UUID.randomUUID()
        val draftId = UUID.randomUUID()
        val now = OffsetDateTime.now()

        dslContext
            .insertInto(WIDGET_VIEW)
            .columns(
                WIDGET_VIEW.ID,
                WIDGET_VIEW.NAME,
                WIDGET_VIEW.PUBLISHED,
                WIDGET_VIEW.CREATED_AT,
                WIDGET_VIEW.UPDATED_AT,
            ).values(publishedId, "Published Widget", true, now, now)
            .values(draftId, "Draft Widget", false, now, now)
            .execute()

        // When: Query for published widgets only
        val publishedCount =
            dslContext
                .selectCount()
                .from(WIDGET_VIEW)
                .where(WIDGET_VIEW.PUBLISHED.eq(true))
                .fetchOne(0, Int::class.java)

        // Then: Should return exactly 1 published widget
        assertThat(publishedCount).isEqualTo(1)
    }

    @Test
    fun `AC7 - Type-safe jOOQ UPDATE should modify records`() {
        // Given: An unpublished widget
        val widgetId = UUID.randomUUID()
        val now = OffsetDateTime.now()

        dslContext
            .insertInto(WIDGET_VIEW)
            .columns(
                WIDGET_VIEW.ID,
                WIDGET_VIEW.NAME,
                WIDGET_VIEW.PUBLISHED,
                WIDGET_VIEW.CREATED_AT,
                WIDGET_VIEW.UPDATED_AT,
            ).values(widgetId, "Draft Widget", false, now, now)
            .execute()

        // When: Update published status using type-safe DSL
        val updateCount =
            dslContext
                .update(WIDGET_VIEW)
                .set(WIDGET_VIEW.PUBLISHED, true)
                .set(WIDGET_VIEW.UPDATED_AT, OffsetDateTime.now())
                .where(WIDGET_VIEW.ID.eq(widgetId))
                .execute()

        // Then: Update should succeed
        assertThat(updateCount).isEqualTo(1)

        // And: Record should reflect changes
        val updated =
            dslContext
                .selectFrom(WIDGET_VIEW)
                .where(WIDGET_VIEW.ID.eq(widgetId))
                .fetchOne()

        assertThat(updated!!.published).isEqualTo(true)
    }

    companion object {
        private lateinit var postgres: PostgreSQLContainer<*>
        private lateinit var dslContext: DSLContext

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            // Start PostgreSQL Testcontainer
            postgres =
                PostgreSQLContainer(DockerImageName.parse("postgres:16.10"))
                    .withDatabaseName("test_db")
                    .withUsername("test_user")
                    .withPassword("test_password")
                    .withInitScript("test-schema.sql")

            postgres.start()

            // Create DataSource using HikariCP
            val config =
                HikariConfig().apply {
                    jdbcUrl = postgres.jdbcUrl
                    username = postgres.username
                    password = postgres.password
                    maximumPoolSize = 5
                }
            val dataSource: DataSource = HikariDataSource(config)

            // Create jOOQ DSLContext (mimics JooqConfiguration.kt)
            dslContext = DSL.using(dataSource, SQLDialect.POSTGRES)
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            postgres.stop()
        }
    }
}
