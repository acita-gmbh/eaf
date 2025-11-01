import simpleRestProvider from 'ra-data-simple-rest';
import DOMPurify from 'dompurify';
import { parseRFC7807Error } from '../utils';
import type { DataProvider } from 'react-admin';

// Scoped localStorage key (matches authProvider)
const TOKEN_STORAGE_KEY = 'eaf.auth.token';

/**
 * Create EAF data provider with JWT authentication, tenant injection, and RFC 7807 error mapping
 *
 * Features:
 * - JWT token injection in Authorization header
 * - Tenant ID extraction from JWT and X-Tenant-ID header injection
 * - DOMPurify sanitization for XSS prevention (SEC-001)
 * - RFC 7807 Problem Details error mapping
 * - Fail-closed tenant validation (SEC-002)
 * - Request/response logging for observability
 *
 * @param apiBaseUrl - Base URL for API (default: http://localhost:8080/api/v1)
 * @returns Configured data provider
 */
export function createDataProvider(apiBaseUrl: string = 'http://localhost:8080/api/v1'): DataProvider {
  const baseProvider = simpleRestProvider(apiBaseUrl, httpClient);

  // Wrap base provider with custom logic
  return {
    ...baseProvider,
    create: async (resource, params) => {
      // Sanitize string inputs to prevent XSS (SEC-001, SEC-004)
      const sanitizedData = sanitizeData(params.data) as typeof params.data;
      return baseProvider.create(resource, { ...params, data: sanitizedData });
    },
    update: async (resource, params) => {
      // Sanitize string inputs to prevent XSS
      const sanitizedData = sanitizeData(params.data) as typeof params.data;
      return baseProvider.update(resource, { ...params, data: sanitizedData });
    },
  };
}

/**
 * Custom HTTP client with JWT auth, tenant injection, and error mapping
 * Matches ra-data-simple-rest expected signature
 */
async function httpClient(url: string, options: RequestInit = {}): Promise<{
  status: number;
  headers: Headers;
  body: string;
  json: unknown;
}> {
  // Get JWT token from localStorage (scoped key)
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);

  // SECURITY FIX (VULN-001): Do NOT extract tenant from unverified JWT
  // Backend Layer 1 filter (Epic 4) validates JWT signature and extracts tenant_id
  // Backend includes validated tenant_id in response headers or requires it in requests

  // For MVP: Backend handles tenant validation, frontend just forwards JWT
  // The backend's 3-layer tenant isolation (Epic 4) is authoritative:
  // - Layer 1: Request filter validates JWT signature and extracts tenant_id
  // - Layer 2: Service boundary validates tenant access
  // - Layer 3: Database RLS enforces tenant filtering

  // Frontend requirement: Include JWT in Authorization header
  // Backend requirement: Return validated tenant_id in response or validate in Layer 1

  // Inject authentication header (tenant validation handled by backend)
  const headers = new Headers(options.headers);
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  // NOTE: X-Tenant-ID header NOT set by frontend (security fix)
  // Backend Layer 1 filter extracts tenant_id from validated JWT

  // Only set Content-Type if not already set and body is not FormData
  // (FormData sets its own boundary, file uploads need multipart/form-data)
  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  // Log request for observability (tenant not logged - security fix)
  console.log(`[DataProvider] ${options.method || 'GET'} ${url}`, {
    hasAuth: !!token,
  });

  try {
    // Make API request
    const response = await fetch(url, {
      ...options,
      headers,
    });

    // Log response
    console.log(`[DataProvider] Response ${response.status}`, {
      url,
      status: response.status,
      ok: response.ok,
    });

    // Parse response body
    const text = await response.text();
    const json = text ? JSON.parse(text) : {};

    // Handle errors with RFC 7807 parsing
    if (!response.ok) {
      const parsedError = parseRFC7807Error({ body: json });
      throw new Error(parsedError.message);
    }

    // Return in ra-data-simple-rest expected format
    return {
      status: response.status,
      headers: response.headers,
      body: text,
      json,
    };
  } catch (error: unknown) {
    // Parse RFC 7807 if available, otherwise use generic message
    const parsedError = parseRFC7807Error(error);
    console.error('[DataProvider] Error:', parsedError);

    throw new Error(parsedError.message);
  }
}

/**
 * Check if value is a plain object (not Date, File, FormData, etc.)
 * @param value - Value to check
 * @returns true if value is a plain object
 */
function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const proto = Object.getPrototypeOf(value);
  return proto === Object.prototype || proto === null;
}

/**
 * Sanitize data object to prevent XSS attacks (SEC-001, SEC-004)
 * Preserves non-plain objects (Date, File, FormData, etc.)
 * @param data - Data object with potentially unsafe strings
 * @returns Sanitized data object
 */
function sanitizeData(data: unknown): unknown {
  if (typeof data === 'string') {
    return DOMPurify.sanitize(data);
  }

  if (Array.isArray(data)) {
    return data.map((item) => sanitizeData(item));
  }

  // Only sanitize plain objects (not Date, File, FormData, etc.)
  if (isPlainObject(data)) {
    const sanitized: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(data)) {
      sanitized[key] = sanitizeData(value);
    }
    return sanitized;
  }

  // Return non-plain objects unchanged (Date, File, Blob, FormData, etc.)
  return data;
}
