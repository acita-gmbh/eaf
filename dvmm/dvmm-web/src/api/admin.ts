/**
 * Admin API functions.
 *
 * Story 2.9: Admin Approval Queue
 *
 * Handles admin-specific operations:
 * - GET /api/admin/requests/pending - Fetch pending VM requests for approval
 * - GET /api/admin/projects - Fetch distinct projects for filter dropdown
 */

import { createApiHeaders } from './api-client'
import { ApiError } from './vm-requests'
import type { VmSizeSpec } from './vm-requests'

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
