package de.acci.dcm.infrastructure.project

import de.acci.dcm.infrastructure.jooq.`public`.tables.ProjectMembers.Companion.PROJECT_MEMBERS
import de.acci.dcm.infrastructure.jooq.`public`.tables.pojos.ProjectMembers
import de.acci.dcm.infrastructure.projection.BaseProjectionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SortField
import org.jooq.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Repository for querying and updating Project Member projections.
 *
 * RLS NOTE: Tenant filtering is handled automatically by PostgreSQL Row-Level Security.
 *
 * @param dsl The jOOQ DSLContext for database operations
 * @param ioDispatcher Dispatcher for blocking I/O operations (injectable for testing)
 */
public class ProjectMemberProjectionRepository(
    dsl: DSLContext,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BaseProjectionRepository<ProjectMembers>(dsl, ioDispatcher) {

    override fun mapRecord(record: Record): ProjectMembers = ProjectMembers(
        id = record.get(PROJECT_MEMBERS.ID)!!,
        projectId = record.get(PROJECT_MEMBERS.PROJECT_ID)!!,
        tenantId = record.get(PROJECT_MEMBERS.TENANT_ID)!!,
        userId = record.get(PROJECT_MEMBERS.USER_ID)!!,
        role = record.get(PROJECT_MEMBERS.ROLE)!!,
        assignedBy = record.get(PROJECT_MEMBERS.ASSIGNED_BY)!!,
        assignedAt = record.get(PROJECT_MEMBERS.ASSIGNED_AT)!!,
        version = record.get(PROJECT_MEMBERS.VERSION)
    )

    override fun table(): Table<*> = PROJECT_MEMBERS

    override fun defaultOrderBy(): List<SortField<*>> = listOf(
        PROJECT_MEMBERS.ASSIGNED_AT.desc()
    )

    /**
     * Inserts a new project member.
     *
     * @param member The member data to insert
     */
    public suspend fun insert(member: ProjectMembers): Unit = withContext(ioDispatcher) {
        dsl.insertInto(PROJECT_MEMBERS)
            .set(PROJECT_MEMBERS.PROJECT_ID, member.projectId)
            .set(PROJECT_MEMBERS.TENANT_ID, member.tenantId)
            .set(PROJECT_MEMBERS.USER_ID, member.userId)
            .set(PROJECT_MEMBERS.ROLE, member.role)
            .set(PROJECT_MEMBERS.ASSIGNED_BY, member.assignedBy)
            .set(PROJECT_MEMBERS.ASSIGNED_AT, member.assignedAt)
            .set(PROJECT_MEMBERS.VERSION, member.version ?: 1)
            .execute()
    }

    /**
     * Updates a member's role.
     *
     * @param projectId The project ID
     * @param userId The user ID
     * @param role The new role
     * @return Number of rows updated (0 if not found)
     */
    public suspend fun updateRole(
        projectId: UUID,
        userId: UUID,
        role: String
    ): Int = withContext(ioDispatcher) {
        dsl.update(PROJECT_MEMBERS)
            .set(PROJECT_MEMBERS.ROLE, role)
            .set(PROJECT_MEMBERS.VERSION, PROJECT_MEMBERS.VERSION.plus(1))
            .where(
                PROJECT_MEMBERS.PROJECT_ID.eq(projectId)
                    .and(PROJECT_MEMBERS.USER_ID.eq(userId))
            )
            .execute()
    }

    /**
     * Removes a member from a project.
     *
     * @param projectId The project ID
     * @param userId The user ID
     * @return Number of rows deleted (0 if not found)
     */
    public suspend fun remove(
        projectId: UUID,
        userId: UUID
    ): Int = withContext(ioDispatcher) {
        dsl.deleteFrom(PROJECT_MEMBERS)
            .where(
                PROJECT_MEMBERS.PROJECT_ID.eq(projectId)
                    .and(PROJECT_MEMBERS.USER_ID.eq(userId))
            )
            .execute()
    }

    /**
     * Finds all members of a project.
     *
     * @param projectId The project ID
     * @return List of members
     */
    public suspend fun findByProjectId(projectId: UUID): List<ProjectMembers> = withContext(ioDispatcher) {
        dsl.selectFrom(PROJECT_MEMBERS)
            .where(PROJECT_MEMBERS.PROJECT_ID.eq(projectId))
            .orderBy(defaultOrderBy())
            .fetch()
            .map { mapRecord(it) }
    }

    /**
     * Finds all projects a user is a member of.
     *
     * @param userId The user ID
     * @return List of member records (use projectId to find project details)
     */
    public suspend fun findByUserId(userId: UUID): List<ProjectMembers> = withContext(ioDispatcher) {
        dsl.selectFrom(PROJECT_MEMBERS)
            .where(PROJECT_MEMBERS.USER_ID.eq(userId))
            .orderBy(defaultOrderBy())
            .fetch()
            .map { mapRecord(it) }
    }

    /**
     * Finds a specific member in a project.
     *
     * @param projectId The project ID
     * @param userId The user ID
     * @return The member if found, null otherwise
     */
    public suspend fun findByProjectAndUser(
        projectId: UUID,
        userId: UUID
    ): ProjectMembers? = withContext(ioDispatcher) {
        dsl.selectFrom(PROJECT_MEMBERS)
            .where(
                PROJECT_MEMBERS.PROJECT_ID.eq(projectId)
                    .and(PROJECT_MEMBERS.USER_ID.eq(userId))
            )
            .fetchOne()
            ?.let { mapRecord(it) }
    }
}
