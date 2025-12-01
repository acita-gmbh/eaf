import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@/test/test-utils'
import userEvent from '@testing-library/user-event'
import { VmRequestForm } from './VmRequestForm'
import { DEFAULT_VM_SIZE } from '@/lib/config/vm-sizes'

// Mock useFormPersistence hook
vi.mock('@/hooks/useFormPersistence', () => ({
  useFormPersistence: vi.fn(),
}))

// Mock useCreateVmRequest hook
vi.mock('@/hooks/useCreateVmRequest', () => ({
  useCreateVmRequest: () => ({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  }),
}))

describe('VmRequestForm Integration', () => {
  describe('size selector integration', () => {
    it('integrates VmSizeSelector with form state', async () => {
      const user = userEvent.setup()
      render(<VmRequestForm />)

      // Default selection should be M
      const mediumRadio = screen.getByRole('radio', { name: /medium.*4 vcpu/i })
      expect(mediumRadio).toHaveAttribute('aria-checked', 'true')

      // Click on Large
      const largeLabel = screen.getByText('L').closest('label')!
      await user.click(largeLabel)

      // Large should now be selected
      await waitFor(() => {
        const largeRadio = screen.getByRole('radio', { name: /large.*8 vcpu/i })
        expect(largeRadio).toHaveAttribute('aria-checked', 'true')
      })

      // Medium should no longer be selected
      expect(mediumRadio).toHaveAttribute('aria-checked', 'false')
    })

    it('default size matches DEFAULT_VM_SIZE constant', () => {
      render(<VmRequestForm />)

      // DEFAULT_VM_SIZE is 'M'
      expect(DEFAULT_VM_SIZE).toBe('M')

      const mediumRadio = screen.getByRole('radio', { name: /medium.*4 vcpu/i })
      expect(mediumRadio).toHaveAttribute('aria-checked', 'true')
    })

    it('all 4 sizes are selectable', async () => {
      const user = userEvent.setup()
      render(<VmRequestForm />)

      const sizes = ['S', 'M', 'L', 'XL']

      for (const sizeId of sizes) {
        const label = screen.getByText(sizeId).closest('label')!
        await user.click(label)

        await waitFor(() => {
          // Find radio by value to avoid name complexity
          const radios = screen.getAllByRole('radio')
          const selectedRadio = radios.find(r => r.getAttribute('value') === sizeId)
          expect(selectedRadio).toHaveAttribute('aria-checked', 'true')
        })
      }
    })
  })

  describe('quota display integration', () => {
    it('does not show quota info when no project selected', () => {
      render(<VmRequestForm />)

      // Quota info should not be visible without project
      expect(screen.queryByText(/Available:.*VMs/)).not.toBeInTheDocument()
    })

    // Note: Full project selection + quota display integration requires
    // Radix Select which is difficult to test in JSDOM.
    // This is covered in E2E tests with Playwright.
  })

  describe('form validation with size', () => {
    it('form has size field in initial state', () => {
      render(<VmRequestForm />)

      // Size selector radiogroup should be present
      expect(screen.getByRole('radiogroup', { name: /select vm size/i })).toBeInTheDocument()

      // All 4 radios present
      expect(screen.getAllByRole('radio')).toHaveLength(4)
    })

    it('always has one size selected (RadioGroup prevents empty state)', () => {
      // Note: In practice, size can't be cleared via UI since one radio is always selected.
      // RadioGroup enforces single selection - there's no way to deselect all options.
      // This test verifies the constraint is maintained at the form level.

      render(<VmRequestForm />)

      const radios = screen.getAllByRole('radio')
      const checkedRadios = radios.filter(r => r.getAttribute('aria-checked') === 'true')

      // Exactly one size should always be selected
      expect(checkedRadios).toHaveLength(1)
    })
  })

  describe('size field state management', () => {
    it('updates size field state when selecting L', async () => {
      const user = userEvent.setup()
      const onSubmit = vi.fn()
      render(<VmRequestForm onSubmit={onSubmit} />)

      // Select Large size
      const largeLabel = screen.getByText('L').closest('label')!
      await user.click(largeLabel)

      // Verify the form field is updated (radiogroup state)
      await waitFor(() => {
        const largeRadio = screen.getByRole('radio', { name: /large.*8 vcpu/i })
        expect(largeRadio).toHaveAttribute('aria-checked', 'true')
      })

      // The form data would include size: 'L' when submitted
      // Full form submission test with all fields is done in E2E
    })
  })

  describe('accessibility integration', () => {
    it('size selector has proper ARIA labels', () => {
      render(<VmRequestForm />)

      // Radiogroup has label
      const radioGroup = screen.getByRole('radiogroup', { name: /select vm size/i })
      expect(radioGroup).toBeInTheDocument()

      // Each radio item has descriptive label
      const radios = screen.getAllByRole('radio')
      for (const radio of radios) {
        expect(radio).toHaveAttribute('aria-label')
      }
    })

    it('form field has required label indicator', () => {
      render(<VmRequestForm />)

      // Find the VM Size label
      const sizeLabel = screen.getByText(/vm size/i)
      expect(sizeLabel).toBeInTheDocument()

      // Check for required asterisk
      const asterisks = screen.getAllByText('*')
      expect(asterisks.length).toBeGreaterThanOrEqual(4) // VM Name, Project, Justification, Size
    })
  })

  describe('responsive layout', () => {
    it('size selector uses responsive grid classes', () => {
      render(<VmRequestForm />)

      const radioGroup = screen.getByRole('radiogroup', { name: /select vm size/i })
      expect(radioGroup.className).toContain('grid-cols-2')
      expect(radioGroup.className).toContain('md:grid-cols-4')
    })
  })
})
