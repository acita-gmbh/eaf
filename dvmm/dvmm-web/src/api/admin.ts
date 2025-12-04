/**
 * Admin API functions.
 *
 * Story 2.9: Admin Approval Queue
 * Story 2.10: Request Detail View (Admin)
 * Story 2.11: Approve/Reject Actions
 *
 * Handles admin-specific operations:
 * - GET /api/admin/requests/pending - Fetch pending VM requests for approval
 * - GET /api/admin/requests/{id} - Fetch detailed request view for admin
 * - GET /api/admin/projects - Fetch distinct projects for filter dropdown
 * - POST /api/admin/requests/{id}/approve - Approve a pending request
 * - POST /api/admin/requests/{id}/reject - Reject a pending request
 */

import { createApiHeaders } from './api-client'
import { ApiError, VM_REQUEST_STATUSES } from './vm-requests'
import type { VmSizeSpec, TimelineEventType, VmRequestStatus } from './vm-requests'

/**
 * Validates that a string is a valid VmRequestStatus.
 * Throws if the status is invalid to fail fast with clear error context.
 */
function validateStatus(status: string, context: string): VmRequestStatus {
  if (!VM_REQUEST_STATUSES.includes(status as VmRequestStatus)) {
    throw new Error(
      `Invalid status "${status}" in ${context}. Expected one of: ${VM_REQUEST_STATUSES.join(', ')}`
    )
  }
  return status as VmRequestStatus
}

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

// ==================== Pending Requests API ====================

/**
 * Backend pending request response format.
 */
interface BackendPendingRequestResponse {
  id: string
  requesterName: string
  vmName: string
  projectName: string
  size: VmSizeSpec
  createdAt: string
}

/**
 * Backend paginated pending requests response.
 */
interface BackendPendingRequestsPageResponse {
  items: BackendPendingRequestResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/**
 * Pending VM request for admin approval queue.
 * Flattens the nested size object for easier display.
 *
 * Story 2.9 AC 2: Required columns
 */
export interface PendingRequest {
  /** Unique request identifier */
  id: string
  /** Name of the user who submitted the request */
  requesterName: string
  /** Requested VM name */
  vmName: string
  /** Project display name */
  projectName: string
  /** VM size code (S, M, L, XL) */
  size: string
  /** CPU cores for this size */
  cpuCores: number
  /** Memory in GB for this size */
  memoryGb: number
  /** Disk size in GB for this size */
  diskGb: number
  /** ISO 8601 timestamp when request was created (for age calculation) */
  createdAt: string
}

/**
 * Paginated response for pending requests.
 */
export interface PendingRequestsPage {
  items: PendingRequest[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/**
 * Parameters for fetching pending requests.
 */
export interface GetPendingRequestsParams {
  /** Optional project filter (UUID) */
  projectId?: string
  /** Page number (0-based) */
  page?: number
  /** Page size (1-100) */
  size?: number
}

/**
 * Transforms backend pending request to frontend format.
 * Flattens nested size object.
 */
function transformPendingRequest(backend: BackendPendingRequestResponse): PendingRequest {
  return {
    id: backend.id,
    requesterName: backend.requesterName,
    vmName: backend.vmName,
    projectName: backend.projectName,
    size: backend.size.code,
    cpuCores: backend.size.cpuCores,
    memoryGb: backend.size.memoryGb,
    diskGb: backend.size.diskGb,
    createdAt: backend.createdAt,
  }
}

/**
 * Parses response body, handling empty responses.
 */
async function parseResponseBody(response: Response): Promise<unknown> {
  const contentLength = response.headers.get('content-length')
  if (response.status === 204 || contentLength === '0') {
    return {}
  }

  const text = await response.text()
  if (!text) {
    return {}
  }

  try {
    return JSON.parse(text)
  } catch (error) {
    // Log invalid JSON for debugging - helps identify backend issues
    console.warn('[Admin API] Invalid JSON response, falling back to text message', {
      url: response.url,
      status: response.status,
      textPreview: text.substring(0, 100),
      error,
    })
    return { message: text }
  }
}

/**
 * Fetches pending VM requests for admin approval queue.
 *
 * Story 2.9: Admin Approval Queue
 * - AC 1: Open Requests section
 * - AC 2: List displays required columns
 * - AC 3: Sorted oldest first (handled by backend)
 * - AC 5: Optional project filter
 *
 * @param params - Query parameters (projectId, page, size)
 * @param accessToken - OAuth2 access token (must have admin role)
 * @returns Paginated pending requests with flattened size fields
 * @throws ApiError on 403 Forbidden (non-admin) or 500 Internal Error
 */
export async function getPendingRequests(
  params: GetPendingRequestsParams,
  accessToken: string
): Promise<PendingRequestsPage> {
  const queryParams = new URLSearchParams()

  if (params.projectId) {
    queryParams.set('projectId', params.projectId)
  }
  if (params.page !== undefined) {
    queryParams.set('page', String(params.page))
  }
  if (params.size !== undefined) {
    queryParams.set('size', String(params.size))
  }

  const queryString = queryParams.toString()
  const url = queryString
    ? `${API_BASE_URL}/api/admin/requests/pending?${queryString}`
    : `${API_BASE_URL}/api/admin/requests/pending`

  const response = await fetch(url, {
    method: 'GET',
    headers: createApiHeaders(accessToken, false),
    credentials: 'include',
  })

  const responseBody = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, responseBody)
  }

