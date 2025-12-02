/**
 * VM Request API functions.
 *
 * Handles CQRS write-side operations for VM requests.
 */

import { createApiHeaders } from './api-client'

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

/**
 * API Error with structured response body.
 */
export class ApiError extends Error {
  status: number
  statusText: string
  body: ValidationErrorResponse | QuotaExceededResponse | unknown

  constructor(
    status: number,
    statusText: string,
    body: ValidationErrorResponse | QuotaExceededResponse | unknown
  ) {
    super(`API Error: ${status} ${statusText}`)
    this.name = 'ApiError'
    this.status = status
    this.statusText = statusText
    this.body = body
  }
}

/**
 * Validation error response from backend.
 */
export interface ValidationErrorResponse {
  type: 'validation'
  errors: Array<{
    field: string
    message: string
  }>
}

/**
 * Quota exceeded error response from backend.
 */
export interface QuotaExceededResponse {
  type: 'quota_exceeded'
  message: string
  available: number
  requested: number
}

/**
 * Request payload for creating a VM request.
 */
export interface CreateVmRequestPayload {
  vmName: string
  projectId: string
  size: 'S' | 'M' | 'L' | 'XL'
  justification: string
}

/**
 * VM size specifications.
 */
export interface VmSizeSpec {
  code: string
  cpuCores: number
  memoryGb: number
  diskGb: number
}

/**
 * Response from successful VM request creation.
 *
 * Note: projectName is null on creation response (command side) and will be
 * populated when reading from projections (query side).
 */
export interface VmRequestResponse {
  id: string
  vmName: string
  projectId: string
  projectName: string | null
  size: VmSizeSpec
  status: 'PENDING'
  createdAt: string
}

/**
 * Type guard for validation error response.
 */
export function isValidationError(body: unknown): body is ValidationErrorResponse {
  return (
    typeof body === 'object' &&
    body !== null &&
    'type' in body &&
    (body as Record<string, unknown>).type === 'validation' &&
    'errors' in body &&
    Array.isArray((body as Record<string, unknown>).errors)
  )
}

/**
 * Type guard for quota exceeded error response.
 */
export function isQuotaExceededError(body: unknown): body is QuotaExceededResponse {
  return (
    typeof body === 'object' &&
    body !== null &&
    'type' in body &&
    (body as Record<string, unknown>).type === 'quota_exceeded'
  )
}

/**
 * Creates a new VM request.
 *
 * @param payload - The request data
 * @param accessToken - OAuth2 access token
 * @returns The created VM request
 * @throws ApiError on failure with structured error body
 */
export async function createVmRequest(
  payload: CreateVmRequestPayload,
  accessToken: string
): Promise<VmRequestResponse> {
  const response = await fetch(`${API_BASE_URL}/api/requests`, {
    method: 'POST',
    headers: createApiHeaders(accessToken, true), // Include CSRF for mutations
    credentials: 'include',
    body: JSON.stringify(payload),
  })

  // Parse response body for both success and error cases
  const responseBody = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, responseBody)
  }

  // Cast to expected type
  const body = responseBody as Partial<VmRequestResponse>

  // Extract ID from Location header if body doesn't have it
  if (!body.id) {
    const location = response.headers.get('Location')
    if (location) {
      const id = location.split('/').pop()
      return { ...body, id } as VmRequestResponse
    }
  }

  return body as VmRequestResponse
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

// ==================== My Requests List API ====================

/**
 * VM request status values.
 */
export type VmRequestStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'PROVISIONING'
  | 'READY'
  | 'FAILED'

/**
 * Size object as returned by the backend API.
 */
interface BackendVmSizeResponse {
  code: string
  cpuCores: number
  memoryGb: number
  diskGb: number
}

/**
 * Raw VM request summary as returned by the backend API.
 * The size is nested as a VmSizeResponse object.
 */
interface BackendVmRequestSummary {
  id: string
  requesterName: string
  projectId: string
  projectName: string
  vmName: string
  size: BackendVmSizeResponse
  justification: string
  status: VmRequestStatus
  createdAt: string
  updatedAt: string
}

/**
 * Raw paginated response from the backend API.
 */
