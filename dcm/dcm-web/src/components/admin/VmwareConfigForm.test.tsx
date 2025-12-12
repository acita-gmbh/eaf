import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@/test/test-utils'
import userEvent from '@testing-library/user-event'
import { VmwareConfigForm } from './VmwareConfigForm'

// Mock sonner toast
const mockToast = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
}))
vi.mock('sonner', () => ({
  toast: mockToast,
}))

// Mock useVmwareConfig hook with controllable behavior
const mockUseVmwareConfig = vi.hoisted(() =>
  vi.fn(() => ({
    data: null,
    isLoading: false,
    isError: false,
    error: null,
  }))
)

// Mock useSaveVmwareConfig hook
const mockSaveMutate = vi.hoisted(() => vi.fn())
const mockUseSaveVmwareConfig = vi.hoisted(() =>
  vi.fn(() => ({
    mutate: mockSaveMutate,
    isPending: false,
    isError: false,
    error: null,
  }))
)

// Mock useTestVmwareConnection hook
const mockTestMutate = vi.hoisted(() => vi.fn())
const mockUseTestVmwareConnection = vi.hoisted(() =>
  vi.fn(() => ({
    mutate: mockTestMutate,
    isPending: false,
    isError: false,
    error: null,
  }))
)

vi.mock('@/hooks/useVmwareConfig', () => ({
  useVmwareConfig: mockUseVmwareConfig,
  useSaveVmwareConfig: mockUseSaveVmwareConfig,
  useTestVmwareConnection: mockUseTestVmwareConnection,
}))

// Mock isConnectionTestError
vi.mock('@/api/vmware-config', () => ({
  isConnectionTestError: (result: { success: boolean }) => !result.success,
}))

