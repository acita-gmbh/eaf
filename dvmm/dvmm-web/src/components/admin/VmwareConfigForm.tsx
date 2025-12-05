import { useEffect, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'sonner'
import { Loader2, CheckCircle2, XCircle, Server, AlertCircle } from 'lucide-react'
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  vmwareConfigFormSchema,
  type VmwareConfigFormData,
} from '@/lib/validations/vmware-config'
import {
  useVmwareConfig,
  useSaveVmwareConfig,
  useTestVmwareConnection,
} from '@/hooks/useVmwareConfig'
import { isConnectionTestError, type ConnectionTestResult } from '@/api/vmware-config'
import { ApiError } from '@/api/vm-requests'
import { cn } from '@/lib/utils'

/**
 * VMware vCenter configuration form.
 *
 * Story 3.1: VMware Connection Configuration
 *
 * Features:
 * - AC-3.1.1: Form fields for vCenter URL, username, password, datacenter, etc.
 * - AC-3.1.2: "Test Connection" button validates connectivity
 * - AC-3.1.3: Real-time feedback on validation errors
 * - AC-3.1.4: Password encrypted before storage (handled by backend)
 */
export function VmwareConfigForm() {
  const { data: existingConfig, isLoading: isLoadingConfig } = useVmwareConfig()
  const saveMutation = useSaveVmwareConfig()
  const testMutation = useTestVmwareConnection()

  const [testResult, setTestResult] = useState<ConnectionTestResult | null>(null)
  const [testError, setTestError] = useState<string | null>(null)

  const form = useForm<VmwareConfigFormData>({
    resolver: zodResolver(vmwareConfigFormSchema),
    defaultValues: {
      vcenterUrl: '',
      username: '',
      password: '',
      datacenterName: '',
      clusterName: '',
      datastoreName: '',
      networkName: '',
      templateName: '',
      folderPath: '',
    },
    mode: 'onChange',
  })

  const { control, reset, setError } = form

  // Populate form with existing config when loaded
  useEffect(() => {
    if (existingConfig) {
      reset({
        vcenterUrl: existingConfig.vcenterUrl,
        username: existingConfig.username,
        password: '', // Never populate password for security
        datacenterName: existingConfig.datacenterName,
        clusterName: existingConfig.clusterName,
        datastoreName: existingConfig.datastoreName,
        networkName: existingConfig.networkName,
        templateName: existingConfig.templateName || '',
        folderPath: existingConfig.folderPath || '',
      })
    }
  }, [existingConfig, reset])

  // Watch password to determine if it's been entered
  const passwordValue = useWatch({ control, name: 'password' })
  const hasPassword = Boolean(passwordValue)

  const isUpdate = Boolean(existingConfig)

  const handleSave = (data: VmwareConfigFormData) => {
    // Clear previous test results
    setTestResult(null)
    setTestError(null)

    // Detect if folderPath was cleared (had value before, now empty)
    const hadFolderPath = Boolean(existingConfig?.folderPath)
    const folderPathCleared = hadFolderPath && !data.folderPath

    const payload = {
      vcenterUrl: data.vcenterUrl,
      username: data.username,
      password: data.password || null, // null = keep existing for updates
      datacenterName: data.datacenterName,
      clusterName: data.clusterName,
      datastoreName: data.datastoreName,
      networkName: data.networkName,
      templateName: data.templateName || undefined,
      folderPath: data.folderPath || undefined,
      clearFolderPath: folderPathCleared, // Explicitly clear folderPath to null
      version: existingConfig?.version ?? null, // null = create, number = update
    }

    saveMutation.mutate(payload, {
      onSuccess: () => {
        toast.success(
          isUpdate ? 'Configuration updated!' : 'Configuration saved!',
          {
            description: 'VMware vCenter settings have been saved successfully.',
          }
        )
        // Clear password field after successful save
        form.setValue('password', '')
      },
      onError: (error) => {
        if (error instanceof ApiError) {
          if (error.status === 400) {
            // Validation error from backend
            toast.error('Validation error', {
              description: 'Please correct the highlighted fields.',
            })
          } else if (error.status === 409) {
            // Optimistic locking conflict or already exists
            setError('root', {
              message:
                'Configuration was modified by another admin. Please refresh and retry.',
            })
            toast.error('Conflict', {
              description: 'Configuration was modified. Please refresh.',
            })
          } else {
            toast.error('Save failed', {
              description: `Error: ${error.statusText}`,
            })
          }
        } else {
          toast.error('Connection error', {
            description: 'Please try again.',
          })
        }
      },
    })
  }

  const handleTestConnection = () => {
    const values = form.getValues()

    // Validate required fields for test
    if (!values.vcenterUrl || !values.username || !values.datacenterName ||
        !values.clusterName || !values.datastoreName || !values.networkName) {
      toast.error('Missing fields', {
        description: 'Please fill in all required fields before testing.',
      })
      return
    }

    // For testing, we need either a new password or an existing config
    if (!values.password && !existingConfig) {
      toast.error('Password required', {
        description: 'Please enter a password to test the connection.',
      })
      return
    }

    // Clear previous results
    setTestResult(null)
    setTestError(null)

    // Send null if no password - backend will fetch and decrypt stored password
    const testPassword = values.password || null

    testMutation.mutate(
      {
        vcenterUrl: values.vcenterUrl,
        username: values.username,
        password: testPassword,
        datacenterName: values.datacenterName,
        clusterName: values.clusterName,
        datastoreName: values.datastoreName,
        networkName: values.networkName,
        templateName: values.templateName || undefined,
        // Update verifiedAt when using stored password
        updateVerifiedAt: Boolean(existingConfig) && !values.password,
      },
      {
        onSuccess: (result) => {
          if (isConnectionTestError(result)) {
            setTestError(result.message)
            setTestResult(null)
            toast.error('Connection failed', {
              description: result.message,
            })
          } else {
            setTestResult(result)
            setTestError(null)
            toast.success('Connection successful!', {
              description: `Connected to vCenter ${result.vcenterVersion}`,
            })
          }
        },
        onError: (error) => {
          setTestError(error.statusText)
          setTestResult(null)
          toast.error('Test failed', {
            description: error.statusText,
          })
        },
      }
    )
  }

  if (isLoadingConfig) {
    return (
      <Card>
        <CardContent className="flex items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Server className="h-5 w-5" />
          VMware vCenter Configuration
        </CardTitle>
        <CardDescription>
          Configure the VMware vCenter connection for VM provisioning.
          {existingConfig && (
            <span className="block mt-1 text-xs">
              Last verified:{' '}
              {existingConfig.verifiedAt
                ? new Date(existingConfig.verifiedAt).toLocaleString()
                : 'Never'}
            </span>
          )}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit(handleSave)}
            className="space-y-6"
            data-testid="vmware-config-form"
          >
            {/* Connection Settings Section */}
            <div className="space-y-4">
              <h3 className="text-sm font-medium text-muted-foreground">
                Connection Settings
              </h3>

              {/* vCenter URL */}
              <FormField
                control={control}
                name="vcenterUrl"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      vCenter URL <span className="text-destructive">*</span>
                    </FormLabel>
                    <FormControl>
                      <Input
                        placeholder="https://vcenter.example.com/sdk"
                        aria-required="true"
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>
                      vCenter SDK URL (must use HTTPS)
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {/* Username */}
              <FormField
                control={control}
                name="username"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      Username <span className="text-destructive">*</span>
                    </FormLabel>
                    <FormControl>
                      <Input
                        placeholder="administrator@vsphere.local"
                        aria-required="true"
                        autoComplete="username"
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>
                      Service account username
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {/* Password */}
              <FormField
                control={control}
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      Password{' '}
                      {!isUpdate && <span className="text-destructive">*</span>}
                    </FormLabel>
                    <FormControl>
                      <Input
                        type="password"
                        placeholder={
                          isUpdate ? '••••••••' : 'Enter password'
                        }
                        aria-required={!isUpdate}
                        autoComplete="current-password"
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>
                      {isUpdate
                        ? 'Leave blank to keep existing password'
                        : 'Password will be encrypted before storage'}
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            {/* vSphere Resources Section */}
            <div className="space-y-4 border-t pt-6">
              <h3 className="text-sm font-medium text-muted-foreground">
                vSphere Resources
              </h3>

              {/* Datacenter Name */}
              <FormField
                control={control}
                name="datacenterName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      Datacenter <span className="text-destructive">*</span>
                    </FormLabel>
                    <FormControl>
                      <Input
                        placeholder="Datacenter1"
                        aria-required="true"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {/* Cluster Name */}
              <FormField
                control={control}
                name="clusterName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      Cluster <span className="text-destructive">*</span>
                    </FormLabel>
                    <FormControl>
                      <Input
                        placeholder="Cluster1"
                        aria-required="true"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {/* Datastore Name */}
              <FormField
                control={control}
                name="datastoreName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      Datastore <span className="text-destructive">*</span>
                    </FormLabel>
                    <FormControl>
                      <Input
                        placeholder="datastore1"
                        aria-required="true"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {/* Network Name */}
              <FormField
                control={control}
                name="networkName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      Network <span className="text-destructive">*</span>
                    </FormLabel>
                    <FormControl>
                      <Input
                        placeholder="VM Network"
                        aria-required="true"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            {/* Optional Settings Section */}
            <div className="space-y-4 border-t pt-6">
              <h3 className="text-sm font-medium text-muted-foreground">
                Optional Settings
              </h3>

              {/* Template Name */}
              <FormField
                control={control}
                name="templateName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>VM Template</FormLabel>
                    <FormControl>
                      <Input
                        placeholder="ubuntu-22.04-template"
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>
                      Default: ubuntu-22.04-template
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {/* Folder Path */}
              <FormField
                control={control}
                name="folderPath"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>VM Folder Path</FormLabel>
                    <FormControl>
                      <Input
                        placeholder="/DVMM/Production"
                        {...field}
                      />
                    </FormControl>
                    <FormDescription>
                      Optional folder for organizing provisioned VMs
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            {/* Connection Test Result */}
            {testResult && (
              <div
                className={cn(
                  'rounded-lg border p-4 text-sm',
                  'border-green-500 bg-green-50 dark:bg-green-950/20'
                )}
                role="status"
                aria-live="polite"
              >
                <div className="flex items-center gap-2 font-medium text-green-700 dark:text-green-400">
                  <CheckCircle2 className="h-4 w-4" />
                  Connection Successful
                </div>
                <ul className="mt-2 space-y-1 text-green-600 dark:text-green-300">
                  <li>vCenter Version: {testResult.vcenterVersion}</li>
                  <li>Cluster: {testResult.clusterName} ({testResult.clusterHosts} hosts)</li>
                  <li>Datastore Free: {testResult.datastoreFreeGb} GB</li>
                </ul>
              </div>
            )}

            {testError && (
              <div
                className={cn(
                  'rounded-lg border p-4 text-sm',
                  'border-destructive bg-destructive/10'
                )}
                role="alert"
                aria-live="polite"
              >
                <div className="flex items-center gap-2 font-medium text-destructive">
                  <XCircle className="h-4 w-4" />
                  Connection Failed
                </div>
                <p className="mt-2 text-destructive/80">{testError}</p>
              </div>
            )}

            {/* Root-level errors */}
            {form.formState.errors.root && (
              <div
                className="rounded-lg border border-destructive bg-destructive/10 p-4 text-sm text-destructive"
                role="alert"
                aria-live="polite"
              >
                <div className="flex items-center gap-2">
                  <AlertCircle className="h-4 w-4" />
                  {form.formState.errors.root.message}
                </div>
              </div>
            )}

            {/* Actions */}
            <div className="flex flex-col gap-3 border-t pt-6 sm:flex-row sm:justify-between">
              <Button
                type="button"
                variant="outline"
                onClick={handleTestConnection}
                disabled={testMutation.isPending || saveMutation.isPending}
                data-testid="test-connection-button"
              >
                {testMutation.isPending ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Testing...
                  </>
                ) : (
                  'Test Connection'
                )}
              </Button>

              <Button
                type="submit"
                disabled={
                  saveMutation.isPending ||
                  testMutation.isPending ||
                  (!isUpdate && !hasPassword)
                }
                data-testid="save-button"
              >
                {saveMutation.isPending ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Saving...
                  </>
                ) : isUpdate ? (
                  'Update Configuration'
                ) : (
                  'Save Configuration'
                )}
              </Button>
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}
