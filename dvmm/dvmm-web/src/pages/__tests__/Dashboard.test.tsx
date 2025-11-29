import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Dashboard } from '../Dashboard'

// Mock localStorage for onboarding hook
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

describe('Dashboard', () => {
  beforeEach(() => {
    localStorageMock.clear()
    vi.clearAllMocks()
    // Mark onboarding as complete to avoid tooltip interference with basic tests
    localStorageMock.store['dvmm_onboarding_completed'] = 'true'
  })

  afterEach(() => {
    localStorageMock.clear()
  })

  it('displays dashboard heading', () => {
    render(<Dashboard />)

    expect(screen.getByRole('heading', { name: /dashboard/i })).toBeInTheDocument()
  })

  it('displays Request New VM CTA button', () => {
    render(<Dashboard />)

    expect(screen.getByRole('button', { name: /request new vm/i })).toBeInTheDocument()
  })

  it('displays CTA button with Tech Teal styling (primary variant)', () => {
    render(<Dashboard />)

    const ctaButton = screen.getByRole('button', { name: /request new vm/i })
    // shadcn Button with default variant uses primary color
    expect(ctaButton).toBeInTheDocument()
    // Button should be large size
    expect(ctaButton.className).toContain('gap-2')
  })

  it('displays stats cards with placeholder values (0, 0, 0)', () => {
    render(<Dashboard />)

    // All three stats should show 0
    const zeros = screen.getAllByText('0')
    expect(zeros).toHaveLength(3)
  })

  it('displays Pending Requests stat', () => {
    render(<Dashboard />)

    expect(screen.getByText('Pending Requests')).toBeInTheDocument()
  })

  it('displays Approved Requests stat', () => {
    render(<Dashboard />)

    expect(screen.getByText('Approved Requests')).toBeInTheDocument()
  })

  it('displays Provisioned VMs stat', () => {
    render(<Dashboard />)

    expect(screen.getByText('Provisioned VMs')).toBeInTheDocument()
  })

  it('displays My Requests section with German empty state', () => {
    render(<Dashboard />)

    expect(screen.getByText('My Requests')).toBeInTheDocument()
    // German empty state text (updated in Story 2.3)
    expect(screen.getByText('Noch keine VMs angefordert')).toBeInTheDocument()
  })
})
