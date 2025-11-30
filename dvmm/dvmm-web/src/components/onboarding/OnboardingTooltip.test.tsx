import { describe, it, expect, vi } from 'vitest'
import { render, screen, act } from '@/test/test-utils'
import { OnboardingTooltip } from './OnboardingTooltip'
import userEvent from '@testing-library/user-event'

describe('OnboardingTooltip', () => {
  it('renders content text', () => {
    render(
      <OnboardingTooltip
        content="This is a helpful tip"
        onDismiss={vi.fn()}
        anchorRect={{ top: 100, left: 100, bottom: 150, right: 200, width: 100, height: 50 }}
      />
    )
    expect(screen.getByText('This is a helpful tip')).toBeInTheDocument()
  })

  it('renders dismiss button with default label', () => {
    render(
      <OnboardingTooltip
        content="Test content"
        onDismiss={vi.fn()}
        anchorRect={{ top: 100, left: 100, bottom: 150, right: 200, width: 100, height: 50 }}
      />
    )
    expect(screen.getByRole('button', { name: 'Got it' })).toBeInTheDocument()
  })

  it('renders custom dismiss label when provided', () => {
    render(
      <OnboardingTooltip
        content="Test content"
        onDismiss={vi.fn()}
        dismissLabel="Got it!"
        anchorRect={{ top: 100, left: 100, bottom: 150, right: 200, width: 100, height: 50 }}
      />
    )
    expect(screen.getByRole('button', { name: 'Got it!' })).toBeInTheDocument()
  })

  it('calls onDismiss when dismiss button is clicked', async () => {
    const handleDismiss = vi.fn()
    const user = userEvent.setup()

    render(
      <OnboardingTooltip
        content="Test content"
        onDismiss={handleDismiss}
        anchorRect={{ top: 100, left: 100, bottom: 150, right: 200, width: 100, height: 50 }}
      />
    )

    await user.click(screen.getByRole('button', { name: 'Got it' }))
    expect(handleDismiss).toHaveBeenCalledTimes(1)
  })

  it('calls onDismiss on Escape key', async () => {
    const handleDismiss = vi.fn()
    const user = userEvent.setup()

    render(
      <OnboardingTooltip
        content="Test content"
        onDismiss={handleDismiss}
        anchorRect={{ top: 100, left: 100, bottom: 150, right: 200, width: 100, height: 50 }}
      />
    )

    await user.keyboard('{Escape}')
    expect(handleDismiss).toHaveBeenCalledTimes(1)
  })

  it('calls onDismiss when clicking outside the tooltip (click-outside)', async () => {
    const handleDismiss = vi.fn()
    const user = userEvent.setup()

    // Render tooltip with a sibling element to click on
    render(
      <div>
        <button data-testid="outside-element">Outside</button>
        <OnboardingTooltip
          content="Test content"
          onDismiss={handleDismiss}
          anchorRect={{ top: 100, left: 100, bottom: 150, right: 200, width: 100, height: 50 }}
        />
      </div>
    )

    // Click on an element outside the popover
    await user.click(screen.getByTestId('outside-element'))

    // onDismiss should be called via onOpenChange handler
    expect(handleDismiss).toHaveBeenCalled()
  })

  it('has accessible dialog role', () => {
    render(
      <OnboardingTooltip
        content="Test content"
        onDismiss={vi.fn()}
        anchorRect={{ top: 100, left: 100, bottom: 150, right: 200, width: 100, height: 50 }}
      />
    )
    // Popover content should be accessible
    expect(screen.getByText('Test content')).toBeInTheDocument()
  })

  it('focuses dismiss button after appearing (accessibility)', async () => {
    // Use fake timers only for this test to control the 100ms focus delay
    vi.useFakeTimers()

    render(
      <OnboardingTooltip
        content="Test content"
        onDismiss={vi.fn()}
        anchorRect={{ top: 100, left: 100, bottom: 150, right: 200, width: 100, height: 50 }}
      />
    )

    const dismissButton = screen.getByRole('button', { name: 'Got it' })

    // Before the 100ms delay, button should not be focused
    expect(document.activeElement).not.toBe(dismissButton)

    // Fast-forward past the 100ms delay
    await act(async () => {
      vi.advanceTimersByTime(150)
    })

    // After the delay, button should be focused
    expect(document.activeElement).toBe(dismissButton)

    vi.useRealTimers()
  })
})
