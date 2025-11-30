import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@/test/test-utils'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { Dashboard } from './Dashboard'

// Wrapper for Dashboard that provides router context
function renderDashboard() {
  return render(
    <MemoryRouter>
      <Dashboard />
    </MemoryRouter>
  )
}

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

      renderDashboard()

      expect(screen.getByText('No VMs requested yet')).toBeInTheDocument()
      expect(screen.getByText('Request your first virtual machine')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Request First VM' })).toBeInTheDocument()
    })

    it('shows stats cards with 0 values', () => {
      localStorageMock.store['dvmm_onboarding_completed'] = 'true'

      renderDashboard()

      expect(screen.getByText('Pending Requests')).toBeInTheDocument()
      expect(screen.getByText('Approved Requests')).toBeInTheDocument()
      expect(screen.getByText('Provisioned VMs')).toBeInTheDocument()
    })
  })

  describe('Onboarding Flow', () => {
    it('shows first tooltip on CTA button for new user', async () => {
      renderDashboard()

      // Should show first onboarding tooltip
      await waitFor(() => {
        expect(screen.getByText('Start a new VM request here')).toBeInTheDocument()
      })
    })

    it('completes onboarding flow when tooltips are dismissed', async () => {
      const user = userEvent.setup()
      renderDashboard()

      // First tooltip should appear
      await waitFor(() => {
        expect(screen.getByText('Start a new VM request here')).toBeInTheDocument()
      })

      // Dismiss first tooltip
      await user.click(screen.getByRole('button', { name: 'Got it' }))

      // Should advance to next step (or complete on mobile)
      expect(localStorageMock.setItem).toHaveBeenCalled()
    })

    it('does not show tooltips when onboarding is complete', () => {
      localStorageMock.store['dvmm_onboarding_completed'] = 'true'

      renderDashboard()

      expect(screen.queryByText('Start a new VM request here')).not.toBeInTheDocument()
      expect(screen.queryByText('Navigate to your requests')).not.toBeInTheDocument()
    })

    it('skips sidebar tooltip on mobile viewport', async () => {
      Object.defineProperty(window, 'innerWidth', { value: 600 })
      const user = userEvent.setup()

      renderDashboard()

      // First tooltip should appear
      await waitFor(() => {
        expect(screen.getByText('Start a new VM request here')).toBeInTheDocument()
      })

      // Dismiss first tooltip
      await user.click(screen.getByRole('button', { name: 'Got it' }))

      // On mobile, should skip sidebar and complete directly
      expect(localStorageMock.setItem).toHaveBeenCalledWith('dvmm_onboarding_completed', 'true')
    })
  })

  describe('CTA Button', () => {
    it('renders Request New VM button', () => {
      localStorageMock.store['dvmm_onboarding_completed'] = 'true'

      renderDashboard()

      expect(screen.getByRole('button', { name: /Request New VM/i })).toBeInTheDocument()
    })

    it('has data-onboarding attribute for tooltip targeting', () => {
      localStorageMock.store['dvmm_onboarding_completed'] = 'true'

      renderDashboard()

      const button = screen.getByRole('button', { name: /Request New VM/i })
      expect(button).toHaveAttribute('data-onboarding', 'cta-button')
    })
  })

  describe('Layout', () => {
    it('displays dashboard heading', () => {
      localStorageMock.store['dvmm_onboarding_completed'] = 'true'

      renderDashboard()

      expect(screen.getByRole('heading', { name: /dashboard/i })).toBeInTheDocument()
    })

    it('displays all three stats cards with 0 values', () => {
      localStorageMock.store['dvmm_onboarding_completed'] = 'true'

      renderDashboard()

      // All three stats should show 0
      const zeros = screen.getAllByText('0')
      expect(zeros).toHaveLength(3)
    })

    it('displays My Requests section', () => {
      localStorageMock.store['dvmm_onboarding_completed'] = 'true'

      renderDashboard()

      expect(screen.getByText('My Requests')).toBeInTheDocument()
    })
  })
})
