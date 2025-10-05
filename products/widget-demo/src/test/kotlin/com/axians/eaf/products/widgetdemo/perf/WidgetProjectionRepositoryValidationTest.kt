package com.axians.eaf.products.widgetdemo.perf

import com.axians.eaf.products.widgetdemo.entities.WidgetProjection
import com.axians.eaf.products.widgetdemo.legacy.LegacyProjectionTestConfig
import com.axians.eaf.products.widgetdemo.legacy.LegacyWidgetProjectionEntity
import com.axians.eaf.products.widgetdemo.legacy.LegacyWidgetProjectionJpaRepository
import com.axians.eaf.products.widgetdemo.repositories.WidgetCategorySummary
import com.axians.eaf.products.widgetdemo.repositories.WidgetProjectionRepository
import com.axians.eaf.products.widgetdemo.repositories.WidgetSearchCriteria
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import javax.sql.DataSource

private const val VALIDATION_TENANT_ID = "tenant-validation"

@SpringBootTest(classes = [LegacyProjectionTestConfig::class])
@ActiveProfiles("test")
@Transactional
class WidgetProjectionRepositoryValidationTest : FunSpec() {
    @Autowired
    private lateinit var jooqRepository: WidgetProjectionRepository

    @Autowired
    private lateinit var legacyRepository: LegacyWidgetProjectionJpaRepository

    @Autowired
    private lateinit var dsl: DSLContext

    @Autowired
    private lateinit var dataSource: DataSource

    private val baseTime: Instant = Instant.now()

    init {
        extension(SpringExtension())

        beforeSpec {
            runWidgetProjectionSchemaMigration(dataSource)
        }

        beforeTest {
            clearWidgetProjectionTable(dsl)
            seedValidationData(jooqRepository, VALIDATION_TENANT_ID, baseTime)
        }

        test("8.3-VAL-001: find queries return identical results") {
            val jooqResults = jooqRepository.findByTenantIdOrderByCreatedAtDesc(VALIDATION_TENANT_ID)
            val legacyResults =
                legacyRepository
                    .findByTenantIdOrderByCreatedAtDesc(VALIDATION_TENANT_ID)
                    .map { it.toDomainProjection() }

            jooqResults.map { it.widgetId } shouldContainExactly legacyResults.map { it.widgetId }

            jooqRepository
                .findByTenantIdAndCategoryOrderByCreatedAtDesc(
                    VALIDATION_TENANT_ID,
                    "ALPHA",
                ).map { it.widgetId } shouldContainExactly
                legacyRepository
                    .findByTenantIdOrderByCreatedAtDesc(VALIDATION_TENANT_ID)
                    .filter { it.category == "ALPHA" }
                    .map { it.widgetId }

            jooqRepository
                .findByTenantIdAndValueGreaterThanOrderByValueDesc(VALIDATION_TENANT_ID, BigDecimal("50"))
                .map { it.widgetId } shouldContainExactly
                legacyRepository
                    .findByTenantIdOrderByCreatedAtDesc(VALIDATION_TENANT_ID)
                    .filter { it.value > BigDecimal("50") }
                    .sortedByDescending { it.value }
                    .map { it.widgetId }

            jooqRepository
                .findByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(VALIDATION_TENANT_ID, baseTime.minusSeconds(3))
                .map { it.widgetId } shouldContainExactly
                legacyRepository
                    .findByTenantIdOrderByCreatedAtDesc(VALIDATION_TENANT_ID)
                    .filter { it.createdAt.isAfter(baseTime.minusSeconds(3)) }
                    .map { it.widgetId }
        }

        test("8.3-VAL-002: count, exists, and delete operations align") {
            jooqRepository.countByTenantId(VALIDATION_TENANT_ID) shouldBe legacyRepository.count().toLong()
            jooqRepository.existsByWidgetIdAndTenantId("validation-1", VALIDATION_TENANT_ID) shouldBe true

            jooqRepository.deleteByWidgetIdAndTenantId("validation-9", VALIDATION_TENANT_ID) shouldBe 1
            legacyRepository.deleteById("validation-9")

            jooqRepository.countByTenantId(VALIDATION_TENANT_ID) shouldBe legacyRepository.count().toLong()
        }

        test("8.3-VAL-003: search produces matching pages") {
            val jooqPage =
                jooqRepository.search(
                    WidgetSearchCriteria(
                        tenantId = VALIDATION_TENANT_ID,
                        category = "BETA",
                        search = "Widget",
                        page = 0,
                        size = 2,
                        sort = listOf("createdAt.desc"),
                    ),
                )
            val legacyResults =
                legacyRepository
                    .findByTenantIdOrderByCreatedAtDesc(VALIDATION_TENANT_ID)
                    .filter { it.category == "BETA" && it.name.contains("Widget", ignoreCase = true) }
            jooqPage.total shouldBe legacyResults.size.toLong()
            jooqPage.items.shouldHaveSize(2)
            jooqPage.items.map { it.widgetId } shouldContainExactly legacyResults.take(2).map { it.widgetId }
        }

        test("8.3-VAL-004: category summaries are equivalent") {
            val jooqSummaries = jooqRepository.getCategorySummaryByTenantId(VALIDATION_TENANT_ID)
            val legacySummaries =
                legacyRepository
                    .findByTenantIdOrderByCreatedAtDesc(VALIDATION_TENANT_ID)
                    .groupBy { it.category }
                    .map { (category, entries) ->
                        val total = entries.map { it.value }.reduce(BigDecimal::add)
                        WidgetCategorySummary(
                            category = category,
                            count = entries.size.toLong(),
                            averageValue = total.divide(BigDecimal.valueOf(entries.size.toLong()), 2, RoundingMode.HALF_UP),
                            totalValue = total,
                        )
                    }.sortedBy { it.category }

            jooqSummaries.sortedBy { it.category } shouldBe legacySummaries
        }
    }

    private fun LegacyWidgetProjectionEntity.toDomainProjection(): WidgetProjection =
        WidgetProjection(
            widgetId = widgetId,
            tenantId = tenantId,
            name = name,
            description = description,
            value = value,
            category = category,
            metadata = metadata,
            createdAt = createdAt,
            updatedAt = updatedAt,
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
