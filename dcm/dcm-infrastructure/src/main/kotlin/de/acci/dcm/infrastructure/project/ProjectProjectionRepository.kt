package de.acci.dcm.infrastructure.project

import de.acci.dcm.infrastructure.jooq.`public`.tables.Projects.Companion.PROJECTS
import de.acci.dcm.infrastructure.jooq.`public`.tables.pojos.Projects
import de.acci.dcm.infrastructure.projection.BaseProjectionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SortField
import org.jooq.Table
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Repository for querying and updating Project projections.
 *
 * RLS NOTE: Tenant filtering is handled automatically by PostgreSQL Row-Level Security.
 * All queries through this repository are automatically filtered to the current tenant
 * based on the `app.tenant_id` session variable set by the connection customizer.
 *
 * @param dsl The jOOQ DSLContext for database operations
 * @param ioDispatcher Dispatcher for blocking I/O operations (injectable for testing)
 */
public class ProjectProjectionRepository(
    dsl: DSLContext,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BaseProjectionRepository<Projects>(dsl, ioDispatcher) {

    override fun mapRecord(record: Record): Projects = Projects(
        id = requireNotNull(record.get(PROJECTS.ID)) { "PROJECTS.ID is null" },
        tenantId = requireNotNull(record.get(PROJECTS.TENANT_ID)) { "PROJECTS.TENANT_ID is null" },
        name = requireNotNull(record.get(PROJECTS.NAME)) { "PROJECTS.NAME is null" },
        description = record.get(PROJECTS.DESCRIPTION),
        status = record.get(PROJECTS.STATUS),
        createdBy = requireNotNull(record.get(PROJECTS.CREATED_BY)) { "PROJECTS.CREATED_BY is null" },
        createdAt = requireNotNull(record.get(PROJECTS.CREATED_AT)) { "PROJECTS.CREATED_AT is null" },
        updatedAt = requireNotNull(record.get(PROJECTS.UPDATED_AT)) { "PROJECTS.UPDATED_AT is null" },
        version = record.get(PROJECTS.VERSION)
    )

    override fun table(): Table<*> = PROJECTS

    override fun defaultOrderBy(): List<SortField<*>> = listOf(
        PROJECTS.CREATED_AT.desc()
    )

    /**
     * Inserts a new project projection.
     *
     * @param projection The project data to insert
     */
    public suspend fun insert(projection: Projects): Unit = withContext(ioDispatcher) {
        dsl.insertInto(PROJECTS)
            .set(PROJECTS.ID, projection.id)
            .set(PROJECTS.TENANT_ID, projection.tenantId)
            .set(PROJECTS.NAME, projection.name)
            .set(PROJECTS.DESCRIPTION, projection.description)
            .set(PROJECTS.STATUS, projection.status)
            .set(PROJECTS.CREATED_BY, projection.createdBy)
            .set(PROJECTS.CREATED_AT, projection.createdAt)
            .set(PROJECTS.UPDATED_AT, projection.updatedAt)
            .set(PROJECTS.VERSION, projection.version)
            .execute()
    }

    /**
     * Updates a project's name and description.
     *
     * @param id The project ID
     * @param name The new name
     * @param description The new description
     * @param version The new version
     * @return Number of rows updated (0 if not found)
     */
    public suspend fun update(
        id: UUID,
        name: String,
        description: String?,
        version: Int
    ): Int = withContext(ioDispatcher) {
        dsl.update(PROJECTS)
            .set(PROJECTS.NAME, name)
            .set(PROJECTS.DESCRIPTION, description)
            .set(PROJECTS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .set(PROJECTS.VERSION, version)
            .where(PROJECTS.ID.eq(id))
            .execute()
    }

    /**
     * Updates a project's status.
     *
     * @param id The project ID
     * @param status The new status (ACTIVE or ARCHIVED)
     * @param version The new version
     * @return Number of rows updated (0 if not found)
     */
    public suspend fun updateStatus(
        id: UUID,
        status: String,
        version: Int
    ): Int = withContext(ioDispatcher) {
        dsl.update(PROJECTS)
            .set(PROJECTS.STATUS, status)
            .set(PROJECTS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .set(PROJECTS.VERSION, version)
            .where(PROJECTS.ID.eq(id))
            .execute()
    }

    /**
     * Finds a project by ID.
     *
     * @param id The project ID
     * @return The project if found, null otherwise
     */
    public suspend fun findById(id: UUID): Projects? = withContext(ioDispatcher) {
        dsl.selectFrom(PROJECTS)
            .where(PROJECTS.ID.eq(id))
            .fetchOne()
            ?.let { mapRecord(it) }
    }

    /**
     * Finds a project by name (case-insensitive).
     *
     * Used for uniqueness validation.
     *
     * @param name The project name to search for
     * @return The project ID if found, null otherwise
     */
    public suspend fun findIdByName(name: String): UUID? = withContext(ioDispatcher) {
        dsl.select(PROJECTS.ID)
            .from(PROJECTS)
            .where(PROJECTS.NAME.equalIgnoreCase(name))
            .fetchOne()
            ?.get(PROJECTS.ID)
    }

    /**
     * Finds all projects with a specific status.
     *
     * @param status The status to filter by
     * @return List of projects with that status
     */
    public suspend fun findByStatus(status: String): List<Projects> = withContext(ioDispatcher) {
        dsl.selectFrom(PROJECTS)
            .where(PROJECTS.STATUS.eq(status))
            .orderBy(defaultOrderBy())
            .fetch()
            .map { mapRecord(it) }
    }
}
