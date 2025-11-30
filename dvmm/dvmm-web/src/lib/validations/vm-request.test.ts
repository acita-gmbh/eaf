import { describe, it, expect } from 'vitest'
import {
  vmNameSchema,
  justificationSchema,
  projectIdSchema,
  vmSizeSchema,
  vmRequestFormSchema,
} from './vm-request'

describe('vmNameSchema', () => {
  describe('length validation', () => {
    it('rejects empty string', () => {
      const result = vmNameSchema.safeParse('')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Minimum 3 characters required')
      }
    })

    it('rejects strings shorter than 3 characters', () => {
      const result = vmNameSchema.safeParse('ab')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Minimum 3 characters required')
      }
    })

    it('rejects strings longer than 63 characters', () => {
      const result = vmNameSchema.safeParse('a'.repeat(64))
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Maximum 63 characters allowed')
      }
    })

    it('accepts exactly 3 characters', () => {
      const result = vmNameSchema.safeParse('abc')
      expect(result.success).toBe(true)
    })

    it('accepts exactly 63 characters', () => {
      const result = vmNameSchema.safeParse('a'.repeat(63))
      expect(result.success).toBe(true)
    })
  })

  describe('character validation', () => {
    it('rejects uppercase letters', () => {
      const result = vmNameSchema.safeParse('Web-Server')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues.some(i => i.message === 'Only lowercase letters allowed')).toBe(true)
      }
    })

    it('rejects spaces', () => {
      const result = vmNameSchema.safeParse('web server')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues.some(i => i.message === 'Only letters, numbers, and hyphens allowed')).toBe(true)
      }
    })

    it('rejects special characters like underscores', () => {
      const result = vmNameSchema.safeParse('web_server_01')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues.some(i => i.message === 'Only letters, numbers, and hyphens allowed')).toBe(true)
      }
    })

    it('rejects dots', () => {
      const result = vmNameSchema.safeParse('web.server.01')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues.some(i => i.message === 'Only letters, numbers, and hyphens allowed')).toBe(true)
      }
    })
  })

  describe('start/end character validation', () => {
    it('rejects strings starting with hyphen', () => {
      const result = vmNameSchema.safeParse('-web-server')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues.some(i => i.message === 'Must start with a letter or number')).toBe(true)
      }
    })

    it('rejects strings ending with hyphen', () => {
      const result = vmNameSchema.safeParse('web-server-')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues.some(i => i.message === 'Must end with a letter or number')).toBe(true)
      }
    })

    it('rejects strings with hyphens at both ends', () => {
      const result = vmNameSchema.safeParse('-web-server-')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues.some(i => i.message === 'Must start with a letter or number')).toBe(true)
        expect(result.error.issues.some(i => i.message === 'Must end with a letter or number')).toBe(true)
      }
    })
  })

  describe('valid names', () => {
    it('accepts "web-server-01"', () => {
      const result = vmNameSchema.safeParse('web-server-01')
      expect(result.success).toBe(true)
    })

    it('accepts "db1"', () => {
      const result = vmNameSchema.safeParse('db1')
      expect(result.success).toBe(true)
    })

    it('accepts "my-app-123"', () => {
      const result = vmNameSchema.safeParse('my-app-123')
      expect(result.success).toBe(true)
    })

    it('accepts all lowercase letters', () => {
      const result = vmNameSchema.safeParse('webserver')
      expect(result.success).toBe(true)
    })

    it('accepts all numbers', () => {
      const result = vmNameSchema.safeParse('12345')
      expect(result.success).toBe(true)
    })

    it('accepts starting with number', () => {
      const result = vmNameSchema.safeParse('1web')
      expect(result.success).toBe(true)
    })

    it('accepts hyphens in the middle', () => {
      const result = vmNameSchema.safeParse('a-b-c-d')
      expect(result.success).toBe(true)
    })
  })
})

describe('justificationSchema', () => {
  it('rejects empty string', () => {
    const result = justificationSchema.safeParse('')
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues[0].message).toBe('Minimum 10 characters required')
    }
  })

  it('rejects strings shorter than 10 characters', () => {
    const result = justificationSchema.safeParse('Too short')
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues[0].message).toBe('Minimum 10 characters required')
    }
  })

  it('accepts exactly 10 characters', () => {
    const result = justificationSchema.safeParse('0123456789')
    expect(result.success).toBe(true)
  })

  it('accepts strings longer than 10 characters', () => {
    const result = justificationSchema.safeParse('This is a valid justification for the VM request.')
    expect(result.success).toBe(true)
  })

  it('rejects strings longer than 1000 characters', () => {
    const result = justificationSchema.safeParse('a'.repeat(1001))
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues[0].message).toBe('Maximum 1000 characters allowed')
    }
  })

  it('accepts exactly 1000 characters', () => {
    const result = justificationSchema.safeParse('a'.repeat(1000))
    expect(result.success).toBe(true)
  })
})