interface BackendPagedVmRequestsResponse {
  items: BackendVmRequestSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

/**
 * VM request summary for list view (frontend format).
 *
 * Note: tenantId and requesterId are optional because they are not currently
 * returned by the backend API (omitted for security - see PagedVmRequestsResponse.kt).
 * They are included as optional fields for future admin views if needed.
 *
 * The size fields are flattened from the backend's nested VmSizeResponse object
 * for easier display rendering in components.
 */
export interface VmRequestSummary {
  id: string
  tenantId?: string
  requesterId?: string
  requesterName: string
  projectId: string
  projectName: string
  vmName: string
  size: string
  cpuCores: number
  memoryGb: number
  diskGb: number
  justification: string
  status: VmRequestStatus
  createdAt: string
  updatedAt: string
}

/**
 * Transforms a backend VM request summary to the frontend format.
 * Flattens the nested size object into individual fields.
 */
function transformVmRequestSummary(backend: BackendVmRequestSummary): VmRequestSummary {
  return {
    id: backend.id,
    requesterName: backend.requesterName,
    projectId: backend.projectId,
    projectName: backend.projectName,
    vmName: backend.vmName,
    size: backend.size.code,
    cpuCores: backend.size.cpuCores,
    memoryGb: backend.size.memoryGb,
    diskGb: backend.size.diskGb,
    justification: backend.justification,
    status: backend.status,
    createdAt: backend.createdAt,
    updatedAt: backend.updatedAt,
  }
}

/**
 * Paginated response for VM requests.
 */
export interface PagedVmRequestsResponse {
  items: VmRequestSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

/**
 * Parameters for fetching my requests.
 */
export interface GetMyRequestsParams {
  page?: number
  size?: number
}

/**
 * Gets the current user's VM requests.
 *
 * Transforms the backend response to flatten the nested size object
 * into individual fields (cpuCores, memoryGb, diskGb) for easier
 * display rendering in components.
 *
 * @param params - Pagination parameters
 * @param accessToken - OAuth2 access token
 * @returns Paginated list of VM requests with flattened size fields
 * @throws ApiError on failure
 */
export async function getMyRequests(
  params: GetMyRequestsParams,
  accessToken: string
): Promise<PagedVmRequestsResponse> {
  const queryParams = new URLSearchParams()
  if (params.page !== undefined) {
    queryParams.set('page', String(params.page))
  }
  if (params.size !== undefined) {
    queryParams.set('size', String(params.size))
  }

  const queryString = queryParams.toString()
  const url = queryString
    ? `${API_BASE_URL}/api/requests/my?${queryString}`
    : `${API_BASE_URL}/api/requests/my`

  const response = await fetch(url, {
    method: 'GET',
    headers: createApiHeaders(accessToken, false),
    credentials: 'include',
  })

  const responseBody = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, responseBody)
  }

  // Transform backend response to flatten nested size objects
  const backendResponse = responseBody as BackendPagedVmRequestsResponse
  return {
    items: backendResponse.items.map(transformVmRequestSummary),
    page: backendResponse.page,
    size: backendResponse.size,
    totalElements: backendResponse.totalElements,
    totalPages: backendResponse.totalPages,
    hasNext: backendResponse.hasNext,
    hasPrevious: backendResponse.hasPrevious,
  }
}

// ==================== Cancel Request API ====================

/**
 * Not found error response from backend.
 */
export interface NotFoundErrorResponse {
  type: 'not_found'
  message: string
}

/**
 * Forbidden error response from backend.
 */
export interface ForbiddenErrorResponse {
  type: 'forbidden'
  message: string
}

/**
 * Invalid state error response from backend.
 */
export interface InvalidStateErrorResponse {
  type: 'invalid_state'
  message: string
  currentState: string
}

/**
 * Type guard for not found error response.
 */
export function isNotFoundError(body: unknown): body is NotFoundErrorResponse {
  return (
    typeof body === 'object' &&
    body !== null &&
    'type' in body &&
    (body as Record<string, unknown>).type === 'not_found'
  )
}

/**
 * Type guard for forbidden error response.
 */
export function isForbiddenError(body: unknown): body is ForbiddenErrorResponse {
  return (
    typeof body === 'object' &&
    body !== null &&
    'type' in body &&
    (body as Record<string, unknown>).type === 'forbidden'
  )
}

