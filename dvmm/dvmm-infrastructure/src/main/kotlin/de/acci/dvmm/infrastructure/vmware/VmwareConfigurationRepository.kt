package de.acci.dvmm.infrastructure.vmware

import de.acci.dvmm.application.vmware.VmwareConfigurationError
import de.acci.dvmm.application.vmware.VmwareConfigurationPort
import de.acci.dvmm.domain.vmware.VmwareConfiguration
import de.acci.dvmm.domain.vmware.VmwareConfigurationId
import de.acci.dvmm.infrastructure.jooq.`public`.tables.VmwareConfigurations.Companion.VMWARE_CONFIGURATIONS
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.InsertSetMoreStep
import org.jooq.Record
import org.jooq.UpdateSetMoreStep
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * jOOQ-based repository for VMware configuration persistence.
 *
 * ## Column Symmetry Pattern
 *
 * This repository uses a sealed column mapping pattern to ensure read/write symmetry.
 * Both [mapRecord] (read) and [insert]/[update] (write) use the same [ConfigurationColumns]
 * sealed interface to guarantee that all columns are handled consistently.
 *
 * **Adding new columns:**
 * 1. Add new sealed class to [ConfigurationColumns]
 * 2. Add case to [mapColumn] for reads
 * 3. Add case to [setColumnInsert] and [setColumnUpdate] for writes
 * 4. Compile will fail if any step is missed
 *
 * ## RLS Note
 *
 * Tenant filtering is handled automatically by PostgreSQL Row-Level Security.
 * The `app.tenant_id` session variable is set by the connection customizer,
 * and RLS policies filter queries automatically.
 *
 * @see ConfigurationColumns
 */
