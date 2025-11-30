import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { NoProjectHelpDialog } from './NoProjectHelpDialog'

describe('NoProjectHelpDialog', () => {
  describe('rendering', () => {
    it('renders trigger button with help text', () => {
      render(<NoProjectHelpDialog />)

      expect(screen.getByText('Kein passendes Projekt?')).toBeInTheDocument()
      expect(screen.getByTestId('no-project-help-trigger')).toBeInTheDocument()
    })

    it('renders help icon', () => {
      render(<NoProjectHelpDialog />)

      const trigger = screen.getByTestId('no-project-help-trigger')
      expect(trigger.querySelector('svg')).toBeInTheDocument()
    })
  })

  describe('interaction', () => {
    it('opens popover when trigger is clicked', async () => {
      const user = userEvent.setup()
      render(<NoProjectHelpDialog />)

      // Popover content should not be visible initially
      expect(screen.queryByText('Projektzugang benötigt')).not.toBeInTheDocument()

      // Click the trigger
      await user.click(screen.getByTestId('no-project-help-trigger'))

      // Popover content should now be visible
      expect(screen.getByText('Projektzugang benötigt')).toBeInTheDocument()
      expect(screen.getByText(/Kontaktieren Sie Ihren Admin/)).toBeInTheDocument()
    })

    it('shows admin contact info in popover', async () => {
      const user = userEvent.setup()
      render(<NoProjectHelpDialog />)

      await user.click(screen.getByTestId('no-project-help-trigger'))

      expect(screen.getByText(/Admins können Sie zu bestehenden Projekten hinzufügen/)).toBeInTheDocument()
    })

    it('closes popover when Escape is pressed', async () => {
      const user = userEvent.setup()
      render(<NoProjectHelpDialog />)

      // Open the popover
      await user.click(screen.getByTestId('no-project-help-trigger'))
      expect(screen.getByText('Projektzugang benötigt')).toBeInTheDocument()

      // Press Escape to close
      await user.keyboard('{Escape}')

      // Popover should be closed (content not visible)
      expect(screen.queryByText('Projektzugang benötigt')).not.toBeInTheDocument()
    })
  })

  describe('accessibility', () => {
    it('trigger has correct aria attributes', () => {
      render(<NoProjectHelpDialog />)

      const trigger = screen.getByTestId('no-project-help-trigger')
      expect(trigger).toHaveAttribute('aria-haspopup', 'dialog')
    })

    it('popover content has dialog role when open', async () => {
      const user = userEvent.setup()
      render(<NoProjectHelpDialog />)

      await user.click(screen.getByTestId('no-project-help-trigger'))

      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })
  })
})
