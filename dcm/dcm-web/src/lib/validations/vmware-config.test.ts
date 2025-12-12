import { describe, it, expect } from 'vitest'
import {
  vcenterUrlSchema,
  usernameSchema,
  passwordSchema,
  requiredPasswordSchema,
  datacenterNameSchema,
  clusterNameSchema,
  datastoreNameSchema,
  networkNameSchema,
  templateNameSchema,
  folderPathSchema,
  vmwareConfigFormSchema,
  connectionTestSchema,
} from './vmware-config'

describe('VMware Config Validations', () => {
  describe('vcenterUrlSchema', () => {
    it('accepts valid HTTPS URL', () => {
      const result = vcenterUrlSchema.safeParse('https://vcenter.example.com/sdk')
      expect(result.success).toBe(true)
    })

    it('rejects empty string', () => {
      const result = vcenterUrlSchema.safeParse('')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('vCenter URL is required')
      }
    })

    it('rejects HTTP URL (requires HTTPS)', () => {
      const result = vcenterUrlSchema.safeParse('http://vcenter.example.com')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('vCenter URL must start with https://')
      }
    })

    it('rejects URL exceeding 500 characters', () => {
      const longUrl = 'https://vcenter.' + 'a'.repeat(500) + '.com'
      const result = vcenterUrlSchema.safeParse(longUrl)
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Maximum 500 characters allowed')
      }
    })

    it('rejects non-URL string not starting with https://', () => {
      const result = vcenterUrlSchema.safeParse('vcenter.example.com')
      expect(result.success).toBe(false)
    })
  })

  describe('usernameSchema', () => {
    it('accepts valid username', () => {
      const result = usernameSchema.safeParse('administrator@vsphere.local')
      expect(result.success).toBe(true)
    })

    it('rejects empty string', () => {
      const result = usernameSchema.safeParse('')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Username is required')
      }
    })

    it('rejects username exceeding 255 characters', () => {
      const result = usernameSchema.safeParse('a'.repeat(256))
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Maximum 255 characters allowed')
      }
    })
  })

  describe('passwordSchema', () => {
    it('accepts valid password', () => {
      const result = passwordSchema.safeParse('secret123')
      expect(result.success).toBe(true)
    })

    it('accepts empty string (optional)', () => {
      const result = passwordSchema.safeParse('')
      expect(result.success).toBe(true)
    })

    it('accepts undefined (optional)', () => {
      const result = passwordSchema.safeParse(undefined)
      expect(result.success).toBe(true)
    })

    it('rejects password exceeding 500 characters', () => {
      const result = passwordSchema.safeParse('a'.repeat(501))
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Maximum 500 characters allowed')
      }
    })
  })

  describe('requiredPasswordSchema', () => {
    it('accepts valid password', () => {
      const result = requiredPasswordSchema.safeParse('secret123')
      expect(result.success).toBe(true)
    })

    it('rejects empty string', () => {
      const result = requiredPasswordSchema.safeParse('')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Password is required for connection test')
      }
    })

    it('rejects password exceeding 500 characters', () => {
      const result = requiredPasswordSchema.safeParse('a'.repeat(501))
      expect(result.success).toBe(false)
    })
  })

  describe('datacenterNameSchema', () => {
    it('accepts valid datacenter name', () => {
      const result = datacenterNameSchema.safeParse('Datacenter1')
      expect(result.success).toBe(true)
    })

    it('rejects empty string', () => {
      const result = datacenterNameSchema.safeParse('')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Datacenter name is required')
      }
    })

    it('rejects name exceeding 255 characters', () => {
      const result = datacenterNameSchema.safeParse('a'.repeat(256))
      expect(result.success).toBe(false)
    })
  })

  describe('clusterNameSchema', () => {
    it('accepts valid cluster name', () => {
      const result = clusterNameSchema.safeParse('Cluster1')
      expect(result.success).toBe(true)
    })

    it('rejects empty string', () => {
      const result = clusterNameSchema.safeParse('')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Cluster name is required')
      }
    })
  })

  describe('datastoreNameSchema', () => {
    it('accepts valid datastore name', () => {
      const result = datastoreNameSchema.safeParse('datastore1')
      expect(result.success).toBe(true)
    })

    it('rejects empty string', () => {
      const result = datastoreNameSchema.safeParse('')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Datastore name is required')
      }
    })
  })

  describe('networkNameSchema', () => {
    it('accepts valid network name', () => {
      const result = networkNameSchema.safeParse('VM Network')
      expect(result.success).toBe(true)
    })

    it('rejects empty string', () => {
      const result = networkNameSchema.safeParse('')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Network name is required')
      }
    })
  })

  describe('templateNameSchema', () => {
    it('accepts valid template name', () => {
      const result = templateNameSchema.safeParse('ubuntu-22.04-template')
      expect(result.success).toBe(true)
    })

    it('accepts empty string (optional)', () => {
      const result = templateNameSchema.safeParse('')
      expect(result.success).toBe(true)
    })

    it('accepts undefined (optional)', () => {
      const result = templateNameSchema.safeParse(undefined)
      expect(result.success).toBe(true)
    })

    it('rejects name exceeding 255 characters', () => {
      const result = templateNameSchema.safeParse('a'.repeat(256))
      expect(result.success).toBe(false)
    })
  })

  describe('folderPathSchema', () => {
    it('accepts valid folder path', () => {
      const result = folderPathSchema.safeParse('/VMs/DCM/Production')
      expect(result.success).toBe(true)
    })

    it('accepts empty string (optional)', () => {
      const result = folderPathSchema.safeParse('')
      expect(result.success).toBe(true)
    })

    it('accepts undefined (optional)', () => {
      const result = folderPathSchema.safeParse(undefined)
      expect(result.success).toBe(true)
    })

    it('rejects path exceeding 500 characters', () => {
      const result = folderPathSchema.safeParse('/'.repeat(501))
      expect(result.success).toBe(false)
    })
  })

  describe('vmwareConfigFormSchema', () => {
    const validConfig = {
      vcenterUrl: 'https://vcenter.example.com',
      username: 'administrator@vsphere.local',
      password: 'secret123',
      datacenterName: 'Datacenter1',
      clusterName: 'Cluster1',
      datastoreName: 'datastore1',
      networkName: 'VM Network',
      templateName: 'ubuntu-22.04-template',
      folderPath: '/VMs/DCM',
    }

    it('accepts valid complete configuration', () => {
      const result = vmwareConfigFormSchema.safeParse(validConfig)
      expect(result.success).toBe(true)
    })

    it('accepts configuration with optional fields omitted', () => {
      const minimalConfig = {
        vcenterUrl: 'https://vcenter.example.com',
        username: 'administrator@vsphere.local',
        datacenterName: 'Datacenter1',
        clusterName: 'Cluster1',
        datastoreName: 'datastore1',
        networkName: 'VM Network',
      }

      const result = vmwareConfigFormSchema.safeParse(minimalConfig)
      expect(result.success).toBe(true)
    })

    it('rejects configuration with missing required fields', () => {
      const invalidConfig = {
        vcenterUrl: 'https://vcenter.example.com',
        username: 'administrator@vsphere.local',
        // Missing datacenterName, clusterName, etc.
      }

      const result = vmwareConfigFormSchema.safeParse(invalidConfig)
      expect(result.success).toBe(false)
    })

    it('rejects configuration with invalid vCenter URL', () => {
      const invalidConfig = {
        ...validConfig,
        vcenterUrl: 'http://vcenter.example.com', // HTTP instead of HTTPS
      }

      const result = vmwareConfigFormSchema.safeParse(invalidConfig)
      expect(result.success).toBe(false)
    })
  })

  describe('connectionTestSchema', () => {
    const validTestConfig = {
      vcenterUrl: 'https://vcenter.example.com',
      username: 'administrator@vsphere.local',
      password: 'secret123', // Required for connection test
      datacenterName: 'Datacenter1',
      clusterName: 'Cluster1',
      datastoreName: 'datastore1',
      networkName: 'VM Network',
    }

    it('accepts valid connection test configuration', () => {
      const result = connectionTestSchema.safeParse(validTestConfig)
      expect(result.success).toBe(true)
    })

    it('rejects configuration with missing password (required for test)', () => {
      const invalidConfig = {
        vcenterUrl: 'https://vcenter.example.com',
        username: 'administrator@vsphere.local',
        password: '', // Empty password not allowed for connection test
        datacenterName: 'Datacenter1',
        clusterName: 'Cluster1',
        datastoreName: 'datastore1',
        networkName: 'VM Network',
      }

      const result = connectionTestSchema.safeParse(invalidConfig)
      expect(result.success).toBe(false)
    })

    it('accepts configuration with optional templateName', () => {
      const configWithTemplate = {
        ...validTestConfig,
        templateName: 'ubuntu-22.04-template',
      }

      const result = connectionTestSchema.safeParse(configWithTemplate)
      expect(result.success).toBe(true)
    })
  })
})
