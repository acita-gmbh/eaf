package com.axians.eaf.products.widgetdemo.perf

import com.axians.eaf.products.widgetdemo.entities.WidgetProjection
import com.axians.eaf.products.widgetdemo.jooq.tables.references.WIDGET_PROJECTION
import com.axians.eaf.products.widgetdemo.repositories.WidgetProjectionRepository
import org.jooq.DSLContext
import org.springframework.core.io.FileSystemResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import java.math.BigDecimal
import java.time.Instant
import javax.sql.DataSource

internal fun runWidgetProjectionSchemaMigration(dataSource: DataSource) {
    val schema = FileSystemResource("${System.getProperty("user.dir")}/scripts/sql/widget_projection_schema.sql")
    ResourceDatabasePopulator(schema).execute(dataSource)
}

internal fun clearWidgetProjectionTable(dsl: DSLContext) {
    dsl.deleteFrom(WIDGET_PROJECTION).execute()
}

internal fun seedBenchmarkData(
    repository: WidgetProjectionRepository,
    dsl: DSLContext,
    tenantId: String,
    size: Int,
) {
    clearWidgetProjectionTable(dsl)
    val start = Instant.now()
    val records =
        (1..size).map { index ->
            WidgetProjection(
                widgetId = "perf-$index",
                tenantId = tenantId,
                name = "Benchmark Widget $index",
                description = "Benchmark description $index",
                value = BigDecimal.valueOf((index % 100 + 1).toLong()),
                category = if (index % 2 == 0) "ALPHA" else "BETA",
                metadata = "{\"feature\":${index % 2 == 0}}",
                createdAt = start.minusSeconds(index.toLong()),
                updatedAt = start.minusSeconds(index.toLong()),
            )
        }

    records.chunked(200).forEach { chunk ->
        chunk.forEach { repository.save(it) }
    }
}

internal fun seedValidationData(
    repository: WidgetProjectionRepository,
    tenantId: String,
    baseTime: Instant,
) {
    (0 until 10).forEach { index ->
        val category = if (index % 2 == 0) "ALPHA" else "BETA"
        val value = BigDecimal((index * 10 + 10).toString())
        val projection =
            WidgetProjection(
                widgetId = "validation-$index",
                tenantId = tenantId,
                name = "Widget $index",
                description = "Validation widget $index",
                value = value,
                category = category,
                metadata = "{\"flag\":$index}",
                createdAt = baseTime.minusSeconds(index.toLong()),
                updatedAt = baseTime.minusSeconds(index.toLong()),
            )
        repository.save(projection)
    }
}

internal fun measureQueryAverageMillis(
    warmUpIterations: Int,
    iterations: Int = 50,
    block: () -> Any?,
): Double {
    repeat(warmUpIterations) { block() }
    val timings =
        (1..iterations).map {
            val start = System.nanoTime()
            block()
            (System.nanoTime() - start) / 1_000_000.0
        }
    return timings.average()
}
