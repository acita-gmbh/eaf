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
