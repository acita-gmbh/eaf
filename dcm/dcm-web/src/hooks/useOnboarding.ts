import { useState } from 'react'

// Exported for testing utilities
export const STORAGE_KEYS = {
  step: 'dcm_onboarding_step',
  completed: 'dcm_onboarding_completed',
} as const

const ONBOARDING_STEPS = ['cta-button', 'sidebar-nav'] as const

export type OnboardingStep = (typeof ONBOARDING_STEPS)[number]

export interface UseOnboardingReturn {
  /** Current onboarding step ID, or null if complete */
  currentStep: OnboardingStep | null
  /** Whether onboarding is complete (derived from currentStep === null) */
  isComplete: boolean
  /** Dismiss current step and advance to next */
  dismissStep: () => void
  /** Reset onboarding to start (dev action) */
  resetOnboarding: () => void
}

// Safe localStorage wrappers - handles private browsing and disabled storage
const safeGetItem = (key: string): string | null => {
  try {
    return localStorage.getItem(key)
  } catch (e) {
    console.warn(`[useOnboarding] localStorage.getItem failed for ${key}:`, e)
    return null
  }
}

const safeSetItem = (key: string, value: string): void => {
  try {
    localStorage.setItem(key, value)
  } catch (e) {
    console.warn(`[useOnboarding] localStorage.setItem failed for ${key}:`, e)
  }
}

const safeRemoveItem = (key: string): void => {
  try {
    localStorage.removeItem(key)
  } catch (e) {
    console.warn(`[useOnboarding] localStorage.removeItem failed for ${key}:`, e)
  }
}

// Check if viewport is mobile (< 768px)
export const isMobile = (): boolean => {
  if (typeof window === 'undefined') {
    return false // Safe default for SSR
  }
  return window.innerWidth < 768
}

/**
 * Hook for managing onboarding tooltip state.
 * Persists progress in localStorage.
 *
 * Steps:
 * 1. 'cta-button' - Highlight the "Request New VM" button
 * 2. 'sidebar-nav' - Highlight sidebar navigation (desktop only)
 */
export function useOnboarding(): UseOnboardingReturn {
  // Read initial state from storage
  const readFromStorage = () => {
    const completed = safeGetItem(STORAGE_KEYS.completed)
    if (completed === 'true') {
      return { stepIndex: ONBOARDING_STEPS.length, isComplete: true }
    }

    const storedStep = safeGetItem(STORAGE_KEYS.step)
    let stepIndex = 0

    if (storedStep !== null) {
      const parsed = parseInt(storedStep, 10)
      // Validate parsed value - reset to 0 if invalid or out of range
      if (Number.isNaN(parsed) || parsed < 0 || parsed >= ONBOARDING_STEPS.length) {
        console.warn(`[useOnboarding] Invalid step index in storage: "${storedStep}", resetting to 0`)
        safeRemoveItem(STORAGE_KEYS.step)
      } else {
        stepIndex = parsed
      }
    }

    return { stepIndex, isComplete: false }
  }

  const [state, setState] = useState(() => readFromStorage())

  // Derive current step from state - React Compiler handles optimization
  const getCurrentStep = (): OnboardingStep | null => {
    if (state.isComplete) return null
    if (state.stepIndex >= ONBOARDING_STEPS.length) return null
    return ONBOARDING_STEPS[state.stepIndex]
  }

  const dismissStep = () => {
    setState((prev) => {
      let nextIndex = prev.stepIndex + 1

      // On mobile, skip the sidebar step (index 1)
      if (isMobile() && nextIndex === 1) {
        nextIndex = ONBOARDING_STEPS.length
      }

      if (nextIndex >= ONBOARDING_STEPS.length) {
        // Complete onboarding
        safeSetItem(STORAGE_KEYS.completed, 'true')
        return { stepIndex: nextIndex, isComplete: true }
      }

      safeSetItem(STORAGE_KEYS.step, String(nextIndex))
      return { stepIndex: nextIndex, isComplete: false }
    })
  }

  const resetOnboarding = () => {
    safeRemoveItem(STORAGE_KEYS.step)
    safeRemoveItem(STORAGE_KEYS.completed)
    setState({ stepIndex: 0, isComplete: false })
  }

  return {
    currentStep: getCurrentStep(),
    isComplete: state.isComplete,
    dismissStep,
    resetOnboarding,
  }
}
