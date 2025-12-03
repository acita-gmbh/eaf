/**
 * Admin API functions.
 *
 * Story 2.9: Admin Approval Queue
 * Story 2.10: Request Detail View (Admin)
 *
 * Handles admin-specific operations:
 * - GET /api/admin/requests/pending - Fetch pending VM requests for approval
 * - GET /api/admin/requests/{id} - Fetch detailed request view for admin
 * - GET /api/admin/projects - Fetch distinct projects for filter dropdown
 */

import { createApiHeaders } from './api-client'
import { ApiError } from './vm-requests'
import type { VmSizeSpec, TimelineEventType } from './vm-requests'

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
  } catch {
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
  status: string
  /** ISO 8601 timestamp when the request was created */
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
  requesterHistory: RequestHistorySummary[]
  createdAt: string
}

/**
 * Detailed admin view of a VM request.
 *
 * Story 2.10: Request Detail View (Admin)
 *
 * Includes all information needed for admin decision-making:
 * - Request details (AC 3)
 * - Requester info (AC 2)
 * - Timeline events (AC 5)
 * - Requester history (AC 6)
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
  status: string
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
}

/**
 * Transforms backend admin request detail to frontend format.
 * Flattens nested size object.
 */
function transformAdminRequestDetail(
  backend: BackendAdminRequestDetailResponse
): AdminRequestDetail {
  return {
    id: backend.id,
    vmName: backend.vmName,
    size: backend.size.code,
    cpuCores: backend.size.cpuCores,
    memoryGb: backend.size.memoryGb,
    diskGb: backend.size.diskGb,
    justification: backend.justification,
    status: backend.status,
    projectName: backend.projectName,
    requester: backend.requester,
    timeline: backend.timeline,
    requesterHistory: backend.requesterHistory,
    createdAt: backend.createdAt,
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
