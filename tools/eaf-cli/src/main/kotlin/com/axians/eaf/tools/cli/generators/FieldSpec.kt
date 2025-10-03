package com.axians.eaf.tools.cli.generators

/**
 * Specification for a single field in an aggregate.
 *
 * Used for template rendering to generate:
 * - Aggregate fields with proper types
 * - Command/Event parameters
 * - JPA entity columns
 * - Validation logic
 * - Liquibase migrations
 *
 * Story 7.3: Create "New Aggregate" Generator
 */
data class FieldSpec(
    val name: String,
    val type: String,
    val kotlinType: String,
    val sqlType: String,
    val nullable: Boolean = false,
    val validationPattern: String? = null,
    val enumValues: List<String>? = null,
    val minValue: String? = null,
    val maxValue: String? = null,
) {
    /**
     * Converts FieldSpec to Map for Mustache template rendering.
     */
    fun toMap(): Map<String, Any?> =
        mapOf(
            "name" to name,
            "type" to type,
            "kotlinType" to kotlinType,
            "sqlType" to sqlType,
            "nullable" to nullable,
            "validationPattern" to validationPattern,
            "enumValues" to enumValues,
            "minValue" to minValue,
            "maxValue" to maxValue,
            "hasValidation" to (validationPattern != null || enumValues != null || minValue != null),
        )

    companion object {
        /**
         * Supported CLI type mappings to Kotlin and SQL types.
         */
        val TYPE_MAPPINGS =
            mapOf(
                "String" to
                    Triple(
                        "kotlin.String",
                        "VARCHAR(255)",
                        "^[A-Za-z0-9](?:[A-Za-z0-9 _-]{0,98}[A-Za-z0-9])?$",
                    ),
                "Int" to Triple("kotlin.Int", "INTEGER", null),
                "Long" to Triple("kotlin.Long", "BIGINT", null),
                "BigDecimal" to Triple("java.math.BigDecimal", "DECIMAL(19,2)", null),
                "Boolean" to Triple("kotlin.Boolean", "BOOLEAN", null),
                "Instant" to Triple("java.time.Instant", "TIMESTAMP WITH TIME ZONE", null),
            )

        /**
         * Creates FieldSpec from CLI field definition.
         *
         * Examples:
         * - "name:String" → FieldSpec(name="name", type="String", kotlinType="kotlin.String")
         * - "price:BigDecimal" → FieldSpec(name="price", type="BigDecimal", kotlinType="java.math.BigDecimal")
         */
        @Suppress("ReturnCount")
        fun fromFieldDefinition(
            fieldDef: String,
            customValidation: Map<String, ValidationSpec>? = null,
        ): FieldSpec? {
            val parts = fieldDef.trim().split(":")
            if (parts.size != 2) return null

            val name = parts[0].trim()
            val type = parts[1].trim()

            val typeMapping = TYPE_MAPPINGS[type] ?: return null
            val (kotlinType, sqlType, defaultPattern) = typeMapping

            val validation = customValidation?.get(name)

            return FieldSpec(
                name = name,
                type = type,
                kotlinType = kotlinType,
                sqlType = sqlType,
                nullable = false,
                validationPattern = validation?.pattern ?: defaultPattern,
                enumValues = validation?.enumValues,
                minValue = validation?.minValue,
                maxValue = validation?.maxValue,
            )
        }

        /**
         * Creates default fields when --fields not specified.
         * Note: Does NOT include aggregate ID field - that's handled separately.
         */
        fun defaultFields(): List<FieldSpec> =
            listOf(
                FieldSpec(
                    name = "name",
                    type = "String",
                    kotlinType = "kotlin.String",
                    sqlType = "VARCHAR(255)",
                    nullable = false,
                    validationPattern = "^[A-Za-z0-9](?:[A-Za-z0-9 _-]{0,98}[A-Za-z0-9])?$",
                ),
            )
    }
}

/**
 * Validation specification for a field (parsed from --validation option).
 */
data class ValidationSpec(
    val pattern: String? = null,
    val enumValues: List<String>? = null,
    val minValue: String? = null,
    val maxValue: String? = null,
)
