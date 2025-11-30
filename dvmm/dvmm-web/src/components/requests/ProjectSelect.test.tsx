import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ProjectSelect } from './ProjectSelect'

// Note: Radix UI Select doesn't work well with user-event in JSDOM
// due to missing pointer capture APIs. Interaction tests are in E2E (Playwright).
// This includes callback tests - verifying onValueChange is called when selecting a project
// is covered by E2E tests in e2e/requests.spec.ts

// Mock data reference:
// proj-1: "Entwicklung", 5/10 VMs (50%)
// proj-2: "Produktion", 8/10 VMs (80%) - at threshold, not warning
// proj-3: "Testing", 0/5 VMs (0%)
// proj-4: "Legacy", 9/10 VMs (90%) - warning state
// proj-5: "Archiv", 0/0 VMs (0%) - zero quota, exhausted

describe('ProjectSelect', () => {
  describe('rendering', () => {
    it('renders select trigger with placeholder when no value', () => {
      render(<ProjectSelect value="" onValueChange={vi.fn()} />)

      expect(screen.getByTestId('project-select-trigger')).toBeInTheDocument()
      expect(screen.getByText('Projekt auswählen...')).toBeInTheDocument()
    })

    it('renders help link for no project', () => {
      render(<ProjectSelect value="" onValueChange={vi.fn()} />)

      expect(screen.getByText('Kein passendes Projekt?')).toBeInTheDocument()
      expect(screen.getByTestId('no-project-help-trigger')).toBeInTheDocument()
    })

    it('renders aria-required attribute', () => {
      render(<ProjectSelect value="" onValueChange={vi.fn()} />)

      expect(screen.getByTestId('project-select-trigger')).toHaveAttribute('aria-required', 'true')
    })

    it('does not show quota info when no project selected', () => {
      render(<ProjectSelect value="" onValueChange={vi.fn()} />)

      expect(screen.queryByTestId('project-quota-display')).not.toBeInTheDocument()
    })
  })

  describe('with selected project', () => {
    it('displays selected project name in trigger', () => {
      render(<ProjectSelect value="proj-1" onValueChange={vi.fn()} />)

      // When value is set, the trigger should show the project name
      expect(screen.getByTestId('project-select-trigger')).toHaveTextContent('Entwicklung')
    })

    it('shows quota display when project is selected', () => {
      render(<ProjectSelect value="proj-1" onValueChange={vi.fn()} />)

      // Quota display section should appear
      expect(screen.getByTestId('project-quota-display')).toBeInTheDocument()
    })

    it('shows VM availability count', () => {
      render(<ProjectSelect value="proj-1" onValueChange={vi.fn()} />)

      // Entwicklung has 5 used of 10 total, so 5 available
      expect(screen.getByText(/Verfügbar: 5 von 10 VMs/)).toBeInTheDocument()
    })

    it('shows progress bar with correct label', () => {
      render(<ProjectSelect value="proj-1" onValueChange={vi.fn()} />)

      expect(screen.getByRole('progressbar')).toBeInTheDocument()
      expect(screen.getByLabelText(/Quota usage: 5 of 10 VMs used/)).toBeInTheDocument()
    })

    it('has normal styling when quota is below 80%', () => {
      render(<ProjectSelect value="proj-1" onValueChange={vi.fn()} />)

      // Entwicklung is at 50% - should have normal styling
      const availText = screen.getByText(/Verfügbar: 5 von 10 VMs/)
      expect(availText).toHaveClass('text-muted-foreground')
    })

    it('has normal styling when quota is exactly at 80%', () => {
      render(<ProjectSelect value="proj-2" onValueChange={vi.fn()} />)

      // Produktion is at exactly 80% - should NOT have warning (>80% required)
      const availText = screen.getByText(/Verfügbar: 2 von 10 VMs/)
      expect(availText).toHaveClass('text-muted-foreground')
    })

    it('has warning styling when quota exceeds 80%', () => {
      render(<ProjectSelect value="proj-4" onValueChange={vi.fn()} />)

      // Legacy is at 90% - should have warning styling
      const availText = screen.getByText(/Verfügbar: 1 von 10 VMs/)
      expect(availText).toHaveClass('text-orange-600')
    })

    it('handles zero-quota projects without division by zero', () => {
      render(<ProjectSelect value="proj-5" onValueChange={vi.fn()} />)

      // Archiv has 0/0 VMs - should show warning (treated as 100% used)
      const availText = screen.getByText(/Verfügbar: 0 von 0 VMs/)
      expect(availText).toHaveClass('text-orange-600')
      // Progress bar should be at 100%
      expect(screen.getByRole('progressbar')).toBeInTheDocument()
    })
  })

  describe('accessibility', () => {
    it('has combobox role', () => {
      render(<ProjectSelect value="" onValueChange={vi.fn()} />)

      expect(screen.getByRole('combobox')).toBeInTheDocument()
    })
  })
})
