import { useEffect, useRef } from 'react'
import { Popover, PopoverContent, PopoverAnchor } from '@/components/ui/popover'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export interface OnboardingTooltipProps {
  /** Tooltip content text */
  content: string
  /** Callback when tooltip is dismissed */
  onDismiss: () => void
  /** Position of the anchor element (for virtual positioning) */
  anchorRect: DOMRect | { top: number; left: number; bottom: number; right: number; width: number; height: number }
  /** Preferred side for positioning */
  side?: 'top' | 'right' | 'bottom' | 'left'
  /** Custom dismiss button label */
  dismissLabel?: string
  /** Additional CSS classes */
  className?: string
}

/**
 * Onboarding tooltip component that uses Popover for click-based dismiss.
 * Positioned relative to an anchor rectangle (from getBoundingClientRect).
 */
export function OnboardingTooltip({
  content,
  onDismiss,
  anchorRect,
  side = 'bottom',
  dismissLabel = 'Verstanden',
  className,
}: OnboardingTooltipProps) {
  const buttonRef = useRef<HTMLButtonElement>(null)

  // Focus the dismiss button when tooltip appears
  useEffect(() => {
    const timer = setTimeout(() => {
      buttonRef.current?.focus()
    }, 100)
    return () => clearTimeout(timer)
  }, [])

  // Handle Escape key to dismiss
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onDismiss()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onDismiss])

  // Create a virtual element for the anchor
  const virtualRef = {
    current: {
      getBoundingClientRect: () => ({
        top: anchorRect.top,
        left: anchorRect.left,
        bottom: anchorRect.bottom,
        right: anchorRect.right,
        width: anchorRect.width,
        height: anchorRect.height,
        x: anchorRect.left,
        y: anchorRect.top,
        toJSON: () => ({}),
      }),
    },
  }

  return (
    <Popover open>
      <PopoverAnchor virtualRef={virtualRef} />
      <PopoverContent
        side={side}
        sideOffset={8}
        className={cn(
          'animate-fade-in w-64 p-4',
          className
        )}
        onOpenAutoFocus={(e) => e.preventDefault()}
      >
        <p className="text-sm mb-3">{content}</p>
        <Button
          ref={buttonRef}
          size="sm"
          onClick={onDismiss}
          className="w-full"
        >
          {dismissLabel}
        </Button>
      </PopoverContent>
    </Popover>
  )
}
