package de.acci.dvmm.domain.exceptions

import de.acci.eaf.core.types.UserId

/**
 * Exception thrown when an admin attempts to approve or reject their own VM request.
 *
 * This enforces the separation of duties principle: a requester cannot approve
 * or reject their own requests, even if they have admin privileges.
 *
 * Example: Admin A submits a VM request, then tries to approve it themselves.
 */
public class SelfApprovalException(
    /** The user ID of the admin who attempted the operation */
    public val adminId: UserId,
    /** The operation that was attempted (approve/reject) */
    public val operation: String
) : RuntimeException(
    "Cannot perform '$operation' on own request: separation of duties requires a different admin"
)
