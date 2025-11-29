import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { useOnboarding } from './useOnboarding'

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

// Mock window.innerWidth for responsive tests
let mockInnerWidth = 1024

Object.defineProperty(window, 'innerWidth', {
  writable: true,
  configurable: true,
  value: mockInnerWidth,
})

describe('useOnboarding', () => {
  beforeEach(() => {
    localStorageMock.clear()
    vi.clearAllMocks()
    mockInnerWidth = 1024
    Object.defineProperty(window, 'innerWidth', { value: mockInnerWidth })
  })

  afterEach(() => {
    localStorageMock.clear()
  })

  it('returns initial state for new user (no localStorage data)', () => {
    const { result } = renderHook(() => useOnboarding())

    expect(result.current.currentStep).toBe('cta-button')
    expect(result.current.isComplete).toBe(false)
  })

  it('reads step progress from localStorage', () => {
    localStorageMock.store['dvmm_onboarding_step'] = '1'

    const { result } = renderHook(() => useOnboarding())

    expect(result.current.currentStep).toBe('sidebar-nav')
  })

  it('returns isComplete true when localStorage has completed flag', () => {
    localStorageMock.store['dvmm_onboarding_completed'] = 'true'

    const { result } = renderHook(() => useOnboarding())

    expect(result.current.isComplete).toBe(true)
    expect(result.current.currentStep).toBeNull()
  })

  it('dismissStep advances to next step', async () => {
    const { result } = renderHook(() => useOnboarding())

    expect(result.current.currentStep).toBe('cta-button')

    act(() => {
      result.current.dismissStep()
    })

    await waitFor(() => {
      expect(result.current.currentStep).toBe('sidebar-nav')
    })

    expect(localStorageMock.setItem).toHaveBeenCalledWith('dvmm_onboarding_step', '1')
  })

  it('dismissStep marks complete on final step', async () => {
    localStorageMock.store['dvmm_onboarding_step'] = '1'

    const { result } = renderHook(() => useOnboarding())

    expect(result.current.currentStep).toBe('sidebar-nav')

    act(() => {
      result.current.dismissStep()
    })

    await waitFor(() => {
      expect(result.current.isComplete).toBe(true)
    })

    expect(localStorageMock.setItem).toHaveBeenCalledWith('dvmm_onboarding_completed', 'true')
  })

  it('skips sidebar step on mobile viewport (< 768px)', () => {
    Object.defineProperty(window, 'innerWidth', { value: 600 })

    const { result } = renderHook(() => useOnboarding())

    expect(result.current.currentStep).toBe('cta-button')

    act(() => {
      result.current.dismissStep()
    })

    // Should skip directly to complete on mobile
    expect(result.current.isComplete).toBe(true)
    expect(localStorageMock.setItem).toHaveBeenCalledWith('dvmm_onboarding_completed', 'true')
  })

  it('resetOnboarding clears localStorage and restarts', () => {
    localStorageMock.store['dvmm_onboarding_step'] = '1'
    localStorageMock.store['dvmm_onboarding_completed'] = 'true'

    const { result } = renderHook(() => useOnboarding())

    act(() => {
      result.current.resetOnboarding()
    })

    expect(localStorageMock.removeItem).toHaveBeenCalledWith('dvmm_onboarding_step')
    expect(localStorageMock.removeItem).toHaveBeenCalledWith('dvmm_onboarding_completed')
    expect(result.current.currentStep).toBe('cta-button')
    expect(result.current.isComplete).toBe(false)
  })
})
