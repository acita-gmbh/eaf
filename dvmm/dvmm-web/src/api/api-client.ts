/**
 * API client for DVMM backend.
 *
 * Handles:
 * - CSRF token management (X-XSRF-TOKEN header)
 * - Bearer token authentication
 * - Base URL configuration
 */

const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

/**
 * Reads CSRF token directly from the XSRF-TOKEN cookie.
 * Reading fresh from cookie prevents staleness in multi-tab scenarios
 * and ensures token rotation is properly handled.
 *
 * @returns The CSRF token value or null if not found
 */
function getCsrfTokenFromCookie(): string | null {
  const cookies = document.cookie.split(';')
  for (const cookie of cookies) {
    const [name, value] = cookie.trim().split('=')
    if (name === 'XSRF-TOKEN') {
      return value
    }
  }
  return null
}

/**
 * Fetches CSRF token from the backend.
 * This triggers the backend to set the XSRF-TOKEN cookie.
 * Should be called after successful authentication.
 */
export async function fetchCsrfToken(accessToken: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/csrf`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
    },
    credentials: 'include',
  })

  if (!response.ok) {
    throw new Error(`Failed to fetch CSRF token: ${response.status}`)
  }
  // Cookie is set by the backend response - no need to store locally
}

/**
 * Gets the current CSRF token from cookie.
 */
export function getCsrfToken(): string | null {
  return getCsrfTokenFromCookie()
}

/**
 * Clears the stored CSRF token.
 * Note: This doesn't clear the cookie itself, just for API consistency.
 * The cookie will be cleared by the backend on logout.
 */
export function clearCsrfToken(): void {
  // Cookie is managed by the backend - nothing to clear locally
}

/**
 * Creates headers for API requests.
 *
 * @param accessToken - The OAuth2 access token
 * @param includeCsrf - Whether to include CSRF token (for mutations)
 */
export function createApiHeaders(
  accessToken: string,
  includeCsrf: boolean = false
): HeadersInit {
  const headers: HeadersInit = {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json',
  }

  if (includeCsrf) {
    // Read fresh from cookie to handle token rotation and multi-tab scenarios
    const token = getCsrfTokenFromCookie()
    if (token) {
      headers['X-XSRF-TOKEN'] = token
    }
  }

  return headers
}

/**
 * Makes a GET request to the API.
 */
export async function apiGet<T>(
  path: string,
  accessToken: string
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'GET',
    headers: createApiHeaders(accessToken, false),
    credentials: 'include',
  })

  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`)
  }

  return response.json()
}

/**
 * Parse JSON response if body is not empty.
 * Returns undefined for 204 No Content or empty responses.
 */
async function parseJsonResponse<T>(response: Response): Promise<T | undefined> {
  // 204 No Content or empty body
  if (response.status === 204) {
    return undefined
  }

  const contentLength = response.headers.get('content-length')
  if (contentLength === '0') {
    return undefined
  }

  // Try to parse JSON, return undefined if body is empty
  const text = await response.text()
  if (!text) {
    return undefined
  }

  return JSON.parse(text) as T
}

/**
 * Makes a POST request to the API.
 */
export async function apiPost<T>(
  path: string,
  accessToken: string,
  body?: unknown
): Promise<T | undefined> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers: createApiHeaders(accessToken, true), // Include CSRF for mutations
    credentials: 'include',
    body: body ? JSON.stringify(body) : undefined,
  })

  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`)
  }

  return parseJsonResponse<T>(response)
}

/**
 * Makes a PUT request to the API.
 */
export async function apiPut<T>(
  path: string,
  accessToken: string,
  body?: unknown
): Promise<T | undefined> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'PUT',
    headers: createApiHeaders(accessToken, true), // Include CSRF for mutations
    credentials: 'include',
    body: body ? JSON.stringify(body) : undefined,
  })

  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`)
  }

  return parseJsonResponse<T>(response)
}

/**
 * Makes a DELETE request to the API.
 */
export async function apiDelete(
  path: string,
  accessToken: string
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'DELETE',
    headers: createApiHeaders(accessToken, true), // Include CSRF for mutations
    credentials: 'include',
  })

  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`)
  }
}
