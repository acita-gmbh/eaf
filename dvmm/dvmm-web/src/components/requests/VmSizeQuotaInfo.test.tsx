import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { VmSizeQuotaInfo } from './VmSizeQuotaInfo'
import type { MockProject } from '@/lib/mock-data/projects'

describe('VmSizeQuotaInfo', () => {
  describe('when no quota provided', () => {
    it('renders nothing when projectQuota is undefined', () => {
      const { container } = render(<VmSizeQuotaInfo projectQuota={undefined} />)
      expect(container.firstChild).toBeNull()
    })
  })

  describe('quota display', () => {
    const normalQuota: MockProject['quota'] = { used: 5, total: 10 }

    it('displays available VMs count', () => {
      render(<VmSizeQuotaInfo projectQuota={normalQuota} />)
      expect(screen.getByText(/Available: 5 of 10 VMs/)).toBeInTheDocument()
    })

    it('displays progress bar', () => {
      render(<VmSizeQuotaInfo projectQuota={normalQuota} />)
      // Progress component should be present
      const progressbar = screen.getByRole('progressbar')
      expect(progressbar).toBeInTheDocument()
    })

    it('calculates correct usage percentage', () => {
      render(<VmSizeQuotaInfo projectQuota={normalQuota} />)
      const progressbar = screen.getByRole('progressbar')
      expect(progressbar).toHaveAttribute('aria-valuenow', '50')
    })
  })

  describe('warning state (>80% used)', () => {
    const warningQuota: MockProject['quota'] = { used: 9, total: 10 } // 90% used

    it('shows warning styling when quota >80% used', () => {
      render(<VmSizeQuotaInfo projectQuota={warningQuota} />)

      const container = screen.getByRole('region')
      expect(container.className).toContain('border-amber')
      expect(container.className).toContain('bg-amber')
    })

    it('displays AlertTriangle icon in warning state', () => {
      render(<VmSizeQuotaInfo projectQuota={warningQuota} />)

      // lucide-react renders SVG with class based on icon ID (triangle-alert)
      const icon = document.querySelector('svg.lucide-triangle-alert')
      expect(icon).toBeInTheDocument()
    })

    it('shows warning for exactly 80% used (boundary condition)', () => {
      const eightyPercent: MockProject['quota'] = { used: 8, total: 10 }
      render(<VmSizeQuotaInfo projectQuota={eightyPercent} />)

      const container = screen.getByRole('region')
      // 80% triggers warning (isWarning = usagePercent >= 80)
      expect(container.className).toContain('border-amber')
    })

    it('shows warning for just over 80% used', () => {
      const overEighty: MockProject['quota'] = { used: 81, total: 100 }
      render(<VmSizeQuotaInfo projectQuota={overEighty} />)

      const container = screen.getByRole('region')
      expect(container.className).toContain('border-amber')
    })
  })

  describe('non-warning states', () => {
    it('does not show warning at 79% usage', () => {
      const underEighty: MockProject['quota'] = { used: 79, total: 100 }
      render(<VmSizeQuotaInfo projectQuota={underEighty} />)

      const container = screen.getByRole('region')
      expect(container.className).not.toContain('border-amber')
    })

    it('does not show AlertTriangle icon when not in warning', () => {
      const normalQuota: MockProject['quota'] = { used: 5, total: 10 }
      render(<VmSizeQuotaInfo projectQuota={normalQuota} />)

      const icon = document.querySelector('svg.lucide-triangle-alert')
      expect(icon).not.toBeInTheDocument()
    })
  })

  describe('edge cases', () => {
    it('handles zero quota gracefully', () => {
      const zeroQuota: MockProject['quota'] = { used: 0, total: 0 }
      render(<VmSizeQuotaInfo projectQuota={zeroQuota} />)

      expect(screen.getByText(/Available: 0 of 0 VMs/)).toBeInTheDocument()
      // Zero total means 100% used (fully exhausted)
      const container = screen.getByRole('region')
      expect(container.className).toContain('border-amber')
    })

    it('handles fully utilized quota (100%)', () => {
      const fullQuota: MockProject['quota'] = { used: 10, total: 10 }
      render(<VmSizeQuotaInfo projectQuota={fullQuota} />)

      expect(screen.getByText(/Available: 0 of 10 VMs/)).toBeInTheDocument()
      const container = screen.getByRole('region')
      expect(container.className).toContain('border-amber')
    })

    it('handles empty quota (no usage)', () => {
      const emptyQuota: MockProject['quota'] = { used: 0, total: 10 }
      render(<VmSizeQuotaInfo projectQuota={emptyQuota} />)

      expect(screen.getByText(/Available: 10 of 10 VMs/)).toBeInTheDocument()
      const progressbar = screen.getByRole('progressbar')
      expect(progressbar).toHaveAttribute('aria-valuenow', '0')
    })
  })

  describe('accessibility', () => {
    it('has role="region"', () => {
      const quota: MockProject['quota'] = { used: 5, total: 10 }
      render(<VmSizeQuotaInfo projectQuota={quota} />)

      expect(screen.getByRole('region')).toBeInTheDocument()
    })

    it('has aria-live="polite" for screen readers', () => {
      const quota: MockProject['quota'] = { used: 5, total: 10 }
      render(<VmSizeQuotaInfo projectQuota={quota} />)

      const region = screen.getByRole('region')
      expect(region).toHaveAttribute('aria-live', 'polite')
    })

    it('has accessible label', () => {
      const quota: MockProject['quota'] = { used: 5, total: 10 }
      render(<VmSizeQuotaInfo projectQuota={quota} />)

      const region = screen.getByRole('region')
      expect(region).toHaveAttribute('aria-label', 'Quota status')
    })
  })

  describe('dark mode support', () => {
    it('includes dark mode classes for warning state', () => {
      const warningQuota: MockProject['quota'] = { used: 9, total: 10 }
      render(<VmSizeQuotaInfo projectQuota={warningQuota} />)

      const container = screen.getByRole('region')
      // Should include dark: variant classes
      expect(container.className).toContain('dark:bg-amber')
    })
  })
})