describe('projectIdSchema', () => {
  it('rejects empty string', () => {
    const result = projectIdSchema.safeParse('')
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues[0].message).toBe('Project is required')
    }
  })

  it('accepts any non-empty string', () => {
    const result = projectIdSchema.safeParse('proj-1')
    expect(result.success).toBe(true)
  })

  it('accepts project IDs with special characters', () => {
    const result = projectIdSchema.safeParse('proj-123-abc')
    expect(result.success).toBe(true)
  })
})

describe('vmSizeSchema', () => {
  describe('valid sizes', () => {
    it('accepts "S" (Small)', () => {
      const result = vmSizeSchema.safeParse('S')
      expect(result.success).toBe(true)
    })

    it('accepts "M" (Medium)', () => {
      const result = vmSizeSchema.safeParse('M')
      expect(result.success).toBe(true)
    })

    it('accepts "L" (Large)', () => {
      const result = vmSizeSchema.safeParse('L')
      expect(result.success).toBe(true)
    })

    it('accepts "XL" (Extra Large)', () => {
      const result = vmSizeSchema.safeParse('XL')
      expect(result.success).toBe(true)
    })
  })

  describe('invalid sizes', () => {
    it('rejects undefined', () => {
      const result = vmSizeSchema.safeParse(undefined)
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Please select a VM size')
      }
    })

    it('rejects empty string', () => {
      const result = vmSizeSchema.safeParse('')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Please select a VM size')
      }
    })

    it('rejects "XXL" (not a valid size)', () => {
      const result = vmSizeSchema.safeParse('XXL')
      expect(result.success).toBe(false)
      if (!result.success) {
        expect(result.error.issues[0].message).toBe('Please select a VM size')
      }
    })

    it('rejects "small" (lowercase)', () => {
      const result = vmSizeSchema.safeParse('small')
      expect(result.success).toBe(false)
    })

    it('rejects "m" (lowercase)', () => {
      const result = vmSizeSchema.safeParse('m')
      expect(result.success).toBe(false)
    })

    it('rejects numbers', () => {
      const result = vmSizeSchema.safeParse(1)
      expect(result.success).toBe(false)
    })
  })
})

describe('vmRequestFormSchema', () => {
  it('accepts valid form data with size', () => {
    const result = vmRequestFormSchema.safeParse({
      vmName: 'web-server-01',
      projectId: 'proj-1',
      justification: 'This is a valid justification for requesting a VM.',
      size: 'M',
    })
    expect(result.success).toBe(true)
  })

  it('accepts valid form data with different sizes', () => {
    for (const size of ['S', 'M', 'L', 'XL']) {
      const result = vmRequestFormSchema.safeParse({
        vmName: 'web-server-01',
        projectId: 'proj-1',
        justification: 'This is a valid justification for requesting a VM.',
        size,
      })
      expect(result.success).toBe(true)
    }
  })

  it('rejects form with invalid vmName', () => {
    const result = vmRequestFormSchema.safeParse({
      vmName: 'Web-Server', // Invalid: uppercase
      projectId: 'proj-1',
      justification: 'This is a valid justification for requesting a VM.',
      size: 'M',
    })
    expect(result.success).toBe(false)
  })

  it('rejects form with empty projectId', () => {
    const result = vmRequestFormSchema.safeParse({
      vmName: 'web-server-01',
      projectId: '',
      justification: 'This is a valid justification for requesting a VM.',
      size: 'M',
    })
    expect(result.success).toBe(false)
  })

  it('rejects form with short justification', () => {
    const result = vmRequestFormSchema.safeParse({
      vmName: 'web-server-01',
      projectId: 'proj-1',
      justification: 'Too short',
      size: 'M',
    })
    expect(result.success).toBe(false)
  })

  it('rejects form with missing size', () => {
    const result = vmRequestFormSchema.safeParse({
      vmName: 'web-server-01',
      projectId: 'proj-1',
      justification: 'This is a valid justification for requesting a VM.',
    })
    expect(result.success).toBe(false)
  })

  it('rejects form with invalid size', () => {
    const result = vmRequestFormSchema.safeParse({
      vmName: 'web-server-01',
      projectId: 'proj-1',
      justification: 'This is a valid justification for requesting a VM.',
      size: 'XXL', // Invalid size
    })
    expect(result.success).toBe(false)
  })

  it('rejects form with missing fields', () => {
    const result = vmRequestFormSchema.safeParse({
      vmName: 'web-server-01',
    })
    expect(result.success).toBe(false)
  })

  it('infers correct TypeScript type with size', () => {
    const validData = {
      vmName: 'web-server-01',
      projectId: 'proj-1',
      justification: 'Valid justification text here.',
      size: 'L' as const,
    }
    const result = vmRequestFormSchema.parse(validData)
    expect(result.vmName).toBe('web-server-01')
    expect(result.projectId).toBe('proj-1')
    expect(result.justification).toBe('Valid justification text here.')
    expect(result.size).toBe('L')
  })
})
