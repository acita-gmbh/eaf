import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  getVmwareConfig,
  saveVmwareConfig,
  testVmwareConnection,
  checkVmwareConfigExists,
  isConnectionTestError,
  type VmwareConfig,
  type ConnectionTestResult,
  type ConnectionTestError,
} from './vmware-config'
import { ApiError } from './vm-requests'

// Mock fetch globally
const mockFetch = vi.fn()
vi.stubGlobal('fetch', mockFetch)

describe('VMware Config API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  const mockVmwareConfig: VmwareConfig = {
    id: 'config-123',
    vcenterUrl: 'https://vcenter.example.com',
    username: 'admin@vsphere.local',
    hasPassword: true,
    datacenterName: 'DC1',
    clusterName: 'Cluster1',
    datastoreName: 'Datastore1',
    networkName: 'VM Network',
    templateName: 'ubuntu-22.04-template',
    folderPath: '/VMs/DVMM',
    verifiedAt: '2024-01-15T10:00:00Z',
    createdAt: '2024-01-01T09:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
    createdBy: 'user-1',
    updatedBy: 'user-1',
    version: 1,
  }

  describe('getVmwareConfig', () => {
    it('fetches VMware configuration successfully', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '500' }),
        text: () => Promise.resolve(JSON.stringify(mockVmwareConfig)),
      })

      const result = await getVmwareConfig('test-token')

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/admin/vmware-config'),
        expect.objectContaining({
          method: 'GET',
          credentials: 'include',
        })
      )
      expect(result).toEqual(mockVmwareConfig)
    })

    it('returns null when configuration does not exist (404)', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
        statusText: 'Not Found',
        headers: new Headers({ 'content-length': '0' }),
        text: () => Promise.resolve(''),
      })

      const result = await getVmwareConfig('test-token')

      expect(result).toBeNull()
    })

    it('throws ApiError on server error (500)', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        headers: new Headers({ 'content-length': '50' }),
        text: () => Promise.resolve(JSON.stringify({ error: 'server_error' })),
      })

      await expect(getVmwareConfig('test-token')).rejects.toThrow(ApiError)
    })

    it('throws ApiError on 403 Forbidden', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 403,
        statusText: 'Forbidden',
        headers: new Headers({ 'content-length': '50' }),
        text: () => Promise.resolve(JSON.stringify({ error: 'forbidden' })),
      })

      try {
        await getVmwareConfig('test-token')
        expect.fail('Should have thrown')
      } catch (err) {
        expect(err).toBeInstanceOf(ApiError)
        expect((err as ApiError).status).toBe(403)
      }
    })

    it('handles empty text response gracefully', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '0' }),
        text: () => Promise.resolve(''),
      })

      const result = await getVmwareConfig('test-token')
      expect(result).toEqual({})
    })
  })

  describe('saveVmwareConfig', () => {
    const saveRequest = {
      vcenterUrl: 'https://vcenter.example.com',
      username: 'admin@vsphere.local',
      password: 'secret123',
      datacenterName: 'DC1',
      clusterName: 'Cluster1',
      datastoreName: 'Datastore1',
      networkName: 'VM Network',
      templateName: 'ubuntu-22.04-template',
      folderPath: '/VMs/DVMM',
      version: null,
    }

    const saveResponse = {
      id: 'config-123',
      version: 1,
      message: 'Configuration saved successfully',
    }

    it('saves VMware configuration successfully', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '100' }),
        text: () => Promise.resolve(JSON.stringify(saveResponse)),
      })

      const result = await saveVmwareConfig(saveRequest, 'test-token')

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/admin/vmware-config'),
        expect.objectContaining({
          method: 'PUT',
          credentials: 'include',
          body: JSON.stringify(saveRequest),
        })
      )
      expect(result).toEqual(saveResponse)
    })

    it('throws ApiError on 422 validation error', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 422,
        statusText: 'Unprocessable Entity',
        headers: new Headers({ 'content-length': '100' }),
        text: () =>
          Promise.resolve(
            JSON.stringify({
              error: 'validation_error',
              errors: [{ field: 'vcenterUrl', message: 'Invalid URL format' }],
            })
          ),
      })

      try {
        await saveVmwareConfig(saveRequest, 'test-token')
        expect.fail('Should have thrown')
      } catch (err) {
        expect(err).toBeInstanceOf(ApiError)
        expect((err as ApiError).status).toBe(422)
      }
    })

    it('throws ApiError on 409 conflict (concurrent modification)', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 409,
        statusText: 'Conflict',
        headers: new Headers({ 'content-length': '100' }),
        text: () =>
          Promise.resolve(
            JSON.stringify({
              error: 'conflict',
              message: 'Configuration was modified by another admin',
            })
          ),
      })

      try {
        await saveVmwareConfig({ ...saveRequest, version: 1 }, 'test-token')
        expect.fail('Should have thrown')
      } catch (err) {
        expect(err).toBeInstanceOf(ApiError)
        expect((err as ApiError).status).toBe(409)
      }
    })
  })

  describe('testVmwareConnection', () => {
    const testRequest = {
      vcenterUrl: 'https://vcenter.example.com',
      username: 'admin@vsphere.local',
      password: 'secret123',
      datacenterName: 'DC1',
      clusterName: 'Cluster1',
      datastoreName: 'Datastore1',
      networkName: 'VM Network',
    }

    const successResult: ConnectionTestResult = {
      success: true,
      vcenterVersion: '8.0.1',
      clusterName: 'Cluster1',
      clusterHosts: 3,
      datastoreFreeGb: 500,
      message: 'Successfully connected to vCenter',
    }

    const errorResult: ConnectionTestError = {
      success: false,
      error: 'CONNECTION_FAILED',
      message: 'Unable to connect to vCenter. Check URL and credentials.',
    }

    it('returns success result on successful connection test', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '200' }),
        text: () => Promise.resolve(JSON.stringify(successResult)),
      })

      const result = await testVmwareConnection(testRequest, 'test-token')

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/admin/vmware-config/test'),
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
          body: JSON.stringify(testRequest),
        })
      )
      expect(result).toEqual(successResult)
      expect(isConnectionTestError(result)).toBe(false)
    })

    it('returns error result on 422 (connection test failure)', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 422,
        statusText: 'Unprocessable Entity',
        headers: new Headers({ 'content-length': '100' }),
        text: () => Promise.resolve(JSON.stringify(errorResult)),
      })

      const result = await testVmwareConnection(testRequest, 'test-token')

      // 422 should return error result, not throw
      expect(result).toEqual(errorResult)
      expect(isConnectionTestError(result)).toBe(true)
    })

    it('throws ApiError on 500 server error', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        headers: new Headers({ 'content-length': '50' }),
        text: () => Promise.resolve(JSON.stringify({ error: 'server_error' })),
      })

      await expect(testVmwareConnection(testRequest, 'test-token')).rejects.toThrow(
        ApiError
      )
    })

    it('throws ApiError on 403 Forbidden', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 403,
        statusText: 'Forbidden',
        headers: new Headers({ 'content-length': '50' }),
        text: () => Promise.resolve(JSON.stringify({ error: 'forbidden' })),
      })

      try {
        await testVmwareConnection(testRequest, 'test-token')
        expect.fail('Should have thrown')
      } catch (err) {
        expect(err).toBeInstanceOf(ApiError)
        expect((err as ApiError).status).toBe(403)
      }
    })
  })

  describe('checkVmwareConfigExists', () => {
    it('returns exists true when config exists and is verified', async () => {
      const existsResponse = {
        exists: true,
        verifiedAt: '2024-01-15T10:00:00Z',
      }

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '50' }),
        text: () => Promise.resolve(JSON.stringify(existsResponse)),
      })

      const result = await checkVmwareConfigExists('test-token')

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/admin/vmware-config/exists'),
        expect.objectContaining({
          method: 'GET',
          credentials: 'include',
        })
      )
      expect(result).toEqual(existsResponse)
    })

    it('returns exists true with null verifiedAt when config exists but not verified', async () => {
      const existsResponse = {
        exists: true,
        verifiedAt: null,
      }

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '30' }),
        text: () => Promise.resolve(JSON.stringify(existsResponse)),
      })

      const result = await checkVmwareConfigExists('test-token')

      expect(result.exists).toBe(true)
      expect(result.verifiedAt).toBeNull()
    })

    it('returns exists false when config does not exist', async () => {
      const existsResponse = {
        exists: false,
        verifiedAt: null,
      }

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-length': '30' }),
        text: () => Promise.resolve(JSON.stringify(existsResponse)),
      })

      const result = await checkVmwareConfigExists('test-token')

      expect(result.exists).toBe(false)
      expect(result.verifiedAt).toBeNull()
    })

    it('throws ApiError on 403 Forbidden', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 403,
        statusText: 'Forbidden',
        headers: new Headers({ 'content-length': '50' }),
        text: () => Promise.resolve(JSON.stringify({ error: 'forbidden' })),
      })

      await expect(checkVmwareConfigExists('test-token')).rejects.toThrow(ApiError)
    })
  })

  describe('isConnectionTestError', () => {
    it('returns true for error result', () => {
      const errorResult: ConnectionTestError = {
        success: false,
        error: 'CONNECTION_FAILED',
        message: 'Connection failed',
      }

      expect(isConnectionTestError(errorResult)).toBe(true)
    })

    it('returns false for success result', () => {
      const successResult: ConnectionTestResult = {
        success: true,
        vcenterVersion: '8.0.1',
        clusterName: 'Cluster1',
        clusterHosts: 3,
        datastoreFreeGb: 500,
        message: 'Connected',
      }

      expect(isConnectionTestError(successResult)).toBe(false)
    })
  })
})
