import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  createVmRequest,
  getMyRequests,
  cancelRequest,
  ApiError,
  isValidationError,
  isQuotaExceededError,
  isNotFoundError,
  isForbiddenError,
  isInvalidStateError,
  type CreateVmRequestPayload,
  type ValidationErrorResponse,
  type QuotaExceededResponse,
  type NotFoundErrorResponse,
  type ForbiddenErrorResponse,
  type InvalidStateErrorResponse,
  type VmRequestSummary,
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

  describe('getMyRequests', () => {
    const accessToken = 'test-token'

    it('transforms nested size object to flat fields', async () => {
      // Backend returns nested size object
      const backendResponse = {
        items: [
          {
            id: 'req-1',
            requesterName: 'John Doe',
            projectId: 'proj-1',
            projectName: 'Test Project',
            vmName: 'test-vm',
            size: { code: 'M', cpuCores: 4, memoryGb: 16, diskGb: 100 },
            justification: 'Test reason',
            status: 'PENDING',
            createdAt: '2024-01-01T00:00:00Z',
            updatedAt: '2024-01-01T00:00:00Z',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      }

      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers(),
        text: async () => JSON.stringify(backendResponse),
      })

      const result = await getMyRequests({}, accessToken)

      // Verify size is flattened
      expect(result.items).toHaveLength(1)
      const item = result.items[0]
      expect(item.size).toBe('M')
      expect(item.cpuCores).toBe(4)
      expect(item.memoryGb).toBe(16)
      expect(item.diskGb).toBe(100)
    })

    it('sends GET request with correct query parameters', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers(),
        text: async () =>
          JSON.stringify({
            items: [],
            page: 1,
            size: 25,
            totalElements: 0,
            totalPages: 0,
            hasNext: false,
            hasPrevious: false,
          }),
      })

      await getMyRequests({ page: 1, size: 25 }, accessToken)

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/requests/my?page=1&size=25'),
        expect.objectContaining({
          method: 'GET',
          credentials: 'include',
        })
      )
    })

    it('preserves pagination metadata from backend', async () => {
      const backendResponse = {
        items: [],
        page: 2,
        size: 10,
        totalElements: 50,
        totalPages: 5,
        hasNext: true,
        hasPrevious: true,
      }

      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers(),
        text: async () => JSON.stringify(backendResponse),
      })

      const result = await getMyRequests({ page: 2, size: 10 }, accessToken)

      expect(result.page).toBe(2)
      expect(result.size).toBe(10)
      expect(result.totalElements).toBe(50)
      expect(result.totalPages).toBe(5)
      expect(result.hasNext).toBe(true)
      expect(result.hasPrevious).toBe(true)
    })

    it('throws ApiError on 401 Unauthorized', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 401,
        statusText: 'Unauthorized',
        headers: new Headers(),
        text: async () => JSON.stringify({ message: 'Token expired' }),
      })

      await expect(getMyRequests({}, accessToken)).rejects.toThrow(ApiError)

      try {
        await getMyRequests({}, accessToken)
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(401)
      }
    })

    it('transforms multiple items in response', async () => {
      const backendResponse = {
        items: [
          {
            id: 'req-1',
            requesterName: 'User 1',
            projectId: 'proj-1',
            projectName: 'Project 1',
            vmName: 'vm-1',
            size: { code: 'S', cpuCores: 2, memoryGb: 4, diskGb: 50 },
            justification: 'Reason 1',
            status: 'PENDING',
            createdAt: '2024-01-01T00:00:00Z',
            updatedAt: '2024-01-01T00:00:00Z',
          },
          {
            id: 'req-2',
            requesterName: 'User 2',
            projectId: 'proj-2',
            projectName: 'Project 2',
            vmName: 'vm-2',
            size: { code: 'XL', cpuCores: 16, memoryGb: 64, diskGb: 500 },
            justification: 'Reason 2',
            status: 'APPROVED',
            createdAt: '2024-01-02T00:00:00Z',
            updatedAt: '2024-01-02T00:00:00Z',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      }

      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers(),
        text: async () => JSON.stringify(backendResponse),
      })

      const result = await getMyRequests({}, accessToken)

      expect(result.items).toHaveLength(2)

      // First item - size S
      expect(result.items[0].size).toBe('S')
      expect(result.items[0].cpuCores).toBe(2)
      expect(result.items[0].memoryGb).toBe(4)
      expect(result.items[0].diskGb).toBe(50)

      // Second item - size XL
      expect(result.items[1].size).toBe('XL')
      expect(result.items[1].cpuCores).toBe(16)
      expect(result.items[1].memoryGb).toBe(64)
      expect(result.items[1].diskGb).toBe(500)
    })
  })

  describe('cancelRequest', () => {
    const accessToken = 'test-token'
    const requestId = 'req-123'

    it('sends POST request to cancel endpoint with reason', async () => {
      const mockResponse = {
        message: 'Request cancelled successfully',
        requestId,
      }

      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers(),
        text: async () => JSON.stringify(mockResponse),
      })

      const result = await cancelRequest(requestId, { reason: 'No longer needed' }, accessToken)

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining(`/api/requests/${requestId}/cancel`),
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
          body: JSON.stringify({ reason: 'No longer needed' }),
        })
      )
      expect(result).toEqual(mockResponse)
    })

    it('sends POST request with empty body when no reason provided', async () => {
      const mockResponse = {
        message: 'Request cancelled successfully',
        requestId,
      }

      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers(),
        text: async () => JSON.stringify(mockResponse),
      })

      await cancelRequest(requestId, undefined, accessToken)

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining(`/api/requests/${requestId}/cancel`),
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
          body: '{}', // Empty object for Content-Type consistency
        })
      )
    })

    it('throws ApiError with not found on 404', async () => {
      const notFoundError: NotFoundErrorResponse = {
        type: 'not_found',
        message: 'Request not found',
      }

      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
        headers: new Headers(),
        text: async () => JSON.stringify(notFoundError),
      })

      await expect(cancelRequest(requestId, undefined, accessToken)).rejects.toThrow(ApiError)

      try {
        await cancelRequest(requestId, undefined, accessToken)
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(404)
        expect(isNotFoundError(apiError.body)).toBe(true)
      }
    })

    it('throws ApiError with forbidden on 403', async () => {
      const forbiddenError: ForbiddenErrorResponse = {
        type: 'forbidden',
        message: 'Not authorized to cancel this request',
      }

      mockFetch.mockResolvedValue({
        ok: false,
        status: 403,
        statusText: 'Forbidden',
        headers: new Headers(),
        text: async () => JSON.stringify(forbiddenError),
      })

      await expect(cancelRequest(requestId, undefined, accessToken)).rejects.toThrow(ApiError)

      try {
        await cancelRequest(requestId, undefined, accessToken)
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(403)
        expect(isForbiddenError(apiError.body)).toBe(true)
      }
    })

    it('throws ApiError with invalid state on 409', async () => {
      const invalidStateError: InvalidStateErrorResponse = {
        type: 'invalid_state',
        message: 'Request cannot be cancelled',
        currentState: 'APPROVED',
      }

      mockFetch.mockResolvedValue({
        ok: false,
        status: 409,
        statusText: 'Conflict',
        headers: new Headers(),
        text: async () => JSON.stringify(invalidStateError),
      })

      await expect(cancelRequest(requestId, undefined, accessToken)).rejects.toThrow(ApiError)

      try {
        await cancelRequest(requestId, undefined, accessToken)
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(409)
        expect(isInvalidStateError(apiError.body)).toBe(true)
        if (isInvalidStateError(apiError.body)) {
          expect(apiError.body.currentState).toBe('APPROVED')
        }
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

      await expect(cancelRequest(requestId, undefined, accessToken)).rejects.toThrow(ApiError)

      try {
        await cancelRequest(requestId, undefined, accessToken)
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        const apiError = error as ApiError
        expect(apiError.status).toBe(500)
      }
    })
  })

  describe('isNotFoundError', () => {
    it('returns true for valid not found error', () => {
      const notFoundError: NotFoundErrorResponse = {
        type: 'not_found',
        message: 'Request not found',
      }
      expect(isNotFoundError(notFoundError)).toBe(true)
    })

    it('returns false for null', () => {
      expect(isNotFoundError(null)).toBe(false)
    })

    it('returns false for undefined', () => {
      expect(isNotFoundError(undefined)).toBe(false)
    })

    it('returns false for non-object', () => {
      expect(isNotFoundError('string')).toBe(false)
      expect(isNotFoundError(123)).toBe(false)
    })

    it('returns false for wrong type field', () => {
      expect(isNotFoundError({ type: 'validation', message: 'test' })).toBe(false)
    })
  })

  describe('isForbiddenError', () => {
    it('returns true for valid forbidden error', () => {
      const forbiddenError: ForbiddenErrorResponse = {
        type: 'forbidden',
        message: 'Not authorized',
      }
      expect(isForbiddenError(forbiddenError)).toBe(true)
    })

    it('returns false for null', () => {
      expect(isForbiddenError(null)).toBe(false)
    })

    it('returns false for undefined', () => {
      expect(isForbiddenError(undefined)).toBe(false)
    })

    it('returns false for non-object', () => {
      expect(isForbiddenError('string')).toBe(false)
      expect(isForbiddenError(123)).toBe(false)
    })

    it('returns false for wrong type field', () => {
      expect(isForbiddenError({ type: 'not_found', message: 'test' })).toBe(false)
    })
  })

  describe('isInvalidStateError', () => {
    it('returns true for valid invalid state error', () => {
      const invalidStateError: InvalidStateErrorResponse = {
        type: 'invalid_state',
        message: 'Cannot cancel',
        currentState: 'APPROVED',
      }
      expect(isInvalidStateError(invalidStateError)).toBe(true)
    })

    it('returns false for null', () => {
      expect(isInvalidStateError(null)).toBe(false)
    })

    it('returns false for undefined', () => {
      expect(isInvalidStateError(undefined)).toBe(false)
    })

    it('returns false for non-object', () => {
      expect(isInvalidStateError('string')).toBe(false)
      expect(isInvalidStateError(123)).toBe(false)
    })

    it('returns false for wrong type field', () => {
      expect(isInvalidStateError({ type: 'forbidden', message: 'test', currentState: 'X' })).toBe(
        false
      )
    })
  })
})
