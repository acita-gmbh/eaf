import { describe, it, expect } from 'vitest'
import { VM_SIZES, DEFAULT_VM_SIZE, getVmSizeById, type VmSize, type VmSizeId } from './vm-sizes'

describe('VM Sizes Configuration', () => {
  describe('VM_SIZES constant', () => {
    it('contains exactly 4 sizes: S, M, L, XL', () => {
      expect(VM_SIZES).toHaveLength(4)
      expect(VM_SIZES.map(s => s.id)).toEqual(['S', 'M', 'L', 'XL'])
    })

    it('has correct specs for Small (S)', () => {
      const small = VM_SIZES.find(s => s.id === 'S')
      expect(small).toEqual({
        id: 'S',
        label: 'Small',
        vCpu: 2,
        ramGb: 4,
        diskGb: 50,
        monthlyEstimateEur: 25,
      })
    })

    it('has correct specs for Medium (M)', () => {
      const medium = VM_SIZES.find(s => s.id === 'M')
      expect(medium).toEqual({
        id: 'M',
        label: 'Medium',
        vCpu: 4,
        ramGb: 8,
        diskGb: 100,
        monthlyEstimateEur: 50,
      })
    })

    it('has correct specs for Large (L)', () => {
      const large = VM_SIZES.find(s => s.id === 'L')
      expect(large).toEqual({
        id: 'L',
        label: 'Large',
        vCpu: 8,
        ramGb: 16,
        diskGb: 200,
        monthlyEstimateEur: 100,
      })
    })

    it('has correct specs for Extra Large (XL)', () => {
      const xl = VM_SIZES.find(s => s.id === 'XL')
      expect(xl).toEqual({
        id: 'XL',
        label: 'Extra Large',
        vCpu: 16,
        ramGb: 32,
        diskGb: 500,
        monthlyEstimateEur: 200,
      })
    })

    it('has increasing resources for each size tier', () => {
      const [s, m, l, xl] = VM_SIZES

      // vCPU increases
      expect(s.vCpu).toBeLessThan(m.vCpu)
      expect(m.vCpu).toBeLessThan(l.vCpu)
      expect(l.vCpu).toBeLessThan(xl.vCpu)

      // RAM increases
      expect(s.ramGb).toBeLessThan(m.ramGb)
      expect(m.ramGb).toBeLessThan(l.ramGb)
      expect(l.ramGb).toBeLessThan(xl.ramGb)

      // Disk increases
      expect(s.diskGb).toBeLessThan(m.diskGb)
      expect(m.diskGb).toBeLessThan(l.diskGb)
      expect(l.diskGb).toBeLessThan(xl.diskGb)
    })

    it('all sizes have positive numeric values', () => {
      for (const size of VM_SIZES) {
        expect(size.vCpu).toBeGreaterThan(0)
        expect(size.ramGb).toBeGreaterThan(0)
        expect(size.diskGb).toBeGreaterThan(0)
        expect(size.monthlyEstimateEur).toBeGreaterThan(0)
      }
    })
  })

  describe('DEFAULT_VM_SIZE', () => {
    it('is set to Medium (M)', () => {
      expect(DEFAULT_VM_SIZE).toBe('M')
    })

    it('exists in VM_SIZES array', () => {
      const defaultSize = VM_SIZES.find(s => s.id === DEFAULT_VM_SIZE)
      expect(defaultSize).toBeDefined()
    })
  })

  describe('getVmSizeById', () => {
    it('returns correct size for valid ID', () => {
      const medium = getVmSizeById('M')
      expect(medium?.id).toBe('M')
      expect(medium?.vCpu).toBe(4)
    })

    it('returns undefined for invalid ID', () => {
      expect(getVmSizeById('XXL')).toBeUndefined()
      expect(getVmSizeById('invalid')).toBeUndefined()
      expect(getVmSizeById('')).toBeUndefined()
    })

    it('is case-sensitive', () => {
      expect(getVmSizeById('m')).toBeUndefined()
      expect(getVmSizeById('M')).toBeDefined()
    })
  })

  describe('Type safety', () => {
    it('VmSizeId is a union of valid size IDs', () => {
      // TypeScript will catch invalid assignments at compile time
      const validId: VmSizeId = 'S'
      expect(['S', 'M', 'L', 'XL']).toContain(validId)
    })

    it('VmSize type matches expected structure', () => {
      const size: VmSize = VM_SIZES[0]
      expect(size).toHaveProperty('id')
      expect(size).toHaveProperty('label')
      expect(size).toHaveProperty('vCpu')
      expect(size).toHaveProperty('ramGb')
      expect(size).toHaveProperty('diskGb')
      expect(size).toHaveProperty('monthlyEstimateEur')
    })
  })
})
