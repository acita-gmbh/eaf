package com.axians.eaf.products.widgetdemo.repositories

import com.axians.eaf.products.widgetdemo.entities.WidgetProjection
import com.axians.eaf.products.widgetdemo.jooq.tables.references.WIDGET_PROJECTION
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.FileSystemResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import javax.sql.DataSource

@SpringBootTest(classes = [WidgetProjectionTestConfig::class])
@ActiveProfiles("test")
@Transactional
class WidgetProjectionRepositoryIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var repository: WidgetProjectionRepository

    @Autowired
    private lateinit var dsl: DSLContext

    @Autowired
    private lateinit var dataSource: DataSource

    init {
        extension(SpringExtension())

        beforeSpec {
            runSchemaMigration(dataSource)
        }

        beforeTest {
            dsl.deleteFrom(WIDGET_PROJECTION).execute()
        }

        test("8.3-INT-001: should persist and fetch projection by widget and tenant") {
            val projection = createWidgetProjection(widgetId = "widget-1", tenantId = "tenant-1")

            repository.save(projection)

            val fetched = repository.findByWidgetIdAndTenantId("widget-1", "tenant-1")

            fetched shouldNotBe null
            fetched!!.widgetId shouldBe "widget-1"
            fetched.name shouldBe "Test Widget"
            fetched.metadata shouldBe "{\"feature\":true}"
        }

        test("8.3-INT-002: search should honour tenant filters and pagination") {
            repeat(5) { index ->
                repository.save(
                    createWidgetProjection(
                        widgetId = "widget-$index",
                        tenantId = "tenant-search",
                        name = "Widget $index",
                        category = if (index % 2 == 0) "ALPHA" else "BETA",
                        createdAt = Instant.now().minusSeconds(index.toLong()),
                    ),
                )
            }

            val page =
                repository.search(
                    WidgetSearchCriteria(
                        tenantId = "tenant-search",
                        category = "ALPHA",
                        search = "Widget",
                        page = 0,
                        size = 2,
                        sort = listOf("createdAt.desc"),
                    ),
                )

            page.total shouldBe 3
            page.items.shouldHaveSize(2)
            page.items.first().category shouldBe "ALPHA"
        }

        test("8.3-INT-003: getCategorySummaryByTenantId should aggregate correctly") {
            repository.save(createWidgetProjection("widget-cat-1", "tenant-cat", category = "REPORTING", value = BigDecimal("10.00")))
            repository.save(createWidgetProjection("widget-cat-2", "tenant-cat", category = "REPORTING", value = BigDecimal("30.00")))
            repository.save(createWidgetProjection("widget-cat-3", "tenant-cat", category = "ALERTING", value = BigDecimal("15.00")))

            val summaries = repository.getCategorySummaryByTenantId("tenant-cat")

            summaries.shouldHaveSize(2)
            val reporting = summaries.first { it.category == "REPORTING" }
            reporting.count shouldBe 2
            reporting.totalValue shouldBe BigDecimal("40.00")
        }

        test("8.3-INT-004: deleteBatch should remove oldest projections first") {
            repeat(3) { index ->
                repository.save(
                    createWidgetProjection("widget-del-$index", "tenant-del", createdAt = Instant.now().minusSeconds(index.toLong())),
                )
            }

            val deleted = repository.deleteBatch(2)
            deleted shouldBe 2
            repository.countByTenantId("tenant-del") shouldBe 1
        }
    }

    private fun runSchemaMigration(dataSource: DataSource) {
        val schema = FileSystemResource("${System.getProperty("user.dir")}/scripts/sql/widget_projection_schema.sql")
        ResourceDatabasePopulator(schema).execute(dataSource)
    }

    private fun createWidgetProjection(
        widgetId: String,
        tenantId: String,
        name: String = "Test Widget",
        description: String? = "Sample description",
        value: BigDecimal = BigDecimal("100.00"),
        category: String = "ALPHA",
        metadata: String? = "{\"feature\":true}",
        createdAt: Instant = Instant.now(),
    ): WidgetProjection =
        WidgetProjection(
            widgetId = widgetId,
            tenantId = tenantId,
            name = name,
            description = description,
            value = value,
            category = category,
            metadata = metadata,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            TestContainers.startAll()
            registry.add("spring.datasource.url") { TestContainers.postgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestContainers.postgres.username }
            registry.add("spring.datasource.password") { TestContainers.postgres.password }
            registry.add("spring.datasource.driver-class-name") { TestContainers.postgres.driverClassName }
            registry.add("spring.jooq.sql-dialect") { "POSTGRES" }
        }
    }
}
