import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  createVmRequest,
  ApiError,
  isValidationError,
  isQuotaExceededError,
  type CreateVmRequestPayload,
  type ValidationErrorResponse,
  type QuotaExceededResponse,
} from './vm-requests'

// Mock fetch globally
const mockFetch = vi.fn()
vi.stubGlobal('fetch', mockFetch)

// Mock createApiHeaders
vi.mock('./api-client', () => ({
  createApiHeaders: vi.fn(() => ({
    'Content-Type': 'application/json',
    Authorization: 'Bearer test-token',
  })),
}))

describe('vm-requests API', () => {
  const validPayload: CreateVmRequestPayload = {
    vmName: 'test-vm',
    projectId: '123e4567-e89b-12d3-a456-426614174000',
    size: 'M',
    justification: 'This is a test justification for the VM request.',
  }

  const accessToken = 'test-token'

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('createVmRequest', () => {
    it('sends POST request with correct payload and headers', async () => {
      const mockResponse = {
        id: 'new-request-id',
        vmName: 'test-vm',
        projectId: validPayload.projectId,
        projectName: null,
        size: { code: 'M', cpuCores: 4, memoryGb: 16, diskGb: 100 },
        status: 'PENDING',
        createdAt: '2024-01-01T00:00:00Z',
      }

      mockFetch.mockResolvedValue({
        ok: true,
        status: 201,
        statusText: 'Created',
        headers: new Headers(),
        text: async () => JSON.stringify(mockResponse),
      })

      const result = await createVmRequest(validPayload, accessToken)

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/requests'),
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
          body: JSON.stringify(validPayload),
        })
      )
      expect(result).toEqual(mockResponse)
    })

    it('extracts ID from Location header when body has no ID', async () => {
      const responseWithoutId = {
        vmName: 'test-vm',
        projectId: validPayload.projectId,
        projectName: null,
        size: { code: 'M', cpuCores: 4, memoryGb: 16, diskGb: 100 },
        status: 'PENDING',
        createdAt: '2024-01-01T00:00:00Z',
      }

      mockFetch.mockResolvedValue({
        ok: true,
        status: 201,
        statusText: 'Created',
        headers: new Headers({
          Location: '/api/requests/extracted-id-123',
        }),
        text: async () => JSON.stringify(responseWithoutId),
      })

      const result = await createVmRequest(validPayload, accessToken)

      expect(result.id).toBe('extracted-id-123')
    })

    it('throws ApiError with validation errors on 400', async () => {
      const validationError: ValidationErrorResponse = {
        type: 'validation',
        errors: [
          { field: 'vmName', message: 'VM name must be between 3 and 63 characters' },
        ],
      }

      mockFetch.mockResolvedValue({
        ok: false,
        status: 400,
        statusText: 'Bad Request',
        headers: new Headers(),
        text: async () => JSON.stringify(validationError),
      })

      await expect(createVmRequest(validPayload, accessToken)).rejects.toThrow(
        ApiError
      )

      try {
        await createVmRequest(validPayload, accessToken)
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(400)
        expect(apiError.statusText).toBe('Bad Request')
        expect(isValidationError(apiError.body)).toBe(true)
      }
    })

    it('throws ApiError with quota exceeded on 409', async () => {
      const quotaError: QuotaExceededResponse = {
        type: 'quota_exceeded',
        message: 'Resource quota exceeded',
        available: 2,
        requested: 4,
      }

      mockFetch.mockResolvedValue({
        ok: false,
        status: 409,
        statusText: 'Conflict',
        headers: new Headers(),
        text: async () => JSON.stringify(quotaError),
      })

      await expect(createVmRequest(validPayload, accessToken)).rejects.toThrow(
        ApiError
      )

      try {
        await createVmRequest(validPayload, accessToken)
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(409)
        expect(isQuotaExceededError(apiError.body)).toBe(true)
      }
    })

    it('throws ApiError on 401 Unauthorized', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 401,
        statusText: 'Unauthorized',
        headers: new Headers(),
        text: async () => JSON.stringify({ message: 'Token expired' }),
      })

      await expect(createVmRequest(validPayload, accessToken)).rejects.toThrow(
        ApiError
      )

      try {
        await createVmRequest(validPayload, accessToken)
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(401)
      }
    })

    it('throws ApiError on 500 Internal Server Error', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        headers: new Headers(),
        text: async () => JSON.stringify({ message: 'Something went wrong' }),
      })

      await expect(createVmRequest(validPayload, accessToken)).rejects.toThrow(
        ApiError
      )

      try {
        await createVmRequest(validPayload, accessToken)
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(500)
      }
    })

    it('handles empty response body (204)', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 204,
        statusText: 'No Content',
        headers: new Headers({
          'content-length': '0',
          Location: '/api/requests/new-id',
        }),
        text: async () => '',
      })

      const result = await createVmRequest(validPayload, accessToken)

      expect(result.id).toBe('new-id')
    })

    it('handles non-JSON response body gracefully', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        headers: new Headers(),
        text: async () => 'Plain text error message',
      })

      try {
        await createVmRequest(validPayload, accessToken)
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.body).toEqual({ message: 'Plain text error message' })
      }
    })

    it('handles network errors', async () => {
      mockFetch.mockRejectedValue(new Error('Network error'))

      await expect(createVmRequest(validPayload, accessToken)).rejects.toThrow(
        'Network error'
      )
    })
  })

  describe('isValidationError', () => {
    it('returns true for valid validation error', () => {
      const validError: ValidationErrorResponse = {
        type: 'validation',
        errors: [{ field: 'vmName', message: 'Invalid' }],
      }
      expect(isValidationError(validError)).toBe(true)
    })

    it('returns false for null', () => {
      expect(isValidationError(null)).toBe(false)
    })

    it('returns false for undefined', () => {
      expect(isValidationError(undefined)).toBe(false)
    })

    it('returns false for non-object', () => {
      expect(isValidationError('string')).toBe(false)
      expect(isValidationError(123)).toBe(false)
    })

    it('returns false for wrong type field', () => {
      expect(isValidationError({ type: 'other', errors: [] })).toBe(false)
    })

    it('returns false for missing errors field', () => {
      expect(isValidationError({ type: 'validation' })).toBe(false)
    })

    it('returns false for non-array errors', () => {
      expect(isValidationError({ type: 'validation', errors: 'not-array' })).toBe(
        false
      )
    })
  })

  describe('isQuotaExceededError', () => {
    it('returns true for valid quota exceeded error', () => {
      const quotaError: QuotaExceededResponse = {
        type: 'quota_exceeded',
        message: 'Quota exceeded',
        available: 2,
        requested: 4,
      }
      expect(isQuotaExceededError(quotaError)).toBe(true)
    })

    it('returns false for null', () => {
      expect(isQuotaExceededError(null)).toBe(false)
    })

    it('returns false for undefined', () => {
      expect(isQuotaExceededError(undefined)).toBe(false)
    })

    it('returns false for non-object', () => {
      expect(isQuotaExceededError('string')).toBe(false)
    })

    it('returns false for wrong type field', () => {
      expect(
        isQuotaExceededError({ type: 'validation', message: '', available: 0, requested: 0 })
      ).toBe(false)
    })
  })

  describe('ApiError', () => {
    it('creates error with correct properties', () => {
      const error = new ApiError(400, 'Bad Request', { message: 'Test' })

      expect(error).toBeInstanceOf(Error)
      expect(error.name).toBe('ApiError')
      expect(error.status).toBe(400)
      expect(error.statusText).toBe('Bad Request')
      expect(error.body).toEqual({ message: 'Test' })
      expect(error.message).toBe('API Error: 400 Bad Request')
    })
  })
})
