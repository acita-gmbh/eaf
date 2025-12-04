/**
 * VMware Configuration API functions.
 *
 * Story 3.1: VMware Connection Configuration
 *
 * Handles VMware vCenter configuration operations:
 * - GET /api/admin/vmware-config - Fetch current configuration
 * - PUT /api/admin/vmware-config - Create or update configuration
 * - POST /api/admin/vmware-config/test - Test vCenter connection
 * - GET /api/admin/vmware-config/exists - Check if configuration exists
 */

import { createApiHeaders } from './api-client'
import { ApiError } from './vm-requests'

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

// ==================== Response Types ====================

/**
 * VMware configuration from backend.
 * Password is never returned - only hasPassword flag.
 */
export interface VmwareConfig {
  id: string
  vcenterUrl: string
  username: string
  hasPassword: boolean
  datacenterName: string
  clusterName: string
  datastoreName: string
  networkName: string
  templateName: string
  folderPath: string | null
  verifiedAt: string | null
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
  version: number
}

/**
 * Connection test result.
 */
export interface ConnectionTestResult {
  success: boolean
  vcenterVersion: string
  clusterName: string
  clusterHosts: number
  datastoreFreeGb: number
  message: string
}

/**
 * Connection test error response.
 */
export interface ConnectionTestError {
  success: false
  error: string
  message: string
}

/**
 * Configuration existence check response.
 */
export interface ConfigExistsResponse {
  exists: boolean
  verifiedAt: string | null
}

/**
 * Save configuration response.
 */
export interface SaveConfigResponse {
  id: string
  version: number
  message: string
}

// ==================== Request Types ====================

/**
 * Request body for saving VMware configuration.
 */
export interface SaveVmwareConfigRequest {
  vcenterUrl: string
  username: string
  password: string | null
  datacenterName: string
  clusterName: string
  datastoreName: string
  networkName: string
  templateName?: string
  folderPath?: string
  version: number | null // null = create, number = update
}

/**
 * Request body for testing connection.
 */
export interface TestConnectionRequest {
  vcenterUrl: string
  username: string
  password: string
  datacenterName: string
  clusterName: string
  datastoreName: string
  networkName: string
  templateName?: string
  updateVerifiedAt?: boolean
}

// ==================== Helper Functions ====================

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
    console.warn('[VMware Config API] Invalid JSON response', {
      url: response.url,
      status: response.status,
      textPreview: text.substring(0, 100),
      error,
    })
    return { message: text }
  }
}

// ==================== API Functions ====================

/**
 * Fetches current VMware configuration.
 *
 * Story 3.1 AC-3.1.1: Get saved configuration
 *
 * @param accessToken - OAuth2 access token (must have admin role)
 * @returns Configuration or null if not configured
 * @throws ApiError on 500 Internal Error
 */
export async function getVmwareConfig(
  accessToken: string
): Promise<VmwareConfig | null> {
  const response = await fetch(`${API_BASE_URL}/api/admin/vmware-config`, {
    method: 'GET',
    headers: createApiHeaders(accessToken, false),
    credentials: 'include',
  })

  if (response.status === 404) {
    return null // Not configured yet
  }

  const responseBody = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, responseBody)
  }

  return responseBody as VmwareConfig
}

/**
 * Saves VMware configuration (create or update).
 *
 * Story 3.1 AC-3.1.1: Save vCenter settings
 * Story 3.1 AC-3.1.4: Password encrypted before storage
 *
 * @param data - Configuration to save
 * @param accessToken - OAuth2 access token (must have admin role)
 * @returns Save result with new version
 * @throws ApiError on validation, conflict, or server error
 */
export async function saveVmwareConfig(
  data: SaveVmwareConfigRequest,
  accessToken: string
): Promise<SaveConfigResponse> {
  const response = await fetch(`${API_BASE_URL}/api/admin/vmware-config`, {
    method: 'PUT',
    headers: createApiHeaders(accessToken, true),
    credentials: 'include',
    body: JSON.stringify(data),
  })

  const responseBody = await parseResponseBody(response)

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, responseBody)
  }

  return responseBody as SaveConfigResponse
}

/**
 * Tests connection to vCenter with provided credentials.
 *
 * Story 3.1 AC-3.1.2: "Test Connection" validates connectivity
 * Story 3.1 AC-3.1.3: Real-time feedback on validation errors
 *
 * @param data - Connection test parameters
 * @param accessToken - OAuth2 access token (must have admin role)
 * @returns Connection test result or error
 * @throws ApiError on server error (not connection errors)
 */
export async function testVmwareConnection(
  data: TestConnectionRequest,
  accessToken: string
): Promise<ConnectionTestResult | ConnectionTestError> {
  const response = await fetch(
    `${API_BASE_URL}/api/admin/vmware-config/test`,
    {
      method: 'POST',
      headers: createApiHeaders(accessToken, true),
      credentials: 'include',
      body: JSON.stringify(data),
    }
  )

  const responseBody = await parseResponseBody(response)

  // 422 returns structured error, not exception
  if (response.status === 422) {
    return responseBody as ConnectionTestError
  }

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, responseBody)
  }

  return responseBody as ConnectionTestResult
}

/**
 * Checks if VMware configuration exists for the tenant.
 *
 * Story 3.1 AC-3.1.5: Lightweight check for warning banner
 *
 * @param accessToken - OAuth2 access token (must have admin role)
 * @returns Existence status with optional verifiedAt timestamp
 */
export async function checkVmwareConfigExists(
  accessToken: string
): Promise<ConfigExistsResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/admin/vmware-config/exists`,
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

  return responseBody as ConfigExistsResponse
}

/**
 * Type guard to check if test result is an error.
 */
export function isConnectionTestError(
  result: ConnectionTestResult | ConnectionTestError
): result is ConnectionTestError {
  return !result.success
}
