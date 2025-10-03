package com.axians.eaf.tools.cli.generators

import java.time.Instant

/**
 * Context data for aggregate template rendering.
 *
 * Transforms user input (PascalCase aggregate name) into all required template variables
 * for generating 16 files across domain, API, projection, query, and controller layers.
 *
 * Naming Transformations:
 * - aggregateName: "ProductCatalog" (PascalCase - original input)
 * - aggregateNameLower: "productCatalog" (camelCase - for field names)
 * - aggregateNameKebab: "product-catalog" (kebab-case - for URLs, directories)
 * - aggregateNamePlural: "ProductCatalogs" (PascalCase plural)
 * - aggregateNamePluralLower: "productCatalogs" (camelCase plural)
 *
 * Story 7.3: Create "New Aggregate" Generator
 */
data class AggregateContext(
    val aggregateName: String,
    val aggregateNameLower: String,
    val aggregateNameKebab: String,
    val aggregateNamePlural: String,
    val aggregateNamePluralLower: String,
    val aggregateIdField: String,
    val moduleName: String,
    val modulePackage: String,
    val fields: List<FieldSpec>,
    val timestamp: String = Instant.now().toString(),
    val author: String = System.getProperty("user.name", "developer"),
) {
    /**
     * Converts context to Map for Mustache template rendering.
     */
    fun toMap(): Map<String, Any> =
        mapOf(
            "aggregateName" to aggregateName,
            "aggregateNameLower" to aggregateNameLower,
            "aggregateNameKebab" to aggregateNameKebab,
            "aggregateNamePlural" to aggregateNamePlural,
            "aggregateNamePluralLower" to aggregateNamePluralLower,
            "aggregateIdField" to aggregateIdField,
            "moduleName" to moduleName,
            "modulePackage" to modulePackage,
            "fields" to fields.map { it.toMap() },
            "timestamp" to timestamp,
            "author" to author,
        )

    companion object {
        /**
         * Creates AggregateContext from PascalCase aggregate name.
         *
         * Examples:
         * - "Product" → productId, product, products
         * - "ProductCatalog" → productCatalogId, product-catalog, productCatalogs
         * - "OrderItem" → orderItemId, order-item, orderItems
         */
        fun fromAggregateName(
            aggregateName: String,
            moduleName: String,
            fields: List<FieldSpec>,
        ): AggregateContext {
            // camelCase: First char lowercase
            val aggregateNameLower = aggregateName.replaceFirstChar { it.lowercaseChar() }

            // kebab-case: Insert hyphen before capitals, lowercase all
            val aggregateNameKebab =
                aggregateName
                    .replace(Regex("([a-z])([A-Z])"), "$1-$2")
                    .lowercase()

            // Simple pluralization (add 's')
            val aggregateNamePlural = "${aggregateName}s"
            val aggregateNamePluralLower = "${aggregateNameLower}s"

            // ID field name
            val aggregateIdField = "${aggregateNameLower}Id"

            // Module package (remove hyphens)
            val modulePackage = moduleName.replace("-", "")

            return AggregateContext(
                aggregateName = aggregateName,
                aggregateNameLower = aggregateNameLower,
                aggregateNameKebab = aggregateNameKebab,
                aggregateNamePlural = aggregateNamePlural,
                aggregateNamePluralLower = aggregateNamePluralLower,
                aggregateIdField = aggregateIdField,
                moduleName = moduleName,
                modulePackage = modulePackage,
                fields = fields,
            )
        }
    }
}
