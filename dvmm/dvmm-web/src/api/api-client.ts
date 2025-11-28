/**
 * API client for DVMM backend.
 *
 * Handles:
 * - CSRF token management (X-XSRF-TOKEN header)
 * - Bearer token authentication
 * - Base URL configuration
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

let csrfToken: string | null = null

/**
 * Fetches CSRF token from the backend and stores it.
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

  // Read the XSRF-TOKEN cookie (set by the backend)
  // Note: This only works if the cookie is NOT httpOnly
  const cookies = document.cookie.split(';')
  for (const cookie of cookies) {
    const [name, value] = cookie.trim().split('=')
    if (name === 'XSRF-TOKEN') {
      csrfToken = value
      break
    }
  }
}

/**
 * Gets the current CSRF token.
 */
export function getCsrfToken(): string | null {
  return csrfToken
}

/**
 * Clears the stored CSRF token.
 * Should be called on logout.
 */
export function clearCsrfToken(): void {
  csrfToken = null
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

  if (includeCsrf && csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken
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
 * Makes a POST request to the API.
 */
export async function apiPost<T>(
  path: string,
  accessToken: string,
  body?: unknown
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers: createApiHeaders(accessToken, true), // Include CSRF for mutations
    credentials: 'include',
    body: body ? JSON.stringify(body) : undefined,
  })

  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`)
  }

  return response.json()
}

/**
 * Makes a PUT request to the API.
 */
export async function apiPut<T>(
  path: string,
  accessToken: string,
  body?: unknown
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'PUT',
    headers: createApiHeaders(accessToken, true), // Include CSRF for mutations
    credentials: 'include',
    body: body ? JSON.stringify(body) : undefined,
  })

  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`)
  }

  return response.json()
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
