package de.acci.dvmm.api.admin

import de.acci.dvmm.application.vmrequest.GetPendingRequestsError
import de.acci.dvmm.application.vmrequest.GetPendingRequestsHandler
import de.acci.dvmm.application.vmrequest.GetPendingRequestsQuery
import de.acci.dvmm.application.vmrequest.VmRequestReadRepository
import de.acci.dvmm.domain.vmrequest.ProjectId
import de.acci.eaf.core.result.Result
import de.acci.eaf.eventsourcing.projection.PageRequest
import de.acci.eaf.tenant.TenantContext
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for admin VM request operations.
 *
 * Story 2.9: Admin Approval Queue
 *
 * All endpoints require ADMIN role via @PreAuthorize.
 * Tenant isolation is handled by PostgreSQL RLS.
 *
 * ## Endpoints
 *
 * - `GET /api/admin/requests/pending` - Get pending requests for approval
 * - `GET /api/admin/projects` - Get distinct projects for filter dropdown
 *
 * ## Error Handling
 *
 * - **401 Unauthorized**: Missing or invalid JWT
 * - **403 Forbidden**: User does not have admin role
 * - **500 Internal Server Error**: Database query failure
 */
private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('admin')")
public class AdminRequestController(
    private val getPendingRequestsHandler: GetPendingRequestsHandler,
    private val readRepository: VmRequestReadRepository
) {

    /**
     * Get pending VM requests for admin approval.
     *
     * Returns a paginated list of PENDING requests from all users in the tenant,
     * sorted by creation date (oldest first - FIFO order for fair processing).
     *
     * AC 1: Admin sees "Open Requests" section
     * AC 2: List displays required columns
     * AC 3: Sorted oldest first
     * AC 5: Filter by project
     * AC 6: Tenant isolation via RLS
     *
     * @param projectId Optional project filter
     * @param page Page number (zero-based, default 0)
     * @param size Page size (default 25, max 100)
     * @param jwt The authenticated admin's JWT
     * @return 200 OK with paginated pending requests, or error response
     */
    @GetMapping("/requests/pending")
    public suspend fun getPendingRequests(
        @RequestParam(required = false) projectId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.current()
        val adminId = jwt.subject

        // Validate pagination parameters
        val validatedSize = size.coerceIn(1, GetPendingRequestsQuery.MAX_PAGE_SIZE)
        val validatedPage = page.coerceAtLeast(0)

        // Parse optional project filter
        val parsedProjectId = projectId?.let {
            try {
                ProjectId(UUID.fromString(it))
            } catch (e: IllegalArgumentException) {
                logger.warn { "Invalid project ID format: $projectId" }
                return ResponseEntity.badRequest().body(
                    mapOf(
                        "error" to "INVALID_PROJECT_ID",
                        "message" to "Invalid project ID format"
                    )
                )
            }
        }

        // Audit log
        logger.info {
            "Admin fetching pending requests: " +
                "adminId=$adminId, " +
                "tenantId=${tenantId.value}, " +
                "projectId=${parsedProjectId?.value}, " +
                "page=$validatedPage, " +
                "size=$validatedSize"
        }

        val query = GetPendingRequestsQuery(
            tenantId = tenantId,
            projectId = parsedProjectId,
            pageRequest = PageRequest(page = validatedPage, size = validatedSize)
        )

        return when (val result = getPendingRequestsHandler.handle(query)) {
            is Result.Success -> {
                ResponseEntity.ok(PendingRequestsPageResponse.fromPagedResponse(result.value))
            }
            is Result.Failure -> handleGetPendingRequestsError(result.error)
        }
    }

    private fun handleGetPendingRequestsError(error: GetPendingRequestsError): ResponseEntity<Any> {
        return when (error) {
            is GetPendingRequestsError.Forbidden -> {
                logger.warn { "Forbidden access to pending requests" }
                ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    mapOf(
                        "error" to "FORBIDDEN",
                        "message" to "Access denied"
                    )
                )
            }
            is GetPendingRequestsError.QueryFailure -> {
                logger.error { "Failed to retrieve pending requests: ${error.message}" }
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    mapOf(
                        "error" to "QUERY_FAILURE",
                        "message" to "Failed to retrieve pending requests"
                    )
                )
            }
        }
    }

    /**
     * Get distinct projects for the filter dropdown.
     *
     * Returns a list of all projects that have VM requests in the tenant,
     * sorted alphabetically by name.
     *
     * AC 5: Project filter dropdown
     *
     * @param jwt The authenticated admin's JWT
     * @return 200 OK with list of projects, or error response on failure
     */
    @GetMapping("/projects")
    public suspend fun getProjects(
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.current()
        val adminId = jwt.subject

        logger.debug { "Admin fetching projects: adminId=$adminId, tenantId=${tenantId.value}" }

        return try {
            val projects = readRepository.findDistinctProjects(tenantId)
            ResponseEntity.ok(projects.map { ProjectResponse.fromSummary(it) })
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve projects for tenant ${tenantId.value}: ${e.message}" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "error" to "QUERY_FAILURE",
                    "message" to "Failed to retrieve projects"
                )
            )
        }
    }
}
