import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { getPendingRequests, getProjects, getAdminRequestDetail } from './admin'
import { ApiError } from './vm-requests'

// Mock fetch globally
const mockFetch = vi.fn()
vi.stubGlobal('fetch', mockFetch)

describe('Admin API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('getPendingRequests', () => {
    const mockBackendResponse = {
      items: [
        {
          id: '1',
          requesterName: 'John Doe',
          vmName: 'web-server-01',
          projectName: 'Project Alpha',
          size: {
            code: 'M',
            cpuCores: 4,
            memoryGb: 16,
            diskGb: 100,
          },
          createdAt: '2024-01-01T10:00:00Z',
        },
      ],
      page: 0,
      size: 25,
      totalElements: 1,
      totalPages: 1,
    }

    it('fetches pending requests with default params', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '100' }),
        text: () => Promise.resolve(JSON.stringify(mockBackendResponse)),
      })

      const result = await getPendingRequests({}, 'test-token')

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/admin/requests/pending'),
        expect.objectContaining({
          method: 'GET',
          credentials: 'include',
        })
      )
      expect(result.items[0]).toEqual({
        id: '1',
        requesterName: 'John Doe',
        vmName: 'web-server-01',
        projectName: 'Project Alpha',
        size: 'M',
        cpuCores: 4,
        memoryGb: 16,
        diskGb: 100,
        createdAt: '2024-01-01T10:00:00Z',
      })
    })

    it('includes query params when provided', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '100' }),
        text: () => Promise.resolve(JSON.stringify(mockBackendResponse)),
      })

      await getPendingRequests(
        { projectId: 'proj-123', page: 2, size: 50 },
        'test-token'
      )

      const calledUrl = mockFetch.mock.calls[0][0] as string
      expect(calledUrl).toContain('projectId=proj-123')
      expect(calledUrl).toContain('page=2')
      expect(calledUrl).toContain('size=50')
    })

    it('throws ApiError on non-OK response', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 403,
        statusText: 'Forbidden',
        headers: new Headers({ 'content-length': '50' }),
        text: () => Promise.resolve(JSON.stringify({ error: 'forbidden' })),
      })

      await expect(getPendingRequests({}, 'test-token')).rejects.toThrow(ApiError)
    })

    it('handles empty response body', async () => {
      const emptyResponse = { items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 }
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '10' }),
        text: () => Promise.resolve(JSON.stringify(emptyResponse)),
      })

      const result = await getPendingRequests({}, 'test-token')
      expect(result.items).toEqual([])
    })

    it('handles response with no projectId filter', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '100' }),
        text: () => Promise.resolve(JSON.stringify(mockBackendResponse)),
      })

      await getPendingRequests({ page: 0, size: 10 }, 'test-token')

      const calledUrl = mockFetch.mock.calls[0][0] as string
      expect(calledUrl).not.toContain('projectId')
      expect(calledUrl).toContain('page=0')
      expect(calledUrl).toContain('size=10')
    })
  })

  describe('getProjects', () => {
    const mockProjects = [
      { id: 'proj-1', name: 'Project Alpha' },
      { id: 'proj-2', name: 'Project Beta' },
    ]

    it('fetches projects successfully', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '100' }),
        text: () => Promise.resolve(JSON.stringify(mockProjects)),
      })

      const result = await getProjects('test-token')

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/admin/projects'),
        expect.objectContaining({
          method: 'GET',
          credentials: 'include',
        })
      )
      expect(result).toEqual(mockProjects)
    })

    it('throws ApiError on non-OK response', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 403,
        statusText: 'Forbidden',
        headers: new Headers({ 'content-length': '50' }),
        text: () => Promise.resolve(JSON.stringify({ error: 'forbidden' })),
      })

      await expect(getProjects('test-token')).rejects.toThrow(ApiError)
    })

    it('handles empty text response', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '0' }),
        text: () => Promise.resolve(''),
      })

      const result = await getProjects('test-token')
      expect(result).toEqual({})
    })

    it('handles non-JSON text response', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '20' }),
        text: () => Promise.resolve('plain text response'),
      })

      const result = await getProjects('test-token')
      expect(result).toEqual({ message: 'plain text response' })
    })
  })

  describe('getAdminRequestDetail', () => {
    const mockBackendDetail = {
      id: 'req-1',
      vmName: 'web-server-01',
      size: {
        code: 'M',
        cpuCores: 4,
        memoryGb: 16,
        diskGb: 100,
      },
      justification: 'Development server',
      status: 'PENDING',
      projectName: 'Project Alpha',
      requester: {
        id: 'user-1',
        name: 'John Doe',
        email: 'john@example.com',
        role: 'Developer',
      },
      timeline: [
        {
          eventType: 'CREATED',
          actorName: 'John Doe',
          details: null,
          occurredAt: '2024-01-01T10:00:00Z',
        },
      ],
      requesterHistory: [
        {
          id: 'req-0',
          vmName: 'old-server',
          status: 'APPROVED',
          createdAt: '2023-12-01T10:00:00Z',
        },
      ],
      createdAt: '2024-01-01T10:00:00Z',
    }

    it('fetches admin request detail successfully', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '500' }),
        text: () => Promise.resolve(JSON.stringify(mockBackendDetail)),
      })

      const result = await getAdminRequestDetail('req-1', 'test-token')

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/admin/requests/req-1'),
        expect.objectContaining({
          method: 'GET',
          credentials: 'include',
        })
      )
      expect(result).toEqual({
        id: 'req-1',
        vmName: 'web-server-01',
        size: 'M',
        cpuCores: 4,
        memoryGb: 16,
        diskGb: 100,
        justification: 'Development server',
        status: 'PENDING',
        projectName: 'Project Alpha',
        requester: {
          id: 'user-1',
          name: 'John Doe',
          email: 'john@example.com',
          role: 'Developer',
        },
        timeline: mockBackendDetail.timeline,
        requesterHistory: mockBackendDetail.requesterHistory,
        createdAt: '2024-01-01T10:00:00Z',
      })
    })

    it('throws ApiError on 404 Not Found', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
        statusText: 'Not Found',
        headers: new Headers({ 'content-length': '50' }),
        text: () => Promise.resolve(JSON.stringify({ error: 'not_found' })),
      })

      await expect(getAdminRequestDetail('invalid-id', 'test-token')).rejects.toThrow(
        ApiError
      )
    })

    it('throws ApiError on 500 Internal Error', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        headers: new Headers({ 'content-length': '50' }),
        text: () => Promise.resolve(JSON.stringify({ error: 'server_error' })),
      })

      try {
        await getAdminRequestDetail('req-1', 'test-token')
        expect.fail('Should have thrown')
      } catch (err) {
        expect(err).toBeInstanceOf(ApiError)
        expect((err as ApiError).status).toBe(500)
      }
    })
  })
})