  const backendResponse = responseBody as BackendPendingRequestsPageResponse
  return {
    items: backendResponse.items.map(transformPendingRequest),
    page: backendResponse.page,
    size: backendResponse.size,
    totalElements: backendResponse.totalElements,
    totalPages: backendResponse.totalPages,
  }
}

// ==================== Projects API ====================

/**
 * Project for filter dropdown.
 *
 * Story 2.9 AC 5: Project filter dropdown
 */
export interface Project {
  /** Project unique identifier (UUID) */
  id: string
  /** Project display name */
  name: string
}

/**
 * Fetches distinct projects for the filter dropdown.
 *
 * Story 2.9 AC 5: Project filter dropdown
 *
 * Returns all projects that have VM requests in the tenant,
 * sorted alphabetically by name (handled by backend).
 *
 * @param accessToken - OAuth2 access token (must have admin role)
 * @returns List of projects sorted by name
 * @throws ApiError on 403 Forbidden (non-admin) or 500 Internal Error
 */
export async function getProjects(accessToken: string): Promise<Project[]> {
  const response = await fetch(`${API_BASE_URL}/api/admin/projects`, {
    method: 'GET',
    headers: createApiHeaders(accessToken, false),
    credentials: 'include',
  })

  const responseBody = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, responseBody)
  }

  return responseBody as Project[]
}

// ==================== Admin Request Detail API (Story 2.10) ====================

/**
 * Requester information for admin view.
 *
 * Story 2.10 AC 2: Requester Information displayed
 */
export interface RequesterInfo {
  /** Unique identifier for the requester */
  id: string
  /** Display name of the requester */
  name: string
  /** Email address of the requester */
  email: string
  /** Role/title of the requester */
  role: string
}

/**
 * Timeline event entry.
 *
 * Story 2.10 AC 5: Timeline events displayed
 */
export interface TimelineEvent {
  /** Type of event (CREATED, APPROVED, REJECTED, CANCELLED, etc.) */
  eventType: TimelineEventType
  /** Name of the user who performed the action (null for system events) */
  actorName: string | null
  /** Additional event details (e.g., rejection reason) */
  details: string | null
  /** ISO 8601 timestamp when the event occurred */
  occurredAt: string
}

/**
 * Summary of a requester's previous request for history display.
 *
 * Story 2.10 AC 6: Requester History shown
 */
export interface RequestHistorySummary {
  /** Unique identifier for the request */
  id: string
  /** Name of the VM requested */
  vmName: string
  /** Current status of the request */
  status: VmRequestStatus
  /** ISO 8601 timestamp when the request was created */
  createdAt: string
}

/**
 * Backend request history summary format (status as string).
 */
interface BackendRequestHistorySummary {
  id: string
  vmName: string
  status: string
  createdAt: string
}

/**
 * Backend admin request detail response format.
 */
interface BackendAdminRequestDetailResponse {
  id: string
  vmName: string
  size: VmSizeSpec
  justification: string
  status: string
  projectName: string
  requester: RequesterInfo
  timeline: TimelineEvent[]
  requesterHistory: BackendRequestHistorySummary[]
  createdAt: string
  version: number
}

/**
 * Detailed admin view of a VM request.
 *
 * Story 2.10: Request Detail View (Admin)
 * Story 2.11: Approve/Reject Actions (version field for optimistic locking)
 *
 * Includes all information needed for admin decision-making:
 * - Request details (AC 3)
 * - Requester info (AC 2)
 * - Timeline events (AC 5)
 * - Requester history (AC 6)
 * - Version for optimistic locking (Story 2.11)
 */
export interface AdminRequestDetail {
  /** Unique request identifier */
  id: string
  /** Requested VM name */
  vmName: string
  /** VM size code (S, M, L, XL) */
  size: string
  /** CPU cores for this size */
  cpuCores: number
  /** Memory in GB for this size */
  memoryGb: number
  /** Disk size in GB for this size */
  diskGb: number
  /** Business justification provided */
  justification: string
  /** Current request status */
  status: VmRequestStatus
  /** Project display name */
  projectName: string
  /** Requester information */
  requester: RequesterInfo
  /** Status change history */
  timeline: TimelineEvent[]
  /** Recent requests from same requester */
  requesterHistory: RequestHistorySummary[]
  /** ISO 8601 timestamp when request was created */
  createdAt: string
  /** Aggregate version for optimistic locking */
  version: number
}

