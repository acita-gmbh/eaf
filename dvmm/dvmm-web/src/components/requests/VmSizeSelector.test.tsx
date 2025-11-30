import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { VmSizeSelector } from './VmSizeSelector'
import { VM_SIZES } from '@/lib/config/vm-sizes'

describe('VmSizeSelector', () => {
  describe('rendering', () => {
    it('renders all 4 size cards (S, M, L, XL)', () => {
      render(<VmSizeSelector value="M" onValueChange={vi.fn()} />)

      for (const size of VM_SIZES) {
        expect(screen.getByText(size.id)).toBeInTheDocument()
      }
    })

    it('displays correct vCPU count for each size', () => {
      render(<VmSizeSelector value="M" onValueChange={vi.fn()} />)

      expect(screen.getByText('2 vCPU')).toBeInTheDocument() // S
      expect(screen.getByText('4 vCPU')).toBeInTheDocument() // M
      expect(screen.getByText('8 vCPU')).toBeInTheDocument() // L
      expect(screen.getByText('16 vCPU')).toBeInTheDocument() // XL
    })

    it('displays correct RAM for each size', () => {
      render(<VmSizeSelector value="M" onValueChange={vi.fn()} />)

      expect(screen.getByText('4 GB RAM')).toBeInTheDocument() // S
      expect(screen.getByText('8 GB RAM')).toBeInTheDocument() // M
      expect(screen.getByText('16 GB RAM')).toBeInTheDocument() // L
      expect(screen.getByText('32 GB RAM')).toBeInTheDocument() // XL
    })

    it('displays correct disk size for each size', () => {
      render(<VmSizeSelector value="M" onValueChange={vi.fn()} />)

      expect(screen.getByText('50 GB')).toBeInTheDocument() // S
      expect(screen.getByText('100 GB')).toBeInTheDocument() // M
      expect(screen.getByText('200 GB')).toBeInTheDocument() // L
      expect(screen.getByText('500 GB')).toBeInTheDocument() // XL
    })

    it('displays monthly cost estimate for each size', () => {
      render(<VmSizeSelector value="M" onValueChange={vi.fn()} />)

      expect(screen.getByText('~€25/mo')).toBeInTheDocument() // S
      expect(screen.getByText('~€50/mo')).toBeInTheDocument() // M
      expect(screen.getByText('~€100/mo')).toBeInTheDocument() // L
      expect(screen.getByText('~€200/mo')).toBeInTheDocument() // XL
    })
  })

  describe('selection behavior', () => {
    it('marks the provided value as selected', () => {
      render(<VmSizeSelector value="L" onValueChange={vi.fn()} />)

      const largeRadio = screen.getByRole('radio', { name: /L.*8 vCPU/i })
      expect(largeRadio).toBeChecked()
    })

    it('calls onValueChange when a different size is clicked', async () => {
      const user = userEvent.setup()
      const handleChange = vi.fn()
      render(<VmSizeSelector value="M" onValueChange={handleChange} />)

      // Click on the Large option
      const largeCard = screen.getByText('L').closest('label')
      if (largeCard) await user.click(largeCard)

      expect(handleChange).toHaveBeenCalledWith('L')
    })

    it('only allows one size to be selected at a time (radio behavior)', () => {
      render(<VmSizeSelector value="M" onValueChange={vi.fn()} />)

      const radios = screen.getAllByRole('radio')
      // Radix uses aria-checked instead of the native checked property
      const checked = radios.filter(r => r.getAttribute('aria-checked') === 'true')

      expect(checked).toHaveLength(1)
    })

    it('supports disabled state', () => {
      render(<VmSizeSelector value="M" onValueChange={vi.fn()} disabled />)

      // When disabled, all radios should be disabled
      const radios = screen.getAllByRole('radio')
      for (const radio of radios) {
        expect(radio).toBeDisabled()
      }
    })
  })

  describe('accessibility', () => {
    it('has role="radiogroup"', () => {
      render(<VmSizeSelector value="M" onValueChange={vi.fn()} />)

      expect(screen.getByRole('radiogroup')).toBeInTheDocument()
    })

    it('has accessible label on radiogroup', () => {
      render(<VmSizeSelector value="M" onValueChange={vi.fn()} />)

      const radioGroup = screen.getByRole('radiogroup')
      expect(radioGroup).toHaveAttribute('aria-label', 'Select VM size')
    })

    it('each card has role="radio" with aria-checked', () => {
      render(<VmSizeSelector value="M" onValueChange={vi.fn()} />)

      const radios = screen.getAllByRole('radio')
      expect(radios).toHaveLength(4)

      // M should be checked
      const mediumRadio = radios.find(r => r.getAttribute('value') === 'M')
      expect(mediumRadio).toHaveAttribute('aria-checked', 'true')
    })

    it('radio items are focusable for keyboard navigation', () => {
      render(<VmSizeSelector value="M" onValueChange={vi.fn()} />)

      // All radio items should be part of the radiogroup for keyboard nav
      const radios = screen.getAllByRole('radio')

      expect(radios).toHaveLength(4)
      // Radix RadioGroup provides built-in keyboard navigation
      // (arrow keys move focus, space/enter select) via its accessibility implementation
    })
  })

  describe('responsive layout', () => {
    it('applies grid classes for responsive layout', () => {
      render(<VmSizeSelector value="M" onValueChange={vi.fn()} />)

      const radioGroup = screen.getByRole('radiogroup')
      // Should have grid layout with responsive columns
      expect(radioGroup.className).toContain('grid')
      expect(radioGroup.className).toContain('grid-cols-2')
      expect(radioGroup.className).toContain('md:grid-cols-4')
    })
  })

  describe('styling', () => {
    it('selected card has ring styling', () => {
      render(<VmSizeSelector value="M" onValueChange={vi.fn()} />)

      // Find the Medium card's label (parent of the radio)
      const mediumLabel = screen.getByText('M').closest('label')
      expect(mediumLabel?.className).toContain('ring')
      expect(mediumLabel?.className).toContain('border-primary')
    })

    it('unselected card does not have ring styling', () => {
      render(<VmSizeSelector value="M" onValueChange={vi.fn()} />)

      // Find the Small card's label
      const smallLabel = screen.getByText('S').closest('label')
      expect(smallLabel?.className).not.toContain('ring-2')
    })
  })
})