describe('VmwareConfigForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockUseVmwareConfig.mockReturnValue({
      data: null,
      isLoading: false,
      isError: false,
      error: null,
    })
    mockUseSaveVmwareConfig.mockReturnValue({
      mutate: mockSaveMutate,
      isPending: false,
      isError: false,
      error: null,
    })
    mockUseTestVmwareConnection.mockReturnValue({
      mutate: mockTestMutate,
      isPending: false,
      isError: false,
      error: null,
    })
  })

  describe('renders all form fields', () => {
    it('renders vCenter URL field with label and help text', () => {
      render(<VmwareConfigForm />)

      expect(screen.getByLabelText(/vcenter url/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText('https://vcenter.example.com/sdk')).toBeInTheDocument()
      expect(screen.getByText(/must use HTTPS/i)).toBeInTheDocument()
    })

    it('renders Username field with label', () => {
      render(<VmwareConfigForm />)

      expect(screen.getByLabelText(/username/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText('administrator@vsphere.local')).toBeInTheDocument()
    })

    it('renders Password field with label', () => {
      render(<VmwareConfigForm />)

      expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
      const passwordInput = screen.getByPlaceholderText('Enter password')
      expect(passwordInput).toHaveAttribute('type', 'password')
    })

    it('renders Datacenter field with label', () => {
      render(<VmwareConfigForm />)

      expect(screen.getByLabelText(/datacenter/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText('Datacenter1')).toBeInTheDocument()
    })

    it('renders Cluster field with label', () => {
      render(<VmwareConfigForm />)

      expect(screen.getByLabelText(/cluster/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText('Cluster1')).toBeInTheDocument()
    })

    it('renders Datastore field with label', () => {
      render(<VmwareConfigForm />)

      expect(screen.getByLabelText(/datastore/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText('datastore1')).toBeInTheDocument()
    })

    it('renders Network field with label', () => {
      render(<VmwareConfigForm />)

      expect(screen.getByLabelText(/network/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText('VM Network')).toBeInTheDocument()
    })

    it('renders optional VM Template field', () => {
      render(<VmwareConfigForm />)

      expect(screen.getByLabelText(/vm template/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText('ubuntu-22.04-template')).toBeInTheDocument()
    })

    it('renders optional VM Folder Path field', () => {
      render(<VmwareConfigForm />)

      expect(screen.getByLabelText(/vm folder path/i)).toBeInTheDocument()
      expect(screen.getByPlaceholderText('/DCM/Production')).toBeInTheDocument()
    })

    it('renders required indicators on mandatory fields', () => {
      render(<VmwareConfigForm />)

      // Seven required field indicators (vCenter URL, Username, Password, Datacenter, Cluster, Datastore, Network)
      const asterisks = screen.getAllByText('*')
      expect(asterisks.length).toBe(7)
    })
  })

  describe('action buttons', () => {
    it('renders Test Connection button', () => {
      render(<VmwareConfigForm />)

      expect(screen.getByTestId('test-connection-button')).toBeInTheDocument()
      expect(screen.getByText('Test Connection')).toBeInTheDocument()
    })

    it('renders Save Configuration button for new config', () => {
      render(<VmwareConfigForm />)

      expect(screen.getByTestId('save-button')).toBeInTheDocument()
      expect(screen.getByText('Save Configuration')).toBeInTheDocument()
    })

    it('save button is disabled when password not provided for new config', () => {
      render(<VmwareConfigForm />)

      const saveButton = screen.getByTestId('save-button')
      expect(saveButton).toBeDisabled()
    })
  })

  describe('loading state', () => {
    it('shows loading indicator when fetching config', () => {
      mockUseVmwareConfig.mockReturnValue({
        data: null,
        isLoading: true,
        isError: false,
        error: null,
      })

      render(<VmwareConfigForm />)

      // Should not show form when loading
      expect(screen.queryByTestId('vmware-config-form')).not.toBeInTheDocument()
    })
  })

  describe('existing configuration', () => {
    const existingConfig = {
      id: 'config-123',
      vcenterUrl: 'https://vcenter.example.com',
      username: 'admin@vsphere.local',
      hasPassword: true,
      datacenterName: 'DC1',
      clusterName: 'Cluster1',
      datastoreName: 'Datastore1',
      networkName: 'VM Network',
      templateName: 'ubuntu-22.04-template',
      folderPath: '/VMs/DCM',
      verifiedAt: '2024-01-15T10:00:00Z',
      createdAt: '2024-01-01T09:00:00Z',
      updatedAt: '2024-01-15T10:00:00Z',
      createdBy: 'user-1',
      updatedBy: 'user-1',
      version: 1,
    }

    it('renders Update Configuration button for existing config', () => {
      mockUseVmwareConfig.mockReturnValue({
        data: existingConfig,
        isLoading: false,
        isError: false,
        error: null,
      })

      render(<VmwareConfigForm />)

      expect(screen.getByText('Update Configuration')).toBeInTheDocument()
    })

    it('shows Last verified timestamp for existing config', () => {
      mockUseVmwareConfig.mockReturnValue({
        data: existingConfig,
        isLoading: false,
        isError: false,
        error: null,
      })

      render(<VmwareConfigForm />)

      expect(screen.getByText(/last verified:/i)).toBeInTheDocument()
    })

    it('password field shows placeholder for update mode', () => {
      mockUseVmwareConfig.mockReturnValue({
        data: existingConfig,
        isLoading: false,
        isError: false,
        error: null,
      })

      render(<VmwareConfigForm />)

      const passwordInput = screen.getByLabelText(/password/i)
      expect(passwordInput).toHaveAttribute('placeholder', '••••••••')
    })

    it('save button is enabled without password for update mode', () => {
      mockUseVmwareConfig.mockReturnValue({
        data: existingConfig,
        isLoading: false,
        isError: false,
        error: null,
      })

      render(<VmwareConfigForm />)

      const saveButton = screen.getByTestId('save-button')
      expect(saveButton).not.toBeDisabled()
    })
  })

  describe('form validation', () => {
    it('shows error when vCenter URL does not start with https://', async () => {
      const user = userEvent.setup()
      render(<VmwareConfigForm />)

      const vcenterInput = screen.getByPlaceholderText('https://vcenter.example.com/sdk')
      await user.type(vcenterInput, 'http://vcenter.example.com')
      await user.tab() // Trigger validation

      await waitFor(() => {
        expect(screen.getByText(/must start with https/i)).toBeInTheDocument()
      })
    })

    it('shows error when username exceeds max length', async () => {
      const user = userEvent.setup()
      render(<VmwareConfigForm />)

      const usernameInput = screen.getByPlaceholderText('administrator@vsphere.local')
      await user.type(usernameInput, 'a'.repeat(256))
      await user.tab()

      await waitFor(() => {
        expect(screen.getByText(/maximum 255 characters/i)).toBeInTheDocument()
      })
    })
  })

  describe('test connection', () => {
    it('shows error toast when required fields are missing', async () => {
      const user = userEvent.setup()
      render(<VmwareConfigForm />)

      const testButton = screen.getByTestId('test-connection-button')
      await user.click(testButton)

      expect(mockToast.error).toHaveBeenCalledWith(
        'Missing fields',
        expect.objectContaining({
          description: 'Please fill in all required fields before testing.',
        })
      )
    })

    it('shows error toast when password not provided for new config', async () => {
      const user = userEvent.setup()
      render(<VmwareConfigForm />)

      // Fill required fields
      await user.type(screen.getByPlaceholderText('https://vcenter.example.com/sdk'), 'https://vcenter.test.com')
      await user.type(screen.getByPlaceholderText('administrator@vsphere.local'), 'admin@test.local')
      await user.type(screen.getByPlaceholderText('Datacenter1'), 'DC1')
      await user.type(screen.getByPlaceholderText('Cluster1'), 'Cluster1')
      await user.type(screen.getByPlaceholderText('datastore1'), 'DS1')
      await user.type(screen.getByPlaceholderText('VM Network'), 'Network1')

      const testButton = screen.getByTestId('test-connection-button')
      await user.click(testButton)

      expect(mockToast.error).toHaveBeenCalledWith(
        'Password required',
        expect.objectContaining({
          description: 'Please enter a password to test the connection.',
        })
      )
    })

    it('calls testMutation with correct payload', async () => {
      const user = userEvent.setup()
      render(<VmwareConfigForm />)

      // Fill all required fields including password
      await user.type(screen.getByPlaceholderText('https://vcenter.example.com/sdk'), 'https://vcenter.test.com')
      await user.type(screen.getByPlaceholderText('administrator@vsphere.local'), 'admin@test.local')
      await user.type(screen.getByPlaceholderText('Enter password'), 'secret123')
      await user.type(screen.getByPlaceholderText('Datacenter1'), 'DC1')
      await user.type(screen.getByPlaceholderText('Cluster1'), 'Cluster1')
      await user.type(screen.getByPlaceholderText('datastore1'), 'DS1')
      await user.type(screen.getByPlaceholderText('VM Network'), 'Network1')

      const testButton = screen.getByTestId('test-connection-button')
      await user.click(testButton)

      expect(mockTestMutate).toHaveBeenCalledWith(
        expect.objectContaining({
          vcenterUrl: 'https://vcenter.test.com',
          username: 'admin@test.local',
          password: 'secret123',
          datacenterName: 'DC1',
          clusterName: 'Cluster1',
          datastoreName: 'DS1',
          networkName: 'Network1',
        }),
        expect.any(Object)
      )
    })

    it('shows Testing... text when test is in progress', () => {
      mockUseTestVmwareConnection.mockReturnValue({
        mutate: mockTestMutate,
        isPending: true,
        isError: false,
        error: null,
      })

      render(<VmwareConfigForm />)

      expect(screen.getByText('Testing...')).toBeInTheDocument()
    })
  })

  describe('save configuration', () => {
    it('calls saveMutation with correct payload on submit', async () => {
      const user = userEvent.setup()
      render(<VmwareConfigForm />)

      // Fill all required fields
      await user.type(screen.getByPlaceholderText('https://vcenter.example.com/sdk'), 'https://vcenter.test.com')
      await user.type(screen.getByPlaceholderText('administrator@vsphere.local'), 'admin@test.local')
      await user.type(screen.getByPlaceholderText('Enter password'), 'secret123')
      await user.type(screen.getByPlaceholderText('Datacenter1'), 'DC1')
      await user.type(screen.getByPlaceholderText('Cluster1'), 'Cluster1')
      await user.type(screen.getByPlaceholderText('datastore1'), 'DS1')
      await user.type(screen.getByPlaceholderText('VM Network'), 'Network1')

      const saveButton = screen.getByTestId('save-button')
      await user.click(saveButton)

      expect(mockSaveMutate).toHaveBeenCalledWith(
        expect.objectContaining({
          vcenterUrl: 'https://vcenter.test.com',
          username: 'admin@test.local',
          password: 'secret123',
          datacenterName: 'DC1',
          clusterName: 'Cluster1',
          datastoreName: 'DS1',
          networkName: 'Network1',
          version: null,
        }),
        expect.any(Object)
      )
    })

    it('shows Saving... text when save is in progress', () => {
      mockUseSaveVmwareConfig.mockReturnValue({
        mutate: mockSaveMutate,
        isPending: true,
        isError: false,
        error: null,
      })

      render(<VmwareConfigForm />)

      expect(screen.getByText('Saving...')).toBeInTheDocument()
    })
  })

  describe('accessibility', () => {
    it('has form with data-testid', () => {
      render(<VmwareConfigForm />)

      expect(screen.getByTestId('vmware-config-form')).toBeInTheDocument()
    })

    it('marks required fields with aria-required', () => {
      render(<VmwareConfigForm />)

      const vcenterInput = screen.getByPlaceholderText('https://vcenter.example.com/sdk')
      expect(vcenterInput).toHaveAttribute('aria-required', 'true')

      const usernameInput = screen.getByPlaceholderText('administrator@vsphere.local')
      expect(usernameInput).toHaveAttribute('aria-required', 'true')
    })
  })
})
