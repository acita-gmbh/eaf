package de.acci.dcm.domain.project

/**
 * Role assigned to a user within a project.
 *
 * Determines what actions a user can perform within the project context.
 */
public enum class ProjectRole {
    /** Regular project member - can request VMs and view project resources */
    MEMBER,

    /** Project administrator - can manage members, edit project, and approve requests */
    PROJECT_ADMIN
}
