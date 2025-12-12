import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@/test/test-utils'
import { CancelConfirmDialog } from './CancelConfirmDialog'

describe('CancelConfirmDialog', () => {
  const defaultProps = {
    open: true,
    onOpenChange: vi.fn(),
    vmName: 'test-vm',
    onConfirm: vi.fn(),
    isPending: false,
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders dialog when open is true', () => {
    render(<CancelConfirmDialog {...defaultProps} />)
    expect(screen.getByTestId('cancel-confirm-dialog')).toBeInTheDocument()
  })

  it('does not render dialog when open is false', () => {
    render(<CancelConfirmDialog {...defaultProps} open={false} />)
    expect(screen.queryByTestId('cancel-confirm-dialog')).not.toBeInTheDocument()
  })

  it('displays dialog title', () => {
    render(<CancelConfirmDialog {...defaultProps} />)
    // Title is rendered in AlertDialogTitle, find it by role
    expect(screen.getByRole('alertdialog')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /Cancel Request/i })).toBeInTheDocument()
  })

  it('displays confirmation message with VM name', () => {
    render(<CancelConfirmDialog {...defaultProps} vmName="my-special-vm" />)
    expect(screen.getByText(/my-special-vm/)).toBeInTheDocument()
    expect(screen.getByText(/Are you sure you want to cancel/)).toBeInTheDocument()
  })

  it('displays warning about irreversibility', () => {
    render(<CancelConfirmDialog {...defaultProps} />)
    expect(screen.getByText(/This action cannot be undone/)).toBeInTheDocument()
  })

  it('renders reason textarea', () => {
    render(<CancelConfirmDialog {...defaultProps} />)
    expect(screen.getByTestId('cancel-reason-input')).toBeInTheDocument()
  })

  it('renders cancel button with "Go Back" text', () => {
    render(<CancelConfirmDialog {...defaultProps} />)
    expect(screen.getByTestId('cancel-dialog-cancel')).toHaveTextContent('Go Back')
  })

  it('renders confirm button with "Cancel Request" text', () => {
    render(<CancelConfirmDialog {...defaultProps} />)
    expect(screen.getByTestId('cancel-dialog-confirm')).toHaveTextContent('Cancel Request')
  })

  describe('confirm action', () => {
    it('calls onConfirm without reason when textarea is empty', () => {
      const onConfirm = vi.fn()
      render(<CancelConfirmDialog {...defaultProps} onConfirm={onConfirm} />)

      fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

      expect(onConfirm).toHaveBeenCalledWith(undefined)
    })

    it('calls onConfirm with reason when textarea has value', () => {
      const onConfirm = vi.fn()
      render(<CancelConfirmDialog {...defaultProps} onConfirm={onConfirm} />)

      fireEvent.change(screen.getByTestId('cancel-reason-input'), {
        target: { value: 'No longer needed' },
      })
      fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

      expect(onConfirm).toHaveBeenCalledWith('No longer needed')
    })

    it('trims whitespace from reason', () => {
      const onConfirm = vi.fn()
      render(<CancelConfirmDialog {...defaultProps} onConfirm={onConfirm} />)

      fireEvent.change(screen.getByTestId('cancel-reason-input'), {
        target: { value: '  Some reason  ' },
      })
      fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

      expect(onConfirm).toHaveBeenCalledWith('Some reason')
    })

    it('treats whitespace-only reason as undefined', () => {
      const onConfirm = vi.fn()
      render(<CancelConfirmDialog {...defaultProps} onConfirm={onConfirm} />)

      fireEvent.change(screen.getByTestId('cancel-reason-input'), {
        target: { value: '   ' },
      })
      fireEvent.click(screen.getByTestId('cancel-dialog-confirm'))

      expect(onConfirm).toHaveBeenCalledWith(undefined)
    })
  })

  describe('cancel action', () => {
    it('calls onOpenChange with false when cancel button clicked', () => {
      const onOpenChange = vi.fn()
      render(<CancelConfirmDialog {...defaultProps} onOpenChange={onOpenChange} />)

      fireEvent.click(screen.getByTestId('cancel-dialog-cancel'))

      expect(onOpenChange).toHaveBeenCalledWith(false)
    })
  })

  describe('pending state', () => {
    it('shows "Cancelling..." text when isPending', () => {
      render(<CancelConfirmDialog {...defaultProps} isPending={true} />)
      expect(screen.getByTestId('cancel-dialog-confirm')).toHaveTextContent('Cancelling...')
    })

    it('disables confirm button when isPending', () => {
      render(<CancelConfirmDialog {...defaultProps} isPending={true} />)
      expect(screen.getByTestId('cancel-dialog-confirm')).toBeDisabled()
    })

    it('disables cancel button when isPending', () => {
      render(<CancelConfirmDialog {...defaultProps} isPending={true} />)
      expect(screen.getByTestId('cancel-dialog-cancel')).toBeDisabled()
    })

    it('disables textarea when isPending', () => {
      render(<CancelConfirmDialog {...defaultProps} isPending={true} />)
      expect(screen.getByTestId('cancel-reason-input')).toBeDisabled()
    })
  })

  describe('dialog close behavior', () => {
    it('calls onOpenChange with empty reason when dismissed', () => {
      const onOpenChange = vi.fn()
      render(<CancelConfirmDialog {...defaultProps} onOpenChange={onOpenChange} />)

      // Enter a reason
      fireEvent.change(screen.getByTestId('cancel-reason-input'), {
        target: { value: 'Some reason' },
      })

      // Click cancel button to dismiss
      fireEvent.click(screen.getByTestId('cancel-dialog-cancel'))

      // onOpenChange should be called with false
      expect(onOpenChange).toHaveBeenCalledWith(false)
    })

    it('reason input starts empty on fresh dialog open', () => {
      render(<CancelConfirmDialog {...defaultProps} />)
      // Fresh dialog should have empty reason
      expect(screen.getByTestId('cancel-reason-input')).toHaveValue('')
    })
  })
})
