import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@/test/test-utils'
import { VmwareConfigWarning } from './VmwareConfigWarning'

// Mock useIsAdmin hook
const mockUseIsAdmin = vi.hoisted(() => vi.fn(() => true))
vi.mock('@/hooks/useIsAdmin', () => ({
  useIsAdmin: mockUseIsAdmin,
}))

// Mock useVmwareConfigExists hook
const mockUseVmwareConfigExists = vi.hoisted(() =>
  vi.fn(() => ({
    data: null,
    isLoading: false,
    isError: false,
    error: null,
  }))
)

vi.mock('@/hooks/useVmwareConfig', () => ({
  useVmwareConfigExists: mockUseVmwareConfigExists,
}))

describe('VmwareConfigWarning', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseIsAdmin.mockReturnValue(true)
    mockUseVmwareConfigExists.mockReturnValue({
      data: null,
      isLoading: false,
      isError: false,
      error: null,
    })
  })

  describe('visibility conditions', () => {
    it('does not render for non-admin users', () => {
      mockUseIsAdmin.mockReturnValue(false)

      render(<VmwareConfigWarning />)

      expect(screen.queryByTestId('vmware-config-warning')).not.toBeInTheDocument()
      expect(screen.queryByTestId('vmware-config-unverified-warning')).not.toBeInTheDocument()
    })

    it('does not render while loading', () => {
      mockUseVmwareConfigExists.mockReturnValue({
        data: null,
        isLoading: true,
        isError: false,
        error: null,
      })

      render(<VmwareConfigWarning />)

      expect(screen.queryByTestId('vmware-config-warning')).not.toBeInTheDocument()
      expect(screen.queryByTestId('vmware-config-unverified-warning')).not.toBeInTheDocument()
    })

    it('does not render on error (fails silently)', () => {
      mockUseVmwareConfigExists.mockReturnValue({
        data: null,
        isLoading: false,
        isError: true,
        error: new Error('Failed to fetch'),
      })

      render(<VmwareConfigWarning />)

      expect(screen.queryByTestId('vmware-config-warning')).not.toBeInTheDocument()
      expect(screen.queryByTestId('vmware-config-unverified-warning')).not.toBeInTheDocument()
    })

    it('does not render when config exists and is verified', () => {
      mockUseVmwareConfigExists.mockReturnValue({
        data: {
          exists: true,
          verifiedAt: '2024-01-15T10:00:00Z',
        },
        isLoading: false,
        isError: false,
        error: null,
      })

      render(<VmwareConfigWarning />)

      expect(screen.queryByTestId('vmware-config-warning')).not.toBeInTheDocument()
      expect(screen.queryByTestId('vmware-config-unverified-warning')).not.toBeInTheDocument()
    })
  })

  describe('not configured warning', () => {
    it('renders warning when config does not exist', () => {
      mockUseVmwareConfigExists.mockReturnValue({
        data: {
          exists: false,
          verifiedAt: null,
        },
        isLoading: false,
        isError: false,
        error: null,
      })

      render(<VmwareConfigWarning />)

      expect(screen.getByTestId('vmware-config-warning')).toBeInTheDocument()
      expect(screen.getByText('VMware vCenter Not Configured')).toBeInTheDocument()
      expect(screen.getByText(/VM provisioning will not work/i)).toBeInTheDocument()
    })

    it('renders Configure vCenter button that links to settings', () => {
      mockUseVmwareConfigExists.mockReturnValue({
        data: {
          exists: false,
          verifiedAt: null,
        },
        isLoading: false,
        isError: false,
        error: null,
      })

      render(<VmwareConfigWarning />)

      const configureButton = screen.getByRole('link', { name: /configure vcenter/i })
      expect(configureButton).toHaveAttribute('href', '/admin/settings')
    })

    it('has correct accessibility attributes', () => {
      mockUseVmwareConfigExists.mockReturnValue({
        data: {
          exists: false,
          verifiedAt: null,
        },
        isLoading: false,
        isError: false,
        error: null,
      })

      render(<VmwareConfigWarning />)

      const warning = screen.getByTestId('vmware-config-warning')
      expect(warning).toHaveAttribute('role', 'alert')
      expect(warning).toHaveAttribute('aria-live', 'polite')
    })
  })

  describe('unverified warning', () => {
    it('renders milder warning when config exists but never verified', () => {
      mockUseVmwareConfigExists.mockReturnValue({
        data: {
          exists: true,
          verifiedAt: null,
        },
        isLoading: false,
        isError: false,
        error: null,
      })

      render(<VmwareConfigWarning />)

      expect(screen.getByTestId('vmware-config-unverified-warning')).toBeInTheDocument()
      expect(screen.getByText('VMware Connection Not Verified')).toBeInTheDocument()
      expect(screen.getByText(/test the vcenter connection/i)).toBeInTheDocument()
    })

    it('renders Verify Connection button that links to settings', () => {
      mockUseVmwareConfigExists.mockReturnValue({
        data: {
          exists: true,
          verifiedAt: null,
        },
        isLoading: false,
        isError: false,
        error: null,
      })

      render(<VmwareConfigWarning />)

      const verifyButton = screen.getByRole('link', { name: /verify connection/i })
      expect(verifyButton).toHaveAttribute('href', '/admin/settings')
    })

    it('has correct accessibility attributes for unverified warning', () => {
      mockUseVmwareConfigExists.mockReturnValue({
        data: {
          exists: true,
          verifiedAt: null,
        },
        isLoading: false,
        isError: false,
        error: null,
      })

      render(<VmwareConfigWarning />)

      const warning = screen.getByTestId('vmware-config-unverified-warning')
      expect(warning).toHaveAttribute('role', 'alert')
      expect(warning).toHaveAttribute('aria-live', 'polite')
    })
  })

  describe('styling', () => {
    it('not configured warning has yellow styling', () => {
      mockUseVmwareConfigExists.mockReturnValue({
        data: {
          exists: false,
          verifiedAt: null,
        },
        isLoading: false,
        isError: false,
        error: null,
      })

      render(<VmwareConfigWarning />)

      const warning = screen.getByTestId('vmware-config-warning')
      expect(warning.className).toContain('border-yellow-500')
      expect(warning.className).toContain('bg-yellow-50')
    })

    it('unverified warning has milder yellow styling', () => {
      mockUseVmwareConfigExists.mockReturnValue({
        data: {
          exists: true,
          verifiedAt: null,
        },
        isLoading: false,
        isError: false,
        error: null,
      })

      render(<VmwareConfigWarning />)

      const warning = screen.getByTestId('vmware-config-unverified-warning')
      expect(warning.className).toContain('border-yellow-500/50')
      expect(warning.className).toContain('bg-yellow-50/50')
    })
  })
})
