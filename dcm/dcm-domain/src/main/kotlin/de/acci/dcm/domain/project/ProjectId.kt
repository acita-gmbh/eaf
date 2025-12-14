package de.acci.dcm.domain.project

/**
 * Type alias for ProjectId from vmrequest package.
 *
 * ProjectId conceptually belongs to the Project aggregate domain,
 * but is currently defined in vmrequest for historical reasons.
 * This alias allows project-related code to use a canonical import path.
 *
 * TODO: In a future refactoring, move the actual ProjectId class here
 * and update the 33+ files that import from vmrequest.
 */
public typealias ProjectId = de.acci.dcm.domain.vmrequest.ProjectId
