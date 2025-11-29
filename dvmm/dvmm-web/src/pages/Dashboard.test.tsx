import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@/test/test-utils'
import userEvent from '@testing-library/user-event'
import { Dashboard } from './Dashboard'

// Mock localStorage
const localStorageMock = {
  store: {} as Record<string, string>,
  getItem: vi.fn((key: string) => localStorageMock.store[key] ?? null),
  setItem: vi.fn((key: string, value: string) => {
    localStorageMock.store[key] = value
  }),
  removeItem: vi.fn((key: string) => {
    delete localStorageMock.store[key]
  }),
  clear: vi.fn(() => {
    localStorageMock.store = {}
  }),
}

Object.defineProperty(window, 'localStorage', { value: localStorageMock })

// Mock window.innerWidth
Object.defineProperty(window, 'innerWidth', {
  writable: true,
  configurable: true,
  value: 1024,
})

describe('Dashboard', () => {
  beforeEach(() => {
    localStorageMock.clear()
    vi.clearAllMocks()
    Object.defineProperty(window, 'innerWidth', { value: 1024 })
  })

  afterEach(() => {
    localStorageMock.clear()
  })

  describe('Empty States', () => {
    it('shows empty state for new user with no requests', () => {
      // Mark onboarding as complete to avoid tooltip interference
      localStorageMock.store['dvmm_onboarding_completed'] = 'true'

      render(<Dashboard />)

      expect(screen.getByText('Noch keine VMs angefordert')).toBeInTheDocument()
      expect(screen.getByText('Fordern Sie Ihre erste virtuelle Maschine an')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Erste VM anfordern' })).toBeInTheDocument()
    })

    it('shows stats cards with 0 values', () => {
      localStorageMock.store['dvmm_onboarding_completed'] = 'true'

      render(<Dashboard />)

      expect(screen.getByText('Pending Requests')).toBeInTheDocument()
      expect(screen.getByText('Approved Requests')).toBeInTheDocument()
      expect(screen.getByText('Provisioned VMs')).toBeInTheDocument()
    })
  })

  describe('Onboarding Flow', () => {
    it('shows first tooltip on CTA button for new user', async () => {
      render(<Dashboard />)

      // Should show first onboarding tooltip
      await waitFor(() => {
        expect(screen.getByText('Hier starten Sie eine neue VM-Anfrage')).toBeInTheDocument()
      })
    })

    it('completes onboarding flow when tooltips are dismissed', async () => {
      const user = userEvent.setup()
      render(<Dashboard />)

      // First tooltip should appear
      await waitFor(() => {
        expect(screen.getByText('Hier starten Sie eine neue VM-Anfrage')).toBeInTheDocument()
      })

      // Dismiss first tooltip
      await user.click(screen.getByRole('button', { name: 'Verstanden' }))

      // Should advance to next step (or complete on mobile)
      expect(localStorageMock.setItem).toHaveBeenCalled()
    })

    it('does not show tooltips when onboarding is complete', () => {
      localStorageMock.store['dvmm_onboarding_completed'] = 'true'

      render(<Dashboard />)

      expect(screen.queryByText('Hier starten Sie eine neue VM-Anfrage')).not.toBeInTheDocument()
      expect(screen.queryByText('Navigieren Sie zu Ihren Anfragen')).not.toBeInTheDocument()
    })

    it('skips sidebar tooltip on mobile viewport', async () => {
      Object.defineProperty(window, 'innerWidth', { value: 600 })
      const user = userEvent.setup()

      render(<Dashboard />)

      // First tooltip should appear
      await waitFor(() => {
        expect(screen.getByText('Hier starten Sie eine neue VM-Anfrage')).toBeInTheDocument()
      })

      // Dismiss first tooltip
      await user.click(screen.getByRole('button', { name: 'Verstanden' }))

      // On mobile, should skip sidebar and complete directly
      expect(localStorageMock.setItem).toHaveBeenCalledWith('dvmm_onboarding_completed', 'true')
    })
  })

  describe('CTA Button', () => {
    it('renders Request New VM button', () => {
      localStorageMock.store['dvmm_onboarding_completed'] = 'true'

      render(<Dashboard />)

      expect(screen.getByRole('button', { name: /Request New VM/i })).toBeInTheDocument()
    })

    it('has data-onboarding attribute for tooltip targeting', () => {
      localStorageMock.store['dvmm_onboarding_completed'] = 'true'

      render(<Dashboard />)

      const button = screen.getByRole('button', { name: /Request New VM/i })
      expect(button).toHaveAttribute('data-onboarding', 'cta-button')
    })
  })
})
