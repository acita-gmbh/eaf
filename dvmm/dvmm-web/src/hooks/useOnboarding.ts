import { useState, useCallback } from 'react'

const STORAGE_KEYS = {
  step: 'dvmm_onboarding_step',
  completed: 'dvmm_onboarding_completed',
} as const

const ONBOARDING_STEPS = ['cta-button', 'sidebar-nav'] as const

export type OnboardingStep = (typeof ONBOARDING_STEPS)[number]

export interface UseOnboardingReturn {
  /** Current onboarding step ID, or null if complete */
  currentStep: OnboardingStep | null
  /** Whether onboarding is complete */
  isComplete: boolean
  /** Dismiss current step and advance to next */
  dismissStep: () => void
  /** Reset onboarding to start (dev action) */
  resetOnboarding: () => void
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
  const readFromStorage = useCallback(() => {
    const completed = localStorage.getItem(STORAGE_KEYS.completed)
    if (completed === 'true') {
      return { stepIndex: ONBOARDING_STEPS.length, isComplete: true }
    }

    const storedStep = localStorage.getItem(STORAGE_KEYS.step)
    const stepIndex = storedStep ? parseInt(storedStep, 10) : 0

    return { stepIndex, isComplete: false }
  }, [])

  const [state, setState] = useState(() => readFromStorage())

  const isMobile = () => {
    // Check viewport width - mobile is < 768px
    return typeof window !== 'undefined' && window.innerWidth < 768
  }

  const getCurrentStep = useCallback((): OnboardingStep | null => {
    if (state.isComplete) return null
    if (state.stepIndex >= ONBOARDING_STEPS.length) return null
    return ONBOARDING_STEPS[state.stepIndex]
  }, [state.stepIndex, state.isComplete])

  const dismissStep = useCallback(() => {
    setState((prev) => {
      let nextIndex = prev.stepIndex + 1

      // On mobile, skip the sidebar step (index 1)
      if (isMobile() && nextIndex === 1) {
        nextIndex = ONBOARDING_STEPS.length
      }

      if (nextIndex >= ONBOARDING_STEPS.length) {
        // Complete onboarding
        localStorage.setItem(STORAGE_KEYS.completed, 'true')
        return { stepIndex: nextIndex, isComplete: true }
      }

      localStorage.setItem(STORAGE_KEYS.step, String(nextIndex))
      return { stepIndex: nextIndex, isComplete: false }
    })
  }, [])

  const resetOnboarding = useCallback(() => {
    localStorage.removeItem(STORAGE_KEYS.step)
    localStorage.removeItem(STORAGE_KEYS.completed)
    setState({ stepIndex: 0, isComplete: false })
  }, [])

  return {
    currentStep: getCurrentStep(),
    isComplete: state.isComplete,
    dismissStep,
    resetOnboarding,
  }
}
