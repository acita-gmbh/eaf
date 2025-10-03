package com.axians.eaf.tools.cli.generators

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.axians.eaf.tools.cli.templates.TemplateEngine
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Generator for CQRS/ES aggregate vertical slices.
 *
 * Generates 16 files across layers:
 * - Domain (2): Aggregate, Error
 * - Shared API (8): Commands, Events, Queries, DTOs
 * - Projection (3): Entity, Repository, Handler
 * - Query (1): QueryHandler
 * - Controller (2): Controller, RequestDTO
 * - Migration (1): Liquibase XML
 *
 * Story 7.3: Create "New Aggregate" Generator
 */
@Suppress("TooManyFunctions")
class AggregateGenerator(
    private val templateEngine: TemplateEngine,
) {
    /**
     * Complexity Justification: Sequential layer generation with early returns for errors.
     * Each layer requires template rendering and file writing with error handling.
     * Complexity driven by comprehensive error handling across 7 generation layers.
     */
    @Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod")
    fun generateAggregate(
        aggregateName: String,
        moduleName: String,
        fieldsInput: String?,
        validationInput: String?,
    ): Either<GeneratorError, AggregateInfo> {
        // Validate module exists
        val modulePath = Paths.get("products", moduleName)
        if (!Files.exists(modulePath) || !Files.isDirectory(modulePath)) {
            return GeneratorError.ModuleNotFound(moduleName).left()
        }

        // Parse validation patterns
        val validationMap =
            when (val result = parseValidation(validationInput)) {
                is Either.Left -> return result
                is Either.Right -> result.value
            }

        // Parse fields
        val fields =
            if (fieldsInput != null) {
                when (val result = parseFields(fieldsInput, validationMap)) {
                    is Either.Left -> return result
                    is Either.Right -> result.value
                }
            } else {
                FieldSpec.defaultFields()
            }

        // Create context
        val context = AggregateContext.fromAggregateName(aggregateName, moduleName, fields)

        // Check if aggregate already exists (filename matches class name)
        val aggregateFile =
            modulePath.resolve(
                "src/main/kotlin/com/axians/eaf/products/${context.modulePackage}/domain/$aggregateName.kt",
            )
        if (Files.exists(aggregateFile)) {
            return GeneratorError.AggregateAlreadyExists(aggregateName, moduleName).left()
        }

        // Generate all layers
        when (val result = generateDomainLayer(context, modulePath)) {
            is Either.Left -> return result
            is Either.Right -> Unit
        }

        when (val result = generateSharedApiLayer(context)) {
            is Either.Left -> return result
            is Either.Right -> Unit
        }

        when (val result = generateProjectionLayer(context, modulePath)) {
            is Either.Left -> return result
            is Either.Right -> Unit
        }

        when (val result = generateQueryLayer(context, modulePath)) {
            is Either.Left -> return result
            is Either.Right -> Unit
        }

        when (val result = generateControllerLayer(context, modulePath)) {
            is Either.Left -> return result
            is Either.Right -> Unit
        }

        when (val result = generateMigration(context, modulePath)) {
            is Either.Left -> return result
            is Either.Right -> Unit
        }

        when (val result = generateTests(context, modulePath)) {
            is Either.Left -> return result
            is Either.Right -> Unit
        }

        return AggregateInfo(aggregateName, moduleName, 16).right()
    }

    private fun parseFields(
        fieldsInput: String,
        validationMap: Map<String, ValidationSpec>,
    ): Either<GeneratorError, List<FieldSpec>> {
        val fieldDefs = fieldsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val fields = mutableListOf<FieldSpec>()

        for (fieldDef in fieldDefs) {
            val field = FieldSpec.fromFieldDefinition(fieldDef, validationMap)
            if (field == null) {
                val parts = fieldDef.split(":")
                val type = if (parts.size == 2) parts[1].trim() else "unknown"
                return GeneratorError
                    .UnsupportedType(
                        type = type,
                        supportedTypes = FieldSpec.TYPE_MAPPINGS.keys,
                    ).left()
            }
            fields.add(field)
        }

        return fields.right()
    }

    private fun parseValidation(validationInput: String?): Either<GeneratorError, Map<String, ValidationSpec>> {
        if (validationInput == null) return emptyMap<String, ValidationSpec>().right()

        val validationMap = mutableMapOf<String, ValidationSpec>()
        val specs = validationInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        for (spec in specs) {
            val parts = spec.split(":")
            if (parts.size < 2) continue

            val fieldName = parts[0].trim()
            val validationDef = parts[1].trim()

            // Check if it's regex pattern (starts with ^)
            if (validationDef.startsWith("^")) {
                validationMap[fieldName] = ValidationSpec(pattern = validationDef)
            } else if (validationDef.contains("|")) {
                // Enum values
                val enumValues = validationDef.split("|").map { it.trim() }
                validationMap[fieldName] = ValidationSpec(enumValues = enumValues)
            } else if (parts.size == 3) {
                // Range: field:min:max
                validationMap[fieldName] = ValidationSpec(minValue = parts[1], maxValue = parts[2])
            }
        }

        return validationMap.right()
    }

    private fun generateDomainLayer(
        context: AggregateContext,
        modulePath: Path,
    ): Either<GeneratorError, Unit> =
        try {
            val domainPath =
                modulePath.resolve("src/main/kotlin/com/axians/eaf/products/${context.modulePackage}/domain")
            Files.createDirectories(domainPath)

            // Generate Aggregate (filename matches class name for ktlint)
            val aggregateContent = templateEngine.render("Aggregate.kt.mustache", context.toMap())
            Files.writeString(domainPath.resolve("${context.aggregateName}.kt"), aggregateContent)

            // Generate Error
            val errorContent = templateEngine.render("DomainError.kt.mustache", context.toMap())
            Files.writeString(domainPath.resolve("${context.aggregateName}Error.kt"), errorContent)

            Unit.right()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            GeneratorError.TemplateError("DomainLayer", ex).left()
        }

    @Suppress("LongMethod")
    private fun generateSharedApiLayer(context: AggregateContext): Either<GeneratorError, Unit> =
        try {
            val apiBasePath =
                Paths.get(
                    "shared/shared-api/src/main/kotlin/com/axians/eaf/api/${context.aggregateNameKebab}",
                )

            // Commands
            val commandsPath = apiBasePath.resolve("commands")
            Files.createDirectories(commandsPath)

            val createCmdContent = templateEngine.render("CreateCommand.kt.mustache", context.toMap())
            Files.writeString(commandsPath.resolve("Create${context.aggregateName}Command.kt"), createCmdContent)

            val updateCmdContent = templateEngine.render("UpdateCommand.kt.mustache", context.toMap())
            Files.writeString(commandsPath.resolve("Update${context.aggregateName}Command.kt"), updateCmdContent)

            // Events
            val eventsPath = apiBasePath.resolve("events")
            Files.createDirectories(eventsPath)

            val createdEventContent = templateEngine.render("CreatedEvent.kt.mustache", context.toMap())
            Files.writeString(eventsPath.resolve("${context.aggregateName}CreatedEvent.kt"), createdEventContent)

            val updatedEventContent = templateEngine.render("UpdatedEvent.kt.mustache", context.toMap())
            Files.writeString(eventsPath.resolve("${context.aggregateName}UpdatedEvent.kt"), updatedEventContent)

            // Queries
            val queriesPath = apiBasePath.resolve("queries")
            Files.createDirectories(queriesPath)

            val findByIdContent = templateEngine.render("FindByIdQuery.kt.mustache", context.toMap())
            Files.writeString(queriesPath.resolve("Find${context.aggregateName}ByIdQuery.kt"), findByIdContent)

            val findAllContent = templateEngine.render("FindAllQuery.kt.mustache", context.toMap())
            Files.writeString(queriesPath.resolve("Find${context.aggregateNamePlural}Query.kt"), findAllContent)

            // DTO
            val dtoPath = apiBasePath.resolve("dto")
            Files.createDirectories(dtoPath)

            val responseContent = templateEngine.render("Response.kt.mustache", context.toMap())
            Files.writeString(dtoPath.resolve("${context.aggregateName}Response.kt"), responseContent)

            Unit.right()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            GeneratorError.TemplateError("SharedAPILayer", ex).left()
        }

    @Suppress("LongMethod")
    private fun generateProjectionLayer(
        context: AggregateContext,
        modulePath: Path,
    ): Either<GeneratorError, Unit> =
        try {
            // Entity
            val entitiesPath =
                modulePath.resolve("src/main/kotlin/com/axians/eaf/products/${context.modulePackage}/entities")
            Files.createDirectories(entitiesPath)

            val entityContent = templateEngine.render("ProjectionEntity.kt.mustache", context.toMap())
            Files.writeString(entitiesPath.resolve("${context.aggregateName}Projection.kt"), entityContent)

            // Repository
            val repositoriesPath =
                modulePath.resolve("src/main/kotlin/com/axians/eaf/products/${context.modulePackage}/repositories")
            Files.createDirectories(repositoriesPath)

            val repoContent = templateEngine.render("ProjectionRepository.kt.mustache", context.toMap())
            Files.writeString(repositoriesPath.resolve("${context.aggregateName}ProjectionRepository.kt"), repoContent)

            // Handler
            val projectionsPath =
                modulePath.resolve("src/main/kotlin/com/axians/eaf/products/${context.modulePackage}/projections")
            Files.createDirectories(projectionsPath)

            val handlerContent = templateEngine.render("ProjectionHandler.kt.mustache", context.toMap())
            Files.writeString(projectionsPath.resolve("${context.aggregateName}ProjectionHandler.kt"), handlerContent)

            Unit.right()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            GeneratorError.TemplateError("ProjectionLayer", ex).left()
        }

    private fun generateQueryLayer(
        context: AggregateContext,
        modulePath: Path,
    ): Either<GeneratorError, Unit> =
        try {
            val queryPath =
                modulePath.resolve("src/main/kotlin/com/axians/eaf/products/${context.modulePackage}/query")
            Files.createDirectories(queryPath)

            val queryHandlerContent = templateEngine.render("QueryHandler.kt.mustache", context.toMap())
            Files.writeString(queryPath.resolve("${context.aggregateName}QueryHandler.kt"), queryHandlerContent)

            Unit.right()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            GeneratorError.TemplateError("QueryLayer", ex).left()
        }

    private fun generateControllerLayer(
        context: AggregateContext,
        modulePath: Path,
    ): Either<GeneratorError, Unit> =
        try {
            val controllersPath =
                modulePath.resolve("src/main/kotlin/com/axians/eaf/products/${context.modulePackage}/controllers")
            Files.createDirectories(controllersPath)

            val controllerContent = templateEngine.render("Controller.kt.mustache", context.toMap())
            Files.writeString(controllersPath.resolve("${context.aggregateName}Controller.kt"), controllerContent)

            val requestContent = templateEngine.render("ControllerRequestDto.kt.mustache", context.toMap())
            Files.writeString(controllersPath.resolve("Create${context.aggregateName}Request.kt"), requestContent)

            Unit.right()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            GeneratorError.TemplateError("ControllerLayer", ex).left()
        }

    private fun generateMigration(
        context: AggregateContext,
        modulePath: Path,
    ): Either<GeneratorError, Unit> =
        try {
            val changelogPath = modulePath.resolve("src/main/resources/db/changelog")
            Files.createDirectories(changelogPath)

            val migrationContent = templateEngine.render("liquibase-migration.xml.mustache", context.toMap())

            val timestamp = System.currentTimeMillis()
            Files.writeString(
                changelogPath.resolve("${context.aggregateNameKebab}-projection-table-$timestamp.xml"),
                migrationContent,
            )

            Unit.right()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            GeneratorError.TemplateError("Migration", ex).left()
        }

    private fun generateTests(
        context: AggregateContext,
        modulePath: Path,
    ): Either<GeneratorError, Unit> =
        try {
            // Unit test
            val testPath =
                modulePath.resolve("src/test/kotlin/com/axians/eaf/products/${context.modulePackage}/domain")
            Files.createDirectories(testPath)

            val aggregateTestContent = templateEngine.render("AggregateTest.kt.mustache", context.toMap())
            Files.writeString(testPath.resolve("${context.aggregateName}Test.kt"), aggregateTestContent)

            // Projection handler test
            val projectionTestPath =
                modulePath.resolve("src/test/kotlin/com/axians/eaf/products/${context.modulePackage}/projections")
            Files.createDirectories(projectionTestPath)

            val handlerTestContent = templateEngine.render("ProjectionHandlerTest.kt.mustache", context.toMap())
            Files.writeString(
                projectionTestPath.resolve("${context.aggregateName}ProjectionHandlerTest.kt"),
                handlerTestContent,
            )

            // Integration test
            val integrationTestPath =
                modulePath.resolve("src/integration-test/kotlin/com/axians/eaf/products/${context.modulePackage}/api")
            Files.createDirectories(integrationTestPath)

            val controllerTestContent = templateEngine.render("ControllerIntegrationTest.kt.mustache", context.toMap())
            Files.writeString(
                integrationTestPath.resolve("${context.aggregateName}ControllerIntegrationTest.kt"),
                controllerTestContent,
            )

            Unit.right()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            GeneratorError.TemplateError("Tests", ex).left()
        }
}
