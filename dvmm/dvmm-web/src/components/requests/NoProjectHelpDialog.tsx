import { useState } from 'react'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { HelpCircle } from 'lucide-react'

/**
 * Help dialog for users who don't have a suitable project
 *
 * Uses the same Popover pattern as OnboardingTooltip for consistency.
 * Dismisses on outside click or Escape key (built into Radix Popover).
 *
 * @see AC #8
 */
export function NoProjectHelpDialog() {
  const [isOpen, setIsOpen] = useState(false)

  return (
    <Popover open={isOpen} onOpenChange={setIsOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          data-testid="no-project-help-trigger"
        >
          <HelpCircle className="h-4 w-4" />
          <span>No suitable project?</span>
        </button>
      </PopoverTrigger>
      <PopoverContent
        className="w-80"
        align="start"
        data-testid="no-project-help-content"
      >
        <div className="space-y-2">
          <h4 className="font-medium">Project access required</h4>
          <p className="text-sm text-muted-foreground">
            Contact your admin to get project access.
            Admins can add you to existing projects or
            create new projects for your team.
          </p>
        </div>
      </PopoverContent>
    </Popover>
  )
}
