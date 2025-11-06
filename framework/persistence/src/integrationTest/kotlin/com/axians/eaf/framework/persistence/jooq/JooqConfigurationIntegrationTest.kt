package com.axians.eaf.framework.persistence.jooq

import com.axians.eaf.framework.persistence.jooq.tables.WidgetView.Companion.WIDGET_VIEW
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Integration test for jOOQ Configuration and type-safe SQL queries.
 *
 * Validates:
 * - AC1: jOOQ 3.20.8 dependency configuration
 * - AC2: DSLContext bean availability and configuration
 * - AC3: jOOQ code generation from PostgreSQL schema
 * - AC5: Generated WIDGET_VIEW table class with column accessors
 * - AC7: Type-safe query execution on widget_view projection table
 * - AC8: jOOQ code generation task success
 *
 * Uses Testcontainers PostgreSQL for real database testing (H2 forbidden).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JooqConfigurationIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var dslContext: DSLContext

    init {
        extension(SpringExtension())

        test("AC2: DSLContext bean should be configured with PostgreSQL dialect") {
            dslContext shouldNotBe null
            dslContext.dialect().name shouldBe "POSTGRES"
        }

        test("AC5: Generated WIDGET_VIEW table class should have column accessors") {
            // Verify generated jOOQ class structure
            WIDGET_VIEW shouldNotBe null
            WIDGET_VIEW.ID shouldNotBe null
            WIDGET_VIEW.NAME shouldNotBe null
            WIDGET_VIEW.PUBLISHED shouldNotBe null
            WIDGET_VIEW.CREATED_AT shouldNotBe null
            WIDGET_VIEW.UPDATED_AT shouldNotBe null
        }

        test("AC7: Type-safe jOOQ query should insert and select rows successfully") {
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
            insertCount shouldBe 1

            // When: Query using type-safe jOOQ DSL
            val result =
                dslContext
                    .selectFrom(WIDGET_VIEW)
                    .where(WIDGET_VIEW.ID.eq(widgetId))
                    .fetchOne()

            // Then: Query should return the inserted record
            result shouldNotBe null
            result!!.id shouldBe widgetId
            result.name shouldBe widgetName
            result.published shouldBe false
            result.createdAt shouldNotBe null
            result.updatedAt shouldNotBe null
        }

        test("AC7: Type-safe jOOQ query should support complex filters") {
            // Given: Multiple widget records
            val widgetId1 = UUID.randomUUID()
            val widgetId2 = UUID.randomUUID()
            val now = OffsetDateTime.now()

            dslContext
                .insertInto(WIDGET_VIEW)
                .columns(
                    WIDGET_VIEW.ID,
                    WIDGET_VIEW.NAME,
                    WIDGET_VIEW.PUBLISHED,
                    WIDGET_VIEW.CREATED_AT,
                    WIDGET_VIEW.UPDATED_AT,
                ).values(widgetId1, "Published Widget", true, now, now)
                .values(widgetId2, "Draft Widget", false, now, now)
                .execute()

            // When: Query for published widgets only
            val publishedWidgets =
                dslContext
                    .selectFrom(WIDGET_VIEW)
                    .where(WIDGET_VIEW.PUBLISHED.eq(true))
                    .orderBy(WIDGET_VIEW.CREATED_AT.desc())
                    .fetch()

            // Then: Should return only published widgets
            publishedWidgets shouldNotBe null
            publishedWidgets.filter { it.id == widgetId1 } shouldHaveSize 1
            publishedWidgets.filter { it.id == widgetId2 } shouldHaveSize 0
        }

        test("AC7: Type-safe jOOQ update should modify existing records") {
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
            updateCount shouldBe 1

            // And: Query should return updated record
            val result =
                dslContext
                    .selectFrom(WIDGET_VIEW)
                    .where(WIDGET_VIEW.ID.eq(widgetId))
                    .fetchOne()

            result shouldNotBe null
            result!!.published shouldBe true
        }
    }

    companion object {
        private val postgresContainer =
            PostgreSQLContainer<Nothing>("postgres:16.10")
                .apply {
                    withDatabaseName("eaf_test")
                    withUsername("test_user")
                    withPassword("test_password")
                    withInitScript("test-schema.sql")
                }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            postgresContainer.start()

            registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
            registry.add("spring.datasource.username") { postgresContainer.username }
            registry.add("spring.datasource.password") { postgresContainer.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "none" }
        }
    }
}
