package de.acci.dcm.domain.project

import de.acci.eaf.core.types.UserId
import java.time.Instant

/**
 * Value object representing a project member assignment.
 *
 * Captures who is assigned to the project, their role, and audit information
 * about when and by whom they were assigned.
 */
public data class ProjectMember(
    /** The assigned user */
    val userId: UserId,
    /** The user's role in this project */
    val role: ProjectRole,
    /** Who assigned this user (admin or creator) */
    val assignedBy: UserId,
    /** When the assignment was made */
    val assignedAt: Instant
) {
    /**
     * Returns true if this member has admin privileges.
     */
    public fun isAdmin(): Boolean = role == ProjectRole.PROJECT_ADMIN
}