/**
 * Type guard for invalid state error response.
 */
export function isInvalidStateError(body: unknown): body is InvalidStateErrorResponse {
  return (
    typeof body === 'object' &&
    body !== null &&
    'type' in body &&
    (body as Record<string, unknown>).type === 'invalid_state'
  )
}

/**
 * Response from successful request cancellation.
 */
export interface CancelRequestResponse {
  message: string
  requestId: string
}

/**
 * Payload for cancelling a request.
 */
export interface CancelRequestPayload {
  reason?: string
}

/**
 * Cancels a pending VM request.
 *
 * @param requestId - The ID of the request to cancel
 * @param payload - Optional cancellation reason
 * @param accessToken - OAuth2 access token
 * @returns Success response with request ID
 * @throws ApiError on failure (404 not found, 403 forbidden, 409 invalid state)
 */
export async function cancelRequest(
  requestId: string,
  payload: CancelRequestPayload | undefined,
  accessToken: string
): Promise<CancelRequestResponse> {
  const response = await fetch(`${API_BASE_URL}/api/requests/${requestId}/cancel`, {
    method: 'POST',
    headers: createApiHeaders(accessToken, true), // Include CSRF for mutations
    credentials: 'include',
    body: JSON.stringify(payload ?? {}), // Always send body for Content-Type consistency
  })

  const responseBody = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, responseBody)
  }

  return responseBody as CancelRequestResponse
}

// ==================== Request Detail & Timeline API ====================

/**
 * Timeline event types.
 */
export type TimelineEventType =
  | 'CREATED'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'PROVISIONING_STARTED'
  | 'PROVISIONING_QUEUED'
  | 'VM_READY'

/**
 * A single timeline event for display.
 */
export interface TimelineEvent {
  /** Event type (CREATED, APPROVED, REJECTED, CANCELLED, etc.) */
  eventType: TimelineEventType
  /** Display name of the actor (null for system events) */
  actorName: string | null
  /** Additional event details (e.g., rejection reason as JSON) */
  details: string | null
  /** ISO 8601 timestamp when the event occurred */
  occurredAt: string
}

/**
 * Backend response for VM request detail with timeline.
 */
interface BackendVmRequestDetailResponse {
  id: string
  vmName: string
  size: BackendVmSizeResponse
  justification: string
  status: VmRequestStatus
  projectName: string
  requesterName: string
  createdAt: string
  timeline: TimelineEvent[]
}

/**
 * VM size response with resource specifications.
 */
export interface VmSizeResponse {
  code: string
  cpuCores: number
  memoryGb: number
  diskGb: number
}

/**
 * VM request detail with timeline.
 * Size is a nested object containing resource specifications.
 */
export interface VmRequestDetailResponse {
  id: string
  vmName: string
  size: VmSizeResponse
  justification: string
  status: VmRequestStatus
  projectName: string
  requesterName: string
  createdAt: string
  timeline: TimelineEvent[]
}

/**
 * Gets detailed information about a specific VM request with timeline.
 *
 * @param requestId - The ID of the request to retrieve
 * @param accessToken - OAuth2 access token
 * @returns Request detail with timeline events
 * @throws ApiError on failure (404 not found)
 */
export async function getRequestDetail(
  requestId: string,
  accessToken: string
): Promise<VmRequestDetailResponse> {
  const response = await fetch(`${API_BASE_URL}/api/requests/${requestId}`, {
    method: 'GET',
    headers: createApiHeaders(accessToken, false),
    credentials: 'include',
  })

  const responseBody = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, responseBody)
  }

  // Transform backend response to flatten nested size object
  const backendResponse = responseBody as BackendVmRequestDetailResponse
  return {
    id: backendResponse.id,
    vmName: backendResponse.vmName,
    size: backendResponse.size.code,
    cpuCores: backendResponse.size.cpuCores,
    memoryGb: backendResponse.size.memoryGb,
    diskGb: backendResponse.size.diskGb,
    justification: backendResponse.justification,
    status: backendResponse.status,
    projectName: backendResponse.projectName,
    requesterName: backendResponse.requesterName,
    createdAt: backendResponse.createdAt,
    timeline: backendResponse.timeline,
  }
}
