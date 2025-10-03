package com.axians.eaf.tools.cli.generators

/**
 * Information about a successfully generated aggregate.
 *
 * Story 7.3: Create "New Aggregate" Generator
 */
data class AggregateInfo(
    val aggregateName: String,
    val moduleName: String,
    val filesGenerated: Int,
)
