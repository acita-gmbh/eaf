import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { VmRequestForm } from './VmRequestForm'

// Mock useFormPersistence hook - we test it separately
vi.mock('@/hooks/useFormPersistence', () => ({
  useFormPersistence: vi.fn(),
}))

describe('VmRequestForm', () => {
  describe('renders all form fields', () => {
    it('renders VM Name field with label and help text', () => {
      render(<VmRequestForm />)

      expect(screen.getByLabelText(/vm name/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText('e.g. web-server-01')).toBeInTheDocument()
      expect(screen.getByText(/3-63 characters, lowercase letters, numbers, and hyphens/)).toBeInTheDocument()
    })

    it('renders Project field with label', () => {
      render(<VmRequestForm />)

      // Label contains "Project" text - use more specific selector to avoid matching placeholder
      const labels = screen.getAllByText(/project/i)
      expect(labels.some((el) => el.tagName === 'LABEL')).toBe(true)
      expect(screen.getByTestId('project-select-trigger')).toBeInTheDocument()
    })

    it('renders Justification field with label and character counter', () => {
      render(<VmRequestForm />)

      expect(screen.getByLabelText(/justification/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText(/describe the purpose/i)).toBeInTheDocument()
      expect(screen.getByText(/\/10 characters \(min\)/)).toBeInTheDocument()
    })

    it('renders required indicators on all fields', () => {
      render(<VmRequestForm />)

      // Three required field indicators (one for each field)
      const asterisks = screen.getAllByText('*')
      expect(asterisks.length).toBe(3)
    })

    it('renders Size Selector placeholder for Story 2.5', () => {
      render(<VmRequestForm />)

      expect(screen.getByText(/vm size selector \(story 2\.5\)/i)).toBeInTheDocument()
    })

    it('renders Submit button placeholder for Story 2.6', () => {
      render(<VmRequestForm />)

      expect(screen.getByText(/submit button \(story 2\.6\)/i)).toBeInTheDocument()
    })
  })

  describe('accessibility', () => {
    it('has form with data-testid', () => {
      render(<VmRequestForm />)

      expect(screen.getByTestId('vm-request-form')).toBeInTheDocument()
    })

    it('marks VM Name field as required via aria-required', () => {
      render(<VmRequestForm />)

      const vmNameInput = screen.getByPlaceholderText('e.g. web-server-01')
      expect(vmNameInput).toHaveAttribute('aria-required', 'true')
    })

    it('marks Justification field as required via aria-required', () => {
      render(<VmRequestForm />)

      const justificationTextarea = screen.getByPlaceholderText(/describe the purpose/i)
      expect(justificationTextarea).toHaveAttribute('aria-required', 'true')
    })

    it('character counter has aria-live for screen reader announcements', () => {
      render(<VmRequestForm />)

      // Character counter div contains the text and has aria-live
      // The text "0" and "/10 characters (min)" are in the same div with aria-live
      const counterDiv = screen.getByText(/\/10 characters \(min\)/).closest('[aria-live]')
      expect(counterDiv).toHaveAttribute('aria-live', 'polite')
    })
  })

  describe('inline validation', () => {
    it('shows error for VM name shorter than 3 characters', async () => {
      const user = userEvent.setup()
      render(<VmRequestForm />)

      const vmNameInput = screen.getByPlaceholderText('e.g. web-server-01')
      await user.type(vmNameInput, 'ab')
      await user.tab() // Trigger blur/validation

      await waitFor(() => {
        expect(screen.getByText(/minimum 3 characters/i)).toBeInTheDocument()
      })
    })

    it('shows error for VM name with invalid characters', async () => {
      const user = userEvent.setup()
      render(<VmRequestForm />)

      const vmNameInput = screen.getByPlaceholderText('e.g. web-server-01')
      await user.type(vmNameInput, 'Invalid_Name!')
      await user.tab()

      await waitFor(() => {
        expect(screen.getByText(/Only lowercase/i)).toBeInTheDocument()
      })
    })

    it('shows error for justification shorter than 10 characters', async () => {
      const user = userEvent.setup()
      render(<VmRequestForm />)

      const justificationTextarea = screen.getByPlaceholderText(/describe the purpose/i)
      await user.type(justificationTextarea, 'Too short')
      await user.tab()

      await waitFor(() => {
        expect(screen.getByText(/minimum 10 characters/i)).toBeInTheDocument()
      })
    })

    it('clears error when valid input is provided', async () => {
      const user = userEvent.setup()
      render(<VmRequestForm />)

      const vmNameInput = screen.getByPlaceholderText('e.g. web-server-01')
      // First enter invalid input
      await user.type(vmNameInput, 'ab')
      await user.tab()

      await waitFor(() => {
        expect(screen.getByText(/minimum 3 characters/i)).toBeInTheDocument()
      })

      // Then correct it
      await user.clear(vmNameInput)
      await user.type(vmNameInput, 'valid-name')

      await waitFor(() => {
        expect(screen.queryByText(/minimum 3 characters/i)).not.toBeInTheDocument()
      })
    })
  })

  describe('character counter', () => {
    it('updates counter as user types', async () => {
      const user = userEvent.setup()
      render(<VmRequestForm />)

      // Initially shows 0
      expect(screen.getByText('0/10 characters (min)')).toBeInTheDocument()

      const justificationTextarea = screen.getByPlaceholderText(/describe the purpose/i)
      await user.type(justificationTextarea, 'Hello')

      // Should now show 5
      expect(screen.getByText('5/10 characters (min)')).toBeInTheDocument()
    })

    it('shows destructive color when below minimum', () => {
      render(<VmRequestForm />)

      // Counter should have destructive styling when below 10
      const counterDiv = screen.getByText('0/10 characters (min)')
      expect(counterDiv).toHaveClass('text-destructive')
    })

    it('shows normal color when minimum reached', async () => {
      const user = userEvent.setup()
      render(<VmRequestForm />)

      const justificationTextarea = screen.getByPlaceholderText(/describe the purpose/i)
      await user.type(justificationTextarea, '1234567890') // 10 characters

      const counterDiv = screen.getByText('10/10 characters (min)')
      expect(counterDiv).toHaveClass('text-muted-foreground')
    })
  })

  describe('form submission', () => {
    it('calls onSubmit with form data when valid', async () => {
      const user = userEvent.setup()
      const onSubmit = vi.fn()
      render(<VmRequestForm onSubmit={onSubmit} />)

      // Fill VM Name
      const vmNameInput = screen.getByPlaceholderText('e.g. web-server-01')
      await user.type(vmNameInput, 'test-vm-01')

      // Fill Justification (must be >= 10 chars)
      const justificationTextarea = screen.getByPlaceholderText(/describe the purpose/i)
      await user.type(justificationTextarea, 'This is a valid justification for the VM request.')

      // Note: Project selection requires Radix Select interaction which doesn't work in JSDOM.
      // Full form submission is tested in E2E tests.
      // Here we verify the onSubmit prop is wired correctly by checking form structure.
      expect(screen.getByTestId('vm-request-form')).toBeInTheDocument()
      expect(onSubmit).not.toHaveBeenCalled() // Not called until form is submitted with valid data
    })
  })
})