@Repository
public class VmwareConfigurationRepository(
    private val dsl: DSLContext
) : VmwareConfigurationPort {

    private val logger = KotlinLogging.logger {}

    /**
     * Sealed interface defining all columns in VMWARE_CONFIGURATIONS.
     *
     * This pattern ensures compile-time safety: if a new column is added to this
     * sealed hierarchy, both read and write operations must handle it or the
     * exhaustive `when` expressions will fail to compile.
     */
    public sealed interface ConfigurationColumns {
        public data object Id : ConfigurationColumns
        public data object TenantId : ConfigurationColumns
        public data object VcenterUrl : ConfigurationColumns
        public data object Username : ConfigurationColumns
        public data object PasswordEncrypted : ConfigurationColumns
        public data object DatacenterName : ConfigurationColumns
        public data object ClusterName : ConfigurationColumns
        public data object DatastoreName : ConfigurationColumns
        public data object NetworkName : ConfigurationColumns
        public data object TemplateName : ConfigurationColumns
        public data object FolderPath : ConfigurationColumns
        public data object VerifiedAt : ConfigurationColumns
        public data object CreatedAt : ConfigurationColumns
        public data object UpdatedAt : ConfigurationColumns
        public data object CreatedBy : ConfigurationColumns
        public data object UpdatedBy : ConfigurationColumns
        public data object Version : ConfigurationColumns

        public companion object {
            /**
             * All columns that must be handled by read and write operations.
             * Iterating over this list ensures exhaustive handling.
             */
            public val all: List<ConfigurationColumns> = listOf(
                Id, TenantId, VcenterUrl, Username, PasswordEncrypted,
                DatacenterName, ClusterName, DatastoreName, NetworkName,
                TemplateName, FolderPath, VerifiedAt, CreatedAt, UpdatedAt,
                CreatedBy, UpdatedBy, Version
            )
        }
    }

    /**
     * Maps a column to its value from a jOOQ Record.
     * Exhaustive when expression ensures all columns are handled.
     */
    private fun mapColumn(record: Record, column: ConfigurationColumns): Any? = when (column) {
        ConfigurationColumns.Id -> record.get(VMWARE_CONFIGURATIONS.ID)!!
        ConfigurationColumns.TenantId -> record.get(VMWARE_CONFIGURATIONS.TENANT_ID)!!
        ConfigurationColumns.VcenterUrl -> record.get(VMWARE_CONFIGURATIONS.VCENTER_URL)!!
        ConfigurationColumns.Username -> record.get(VMWARE_CONFIGURATIONS.USERNAME)!!
        ConfigurationColumns.PasswordEncrypted -> record.get(VMWARE_CONFIGURATIONS.PASSWORD_ENCRYPTED)!!
        ConfigurationColumns.DatacenterName -> record.get(VMWARE_CONFIGURATIONS.DATACENTER_NAME)!!
        ConfigurationColumns.ClusterName -> record.get(VMWARE_CONFIGURATIONS.CLUSTER_NAME)!!
        ConfigurationColumns.DatastoreName -> record.get(VMWARE_CONFIGURATIONS.DATASTORE_NAME)!!
        ConfigurationColumns.NetworkName -> record.get(VMWARE_CONFIGURATIONS.NETWORK_NAME)!!
        ConfigurationColumns.TemplateName -> record.get(VMWARE_CONFIGURATIONS.TEMPLATE_NAME)!!
        ConfigurationColumns.FolderPath -> record.get(VMWARE_CONFIGURATIONS.FOLDER_PATH)
        ConfigurationColumns.VerifiedAt -> record.get(VMWARE_CONFIGURATIONS.VERIFIED_AT)
        ConfigurationColumns.CreatedAt -> record.get(VMWARE_CONFIGURATIONS.CREATED_AT)!!
        ConfigurationColumns.UpdatedAt -> record.get(VMWARE_CONFIGURATIONS.UPDATED_AT)!!
        ConfigurationColumns.CreatedBy -> record.get(VMWARE_CONFIGURATIONS.CREATED_BY)!!
        ConfigurationColumns.UpdatedBy -> record.get(VMWARE_CONFIGURATIONS.UPDATED_BY)!!
        ConfigurationColumns.Version -> record.get(VMWARE_CONFIGURATIONS.VERSION)!!
    }

    /**
     * Sets a column value in an INSERT statement.
     * Exhaustive when expression ensures all columns are handled symmetrically with [mapColumn].
     */
    private fun setColumnInsert(
        step: InsertSetMoreStep<*>,
        column: ConfigurationColumns,
        config: VmwareConfiguration
    ): InsertSetMoreStep<*> = when (column) {
        ConfigurationColumns.Id -> step.set(VMWARE_CONFIGURATIONS.ID, config.id.value)
        ConfigurationColumns.TenantId -> step.set(VMWARE_CONFIGURATIONS.TENANT_ID, config.tenantId.value)
        ConfigurationColumns.VcenterUrl -> step.set(VMWARE_CONFIGURATIONS.VCENTER_URL, config.vcenterUrl)
        ConfigurationColumns.Username -> step.set(VMWARE_CONFIGURATIONS.USERNAME, config.username)
        ConfigurationColumns.PasswordEncrypted -> step.set(VMWARE_CONFIGURATIONS.PASSWORD_ENCRYPTED, config.passwordEncrypted)
        ConfigurationColumns.DatacenterName -> step.set(VMWARE_CONFIGURATIONS.DATACENTER_NAME, config.datacenterName)
        ConfigurationColumns.ClusterName -> step.set(VMWARE_CONFIGURATIONS.CLUSTER_NAME, config.clusterName)
        ConfigurationColumns.DatastoreName -> step.set(VMWARE_CONFIGURATIONS.DATASTORE_NAME, config.datastoreName)
        ConfigurationColumns.NetworkName -> step.set(VMWARE_CONFIGURATIONS.NETWORK_NAME, config.networkName)
        ConfigurationColumns.TemplateName -> step.set(VMWARE_CONFIGURATIONS.TEMPLATE_NAME, config.templateName)
        ConfigurationColumns.FolderPath -> step.set(VMWARE_CONFIGURATIONS.FOLDER_PATH, config.folderPath)
        ConfigurationColumns.VerifiedAt -> step.set(VMWARE_CONFIGURATIONS.VERIFIED_AT, config.verifiedAt?.atOffset(ZoneOffset.UTC))
        ConfigurationColumns.CreatedAt -> step.set(VMWARE_CONFIGURATIONS.CREATED_AT, config.createdAt.atOffset(ZoneOffset.UTC))
        ConfigurationColumns.UpdatedAt -> step.set(VMWARE_CONFIGURATIONS.UPDATED_AT, config.updatedAt.atOffset(ZoneOffset.UTC))
        ConfigurationColumns.CreatedBy -> step.set(VMWARE_CONFIGURATIONS.CREATED_BY, config.createdBy.value)
        ConfigurationColumns.UpdatedBy -> step.set(VMWARE_CONFIGURATIONS.UPDATED_BY, config.updatedBy.value)
        ConfigurationColumns.Version -> step.set(VMWARE_CONFIGURATIONS.VERSION, config.version)
    }

    /**
     * Sets a column value in an UPDATE statement.
     * ID and TenantId are not updated (used in WHERE clause instead).
     */
    private fun setColumnUpdate(
        step: UpdateSetMoreStep<*>,
        column: ConfigurationColumns,
        config: VmwareConfiguration
    ): UpdateSetMoreStep<*> = when (column) {
        // ID and TenantId are never updated - they're part of the WHERE clause
        ConfigurationColumns.Id -> step
        ConfigurationColumns.TenantId -> step
        ConfigurationColumns.VcenterUrl -> step.set(VMWARE_CONFIGURATIONS.VCENTER_URL, config.vcenterUrl)
        ConfigurationColumns.Username -> step.set(VMWARE_CONFIGURATIONS.USERNAME, config.username)
        ConfigurationColumns.PasswordEncrypted -> step.set(VMWARE_CONFIGURATIONS.PASSWORD_ENCRYPTED, config.passwordEncrypted)
        ConfigurationColumns.DatacenterName -> step.set(VMWARE_CONFIGURATIONS.DATACENTER_NAME, config.datacenterName)
        ConfigurationColumns.ClusterName -> step.set(VMWARE_CONFIGURATIONS.CLUSTER_NAME, config.clusterName)
        ConfigurationColumns.DatastoreName -> step.set(VMWARE_CONFIGURATIONS.DATASTORE_NAME, config.datastoreName)
        ConfigurationColumns.NetworkName -> step.set(VMWARE_CONFIGURATIONS.NETWORK_NAME, config.networkName)
        ConfigurationColumns.TemplateName -> step.set(VMWARE_CONFIGURATIONS.TEMPLATE_NAME, config.templateName)
        ConfigurationColumns.FolderPath -> step.set(VMWARE_CONFIGURATIONS.FOLDER_PATH, config.folderPath)
        ConfigurationColumns.VerifiedAt -> step.set(VMWARE_CONFIGURATIONS.VERIFIED_AT, config.verifiedAt?.atOffset(ZoneOffset.UTC))
        ConfigurationColumns.CreatedAt -> step // CreatedAt never changes
        ConfigurationColumns.UpdatedAt -> step.set(VMWARE_CONFIGURATIONS.UPDATED_AT, config.updatedAt.atOffset(ZoneOffset.UTC))
        ConfigurationColumns.CreatedBy -> step // CreatedBy never changes
        ConfigurationColumns.UpdatedBy -> step.set(VMWARE_CONFIGURATIONS.UPDATED_BY, config.updatedBy.value)
        ConfigurationColumns.Version -> step.set(VMWARE_CONFIGURATIONS.VERSION, config.version)
    }

    /**
     * Maps a jOOQ Record to a domain VmwareConfiguration.
     */
    private fun mapRecord(record: Record): VmwareConfiguration {
        val verifiedAtOffset = mapColumn(record, ConfigurationColumns.VerifiedAt) as OffsetDateTime?
        val createdAtOffset = mapColumn(record, ConfigurationColumns.CreatedAt) as OffsetDateTime
        val updatedAtOffset = mapColumn(record, ConfigurationColumns.UpdatedAt) as OffsetDateTime

        return VmwareConfiguration(
            id = VmwareConfigurationId.fromString((mapColumn(record, ConfigurationColumns.Id) as UUID).toString()),
            tenantId = TenantId.fromString((mapColumn(record, ConfigurationColumns.TenantId) as UUID).toString()),
            vcenterUrl = mapColumn(record, ConfigurationColumns.VcenterUrl) as String,
            username = mapColumn(record, ConfigurationColumns.Username) as String,
            passwordEncrypted = mapColumn(record, ConfigurationColumns.PasswordEncrypted) as ByteArray,
            datacenterName = mapColumn(record, ConfigurationColumns.DatacenterName) as String,
            clusterName = mapColumn(record, ConfigurationColumns.ClusterName) as String,
            datastoreName = mapColumn(record, ConfigurationColumns.DatastoreName) as String,
            networkName = mapColumn(record, ConfigurationColumns.NetworkName) as String,
            templateName = mapColumn(record, ConfigurationColumns.TemplateName) as String,
            folderPath = mapColumn(record, ConfigurationColumns.FolderPath) as String?,
            verifiedAt = verifiedAtOffset?.toInstant(),
            createdAt = createdAtOffset.toInstant(),
            updatedAt = updatedAtOffset.toInstant(),
            createdBy = UserId.fromString((mapColumn(record, ConfigurationColumns.CreatedBy) as UUID).toString()),
            updatedBy = UserId.fromString((mapColumn(record, ConfigurationColumns.UpdatedBy) as UUID).toString()),
            version = mapColumn(record, ConfigurationColumns.Version) as Long
        )
    }

    override suspend fun findByTenantId(tenantId: TenantId): VmwareConfiguration? =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(VMWARE_CONFIGURATIONS)
                .where(VMWARE_CONFIGURATIONS.TENANT_ID.eq(tenantId.value))
                .fetchOne()
                ?.let { mapRecord(it) }
        }

    override suspend fun findById(id: VmwareConfigurationId): VmwareConfiguration? =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(VMWARE_CONFIGURATIONS)
                .where(VMWARE_CONFIGURATIONS.ID.eq(id.value))
                .fetchOne()
                ?.let { mapRecord(it) }
        }

    override suspend fun save(configuration: VmwareConfiguration): Result<Unit, VmwareConfigurationError> =
        withContext(Dispatchers.IO) {
            try {
                // Use atomic INSERT ON CONFLICT to avoid TOCTOU race condition
                // Start with ID column to get InsertSetMoreStep type
                val initialStep: InsertSetMoreStep<*> = dsl.insertInto(VMWARE_CONFIGURATIONS)
                    .set(VMWARE_CONFIGURATIONS.ID, configuration.id.value)

                // Set remaining columns, filtering out Id to avoid duplicate
                var step = initialStep
                ConfigurationColumns.all
                    .filterNot { it is ConfigurationColumns.Id }
                    .forEach { column -> step = setColumnInsert(step, column, configuration) }

                // ON CONFLICT (TENANT_ID) DO NOTHING - returns 0 rows if conflict
                val rowsInserted = step
                    .onConflict(VMWARE_CONFIGURATIONS.TENANT_ID)
                    .doNothing()
                    .execute()

                if (rowsInserted == 0) {
                    logger.warn {
                        "Configuration already exists for tenant ${configuration.tenantId.value}"
                    }
                    return@withContext VmwareConfigurationError.AlreadyExists(
                        tenantId = configuration.tenantId
                    ).failure()
                }

                logger.debug {
                    "Saved VMware configuration: id=${configuration.id.value}, tenantId=${configuration.tenantId.value}"
                }

                Unit.success()
            } catch (e: Exception) {
                logger.error(e) {
                    "Failed to save VMware configuration for tenant ${configuration.tenantId.value}"
                }
                VmwareConfigurationError.PersistenceFailure(
                    message = "Failed to save configuration: ${e.message}"
                ).failure()
            }
        }

    /**
     * Updates an existing VMware configuration with optimistic locking.
     *
     * ## Optimistic Locking Contract
     *
     * **IMPORTANT:** The caller MUST increment the version before calling this method.
     * The repository expects `configuration.version` to be the NEW version (current + 1),
     * and checks against `version - 1` in the WHERE clause.
     *
     * Example usage in command handler:
     * ```kotlin
     * val updated = existing.update(/* ... */).copy(version = existing.version + 1)
     * repository.update(updated)
     * ```
     *
     * This explicit contract ensures the caller is aware of version management and
     * prevents accidental overwrites.
     *
     * @param configuration The configuration with the NEW version already set
     * @return Success if update succeeded, or failure with ConcurrencyConflict/NotFound
     */
    override suspend fun update(configuration: VmwareConfiguration): Result<Unit, VmwareConfigurationError> =
        withContext(Dispatchers.IO) {
            try {
                // Start update with vcenterUrl column
                val initialStep: UpdateSetMoreStep<*> = dsl.update(VMWARE_CONFIGURATIONS)
                    .set(VMWARE_CONFIGURATIONS.VCENTER_URL, configuration.vcenterUrl)

                // Set remaining columns
                var step = initialStep
                ConfigurationColumns.all
                    .filterNot { it is ConfigurationColumns.Id || it is ConfigurationColumns.TenantId || it is ConfigurationColumns.VcenterUrl }
                    .forEach { column -> step = setColumnUpdate(step, column, configuration) }

                // Execute with optimistic locking - version must match expected (current - 1)
                val expectedVersion = configuration.version - 1
                val rowsUpdated = step
                    .where(VMWARE_CONFIGURATIONS.ID.eq(configuration.id.value))
                    .and(VMWARE_CONFIGURATIONS.VERSION.eq(expectedVersion))
                    .execute()

                if (rowsUpdated == 0) {
                    // Either not found or version mismatch
                    val current = dsl.selectFrom(VMWARE_CONFIGURATIONS)
                        .where(VMWARE_CONFIGURATIONS.ID.eq(configuration.id.value))
                        .fetchOne()

                    if (current == null) {
                        logger.warn { "Configuration not found: id=${configuration.id.value}" }
                        return@withContext VmwareConfigurationError.NotFound(
                            id = configuration.id
                        ).failure()
                    }

                    val actualVersion = current.get(VMWARE_CONFIGURATIONS.VERSION)!!
                    logger.warn {
                        "Concurrency conflict: id=${configuration.id.value}, expected=$expectedVersion, actual=$actualVersion"
                    }
                    return@withContext VmwareConfigurationError.ConcurrencyConflict(
                        expectedVersion = expectedVersion,
                        actualVersion = actualVersion
                    ).failure()
                }

                logger.debug {
                    "Updated VMware configuration: id=${configuration.id.value}, newVersion=${configuration.version}"
                }

                Unit.success()
            } catch (e: Exception) {
                logger.error(e) {
                    "Failed to update VMware configuration: id=${configuration.id.value}"
                }
                VmwareConfigurationError.PersistenceFailure(
                    message = "Failed to update configuration: ${e.message}"
                ).failure()
            }
        }

    override suspend fun existsByTenantId(tenantId: TenantId): Boolean =
        withContext(Dispatchers.IO) {
            dsl.fetchExists(
                dsl.selectFrom(VMWARE_CONFIGURATIONS)
                    .where(VMWARE_CONFIGURATIONS.TENANT_ID.eq(tenantId.value))
            )
        }
}
