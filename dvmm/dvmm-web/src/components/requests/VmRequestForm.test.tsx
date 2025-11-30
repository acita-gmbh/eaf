import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { VmRequestForm } from './VmRequestForm'

// Mock useFormPersistence hook - we test it separately
vi.mock('@/hooks/useFormPersistence', () => ({
  useFormPersistence: vi.fn(),
}))

describe('VmRequestForm', () => {
  describe('renders all form fields', () => {
    it('renders VM Name field with label and help text', () => {
      render(<VmRequestForm />)

      expect(screen.getByLabelText(/vm-name/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText('z.B. web-server-01')).toBeInTheDocument()
      expect(screen.getByText(/3-63 Zeichen, Kleinbuchstaben, Zahlen und Bindestriche/)).toBeInTheDocument()
    })

    it('renders Project field with label', () => {
      render(<VmRequestForm />)

      // Label contains "Projekt" text - use more specific selector to avoid matching placeholder
      const labels = screen.getAllByText(/projekt/i)
      expect(labels.some((el) => el.tagName === 'LABEL')).toBe(true)
      expect(screen.getByTestId('project-select-trigger')).toBeInTheDocument()
    })

    it('renders Justification field with label and character counter', () => {
      render(<VmRequestForm />)

      expect(screen.getByLabelText(/begründung/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText(/beschreiben sie den zweck/i)).toBeInTheDocument()
      expect(screen.getByText(/\/10 Zeichen \(min\)/)).toBeInTheDocument()
    })

    it('renders required indicators on all fields', () => {
      render(<VmRequestForm />)

      // Three required field indicators (one for each field)
      const asterisks = screen.getAllByText('*')
      expect(asterisks.length).toBe(3)
    })

    it('renders Size Selector placeholder for Story 2.5', () => {
      render(<VmRequestForm />)

      expect(screen.getByText(/vm-größe auswahl \(story 2\.5\)/i)).toBeInTheDocument()
    })

    it('renders Submit button placeholder for Story 2.6', () => {
      render(<VmRequestForm />)

      expect(screen.getByText(/absenden button \(story 2\.6\)/i)).toBeInTheDocument()
    })
  })

  describe('accessibility', () => {
    it('has form with data-testid', () => {
      render(<VmRequestForm />)

      expect(screen.getByTestId('vm-request-form')).toBeInTheDocument()
    })

    it('marks VM Name field as required via aria-required', () => {
      render(<VmRequestForm />)

      const vmNameInput = screen.getByPlaceholderText('z.B. web-server-01')
      expect(vmNameInput).toHaveAttribute('aria-required', 'true')
    })

    it('marks Justification field as required via aria-required', () => {
      render(<VmRequestForm />)

      const justificationTextarea = screen.getByPlaceholderText(/beschreiben sie den zweck/i)
      expect(justificationTextarea).toHaveAttribute('aria-required', 'true')
    })

    it('character counter has aria-live for screen reader announcements', () => {
      render(<VmRequestForm />)

      // Character counter div contains the text and has aria-live
      // The text "0" and "/10 Zeichen (min)" are in the same div with aria-live
      const counterDiv = screen.getByText(/\/10 Zeichen \(min\)/).closest('[aria-live]')
      expect(counterDiv).toHaveAttribute('aria-live', 'polite')
    })
  })
})