/**
 * Transforms backend admin request detail to frontend format.
 * Flattens nested size object and validates status values.
 *
 * @throws Error if status values are invalid (fail-fast approach)
 */
function transformAdminRequestDetail(
  backend: BackendAdminRequestDetailResponse
): AdminRequestDetail {
  // Validate main request status
  const validatedStatus = validateStatus(
    backend.status,
    `request ${backend.id}`
  )

  // Validate requester history statuses
  const validatedHistory = backend.requesterHistory.map((item, index) => ({
    ...item,
    status: validateStatus(
      item.status,
      `requesterHistory[${index}] for request ${backend.id}`
    ),
  }))

  return {
    id: backend.id,
    vmName: backend.vmName,
    size: backend.size.code,
    cpuCores: backend.size.cpuCores,
    memoryGb: backend.size.memoryGb,
    diskGb: backend.size.diskGb,
    justification: backend.justification,
    status: validatedStatus,
    projectName: backend.projectName,
    requester: backend.requester,
    timeline: backend.timeline,
    requesterHistory: validatedHistory,
    createdAt: backend.createdAt,
    version: backend.version,
  }
}

/**
 * Fetches detailed admin view of a VM request.
 *
 * Story 2.10: Request Detail View (Admin)
 * - AC 1: Page loads with correct request details
 * - AC 2: Requester Information displayed
 * - AC 3: Request Details displayed
 * - AC 5: Timeline events displayed
 * - AC 6: Requester History shown
 *
 * Security: Returns 404 for both not-found and forbidden cases
 * (prevents tenant enumeration attacks).
 *
 * @param requestId - UUID of the request to fetch
 * @param accessToken - OAuth2 access token (must have admin role)
 * @returns Detailed admin request info with flattened size fields
 * @throws ApiError on 404 Not Found or 500 Internal Error
 */
export async function getAdminRequestDetail(
  requestId: string,
  accessToken: string
): Promise<AdminRequestDetail> {
  const response = await fetch(
    `${API_BASE_URL}/api/admin/requests/${requestId}`,
    {
      method: 'GET',
      headers: createApiHeaders(accessToken, false),
      credentials: 'include',
    }
  )

  const responseBody = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, responseBody)
  }

  const backendResponse = responseBody as BackendAdminRequestDetailResponse
  return transformAdminRequestDetail(backendResponse)
}

// ==================== Approve/Reject Actions API (Story 2.11) ====================

/**
 * Response from approve/reject endpoints.
 */
export interface AdminActionResponse {
  /** The ID of the request that was acted on */
  requestId: string
  /** The new status after the action */
  status: 'APPROVED' | 'REJECTED'
}

/**
 * Approves a pending VM request.
 *
 * Story 2.11: Approve/Reject Actions
 *
 * Dispatches an approval command with optimistic locking to prevent
 * lost updates when multiple admins act on the same request.
 *
 * Separation of duties: Admin cannot approve their own request.
 *
 * @param requestId - UUID of the request to approve
 * @param version - Expected aggregate version for optimistic locking
 * @param accessToken - OAuth2 access token (must have admin role)
 * @returns Action response with new status
 * @throws ApiError on:
 *   - 404 Not Found (request doesn't exist or admin trying to approve own request)
 *   - 409 Conflict (concurrent modification)
 *   - 422 Unprocessable Entity (request not in PENDING state)
 */
export async function approveRequest(
  requestId: string,
  version: number,
  accessToken: string
): Promise<AdminActionResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/admin/requests/${requestId}/approve`,
    {
      method: 'POST',
      headers: createApiHeaders(accessToken, true),
      credentials: 'include',
      body: JSON.stringify({ version }),
    }
  )

  const responseBody = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, responseBody)
  }

  return responseBody as AdminActionResponse
}

/**
 * Rejects a pending VM request with a mandatory reason.
 *
 * Story 2.11: Approve/Reject Actions
 *
 * Dispatches a rejection command with optimistic locking and a
 * mandatory reason (10-500 characters) for audit trail.
 *
 * Separation of duties: Admin cannot reject their own request.
 *
 * @param requestId - UUID of the request to reject
 * @param version - Expected aggregate version for optimistic locking
 * @param reason - Rejection reason (10-500 characters)
 * @param accessToken - OAuth2 access token (must have admin role)
 * @returns Action response with new status
 * @throws ApiError on:
 *   - 404 Not Found (request doesn't exist or admin trying to reject own request)
 *   - 409 Conflict (concurrent modification)
 *   - 422 Unprocessable Entity (request not in PENDING state or invalid reason)
 */
export async function rejectRequest(
  requestId: string,
  version: number,
  reason: string,
  accessToken: string
): Promise<AdminActionResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/admin/requests/${requestId}/reject`,
    {
      method: 'POST',
      headers: createApiHeaders(accessToken, true),
      credentials: 'include',
      body: JSON.stringify({ version, reason }),
    }
  )

  const responseBody = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, responseBody)
  }

  return responseBody as AdminActionResponse
}
