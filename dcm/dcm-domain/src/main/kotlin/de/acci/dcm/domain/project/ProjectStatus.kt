package de.acci.dcm.domain.project

/**
 * Status of a project in its lifecycle.
 *
 * Projects start in [ACTIVE] status and can be archived/unarchived.
 */
public enum class ProjectStatus {
    /** Project is active and can have VMs and members */
    ACTIVE,

    /** Project is archived (soft-deleted) - no modifications allowed */
    ARCHIVED;

    /**
     * Returns true if the project can be modified (members assigned, updated, etc.)
     */
    public fun canBeModified(): Boolean = this == ACTIVE
}
